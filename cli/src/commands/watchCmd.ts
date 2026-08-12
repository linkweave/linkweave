import type { Command } from 'commander'

import type { ChangeKind, CollectionEventJson } from '../api'
import { createAuthenticatedClients } from '../client'
import { AUTH_FAILED_MESSAGE, CliError } from '../errors'
import {
  COLLECTION_FORBIDDEN_MESSAGE,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} from './commandHelpers'

export interface WatchOptions {
  collection?: string
  format?: string
  retries?: string
}

/**
 * Reconnect budget, mirroring the web client (UC-104 BR-206/A4): a bounded,
 * jittered backoff and then a clean exit rather than a terminal that retries
 * forever against a server that is never coming back.
 */
const DEFAULT_RETRIES = 6
const MAX_RECONNECT_DELAY_MS = 30_000

/**
 * Base backoff, in a mutable holder purely so tests can shrink it. A reconnect
 * test that waits out the real delays takes seconds per case; the alternative —
 * a hidden `--retry-delay` flag — would be user-facing surface that exists only
 * for the test suite.
 */
export const reconnectTuning = { baseDelayMs: 1_000 }

/** Human wording per kind. Unknown kinds print their raw name — a newer server may know more. */
const DESCRIPTIONS: Partial<Record<ChangeKind, string>> = {
  BOOKMARK_ADDED: 'bookmark added',
  BOOKMARK_CHANGED: 'bookmark changed',
  BOOKMARK_REMOVED: 'bookmark removed',
  FOLDER_ADDED: 'folder added',
  FOLDER_CHANGED: 'folder changed',
  FOLDER_REMOVED: 'folder removed',
  COLLECTION_CHANGED: 'collection changed',
  SCREENSHOT_READY: 'screenshot ready',
}

function reconnectDelay(attempt: number): number {
  const backoff = Math.min(reconnectTuning.baseDelayMs * 2 ** attempt, MAX_RECONNECT_DELAY_MS)
  return backoff / 2 + Math.random() * (backoff / 2)
}

/**
 * Splits an SSE byte stream into events.
 *
 * Hand-rolled rather than using `EventSource`, for two reasons that both make it
 * unusable here: it cannot send the `X-API-Key` header this client authenticates
 * with, and it is not available on every Node version the CLI supports. Only the
 * `data:` field is needed — the server sends no event names or ids.
 */
export async function* parseSseFrames(body: ReadableStream<Uint8Array>): AsyncGenerator<string> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) return
    buffer += decoder.decode(value, { stream: true })
    let separator = buffer.indexOf('\n\n')
    while (separator !== -1) {
      const frame = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      const data = frame
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice('data:'.length).trim())
        .join('')
      if (data) yield data
      separator = buffer.indexOf('\n\n')
    }
  }
}

function describe(event: CollectionEventJson): string {
  const what = DESCRIPTIONS[event.kind] ?? event.kind
  const who = event.actorName ? ` by ${event.actorName}` : ''
  const which = event.bookmarkId ? ` (${event.bookmarkId})` : ''
  return `${what}${which}${who}`
}

/**
 * Consumes one connection until it ends, returning true if anything was
 * received — which is what tells a caller the stream worked at all, so a
 * connection that opened and then dropped resets the retry budget rather than
 * counting toward it.
 */
async function consume(
  response: Response,
  json: boolean,
  onOpen: () => void,
): Promise<boolean> {
  if (!response.body) return false
  let received = false
  for await (const data of parseSseFrames(response.body)) {
    let event: CollectionEventJson
    try {
      event = JSON.parse(data) as CollectionEventJson
    } catch {
      continue // a frame we cannot read is not worth ending the stream over
    }
    if (!received) onOpen()
    received = true
    // Heartbeats are keep-alive traffic, not news (BR-208). The first one is
    // what confirms the connection, which `onOpen` reports on stderr so piped
    // stdout stays pure.
    if (event.kind === 'HEARTBEAT') continue
    console.log(json ? JSON.stringify(event) : describe(event))
  }
  return received
}

/** `linkweave watch` — follow a collection's live changes (UC-104). */
export async function runWatch(options: WatchOptions, cmd: Command): Promise<void> {
  const config = effectiveConfig(cmd)
  const json = (options.format ?? 'table') === 'json'
  const maxRetries = Number.parseInt(options.retries ?? String(DEFAULT_RETRIES), 10)
  if (!Number.isInteger(maxRetries) || maxRetries < 0) {
    throw new CliError(`--retries must be a non-negative whole number, got '${options.retries}'`)
  }
  const clients = createAuthenticatedClients(config)

  const collectionId = await withHttpErrors(config, { forbidden: COLLECTION_FORBIDDEN_MESSAGE }, () =>
    resolveTargetCollectionId(clients, config, options.collection),
  )
  const url = `${config.server}/api/collections/${encodeURIComponent(collectionId)}/events`

  // `--retries` counts *consecutive* reconnects: a connection that delivered
  // something resets the budget, so a watch left running for days is not killed
  // by six blips spread across a week. The check comes before the reset is used,
  // or `--retries 0` — "do not reconnect" — would loop forever against a server
  // that keeps accepting, delivering and dropping.
  let attempt = 0
  for (;;) {
    const response = await fetch(url, {
      headers: { 'X-API-Key': config.apiKey ?? '', Accept: 'text/event-stream' },
    })
    // Authorization is re-checked on every reconnect, so a key revoked or an
    // access removed mid-watch stops the loop instead of retrying into a wall.
    if (response.status === 401) throw new CliError(AUTH_FAILED_MESSAGE)
    if (response.status === 403) throw new CliError(COLLECTION_FORBIDDEN_MESSAGE)
    if (!response.ok) throw new CliError(`Server responded with ${response.status}.`)

    const received = await consume(response, json, () =>
      process.stderr.write(`Watching collection ${collectionId}. Press Ctrl-C to stop.\n`),
    )
    if (received) attempt = 0
    if (attempt >= maxRetries) {
      throw new CliError(
        received
          ? 'The server closed the connection.'
          : 'Lost the connection to the server and gave up reconnecting.',
      )
    }
    attempt++
    await new Promise((resolve) => setTimeout(resolve, reconnectDelay(attempt)))
  }
}
