import type { Command } from 'commander'

import type { ChangeKind, CollectionEventJson } from '../api'
import { createAuthenticatedClients } from '../client'
import { AUTH_FAILED_MESSAGE, CliError, isTlsError, TLS_FAILED_MESSAGE } from '../errors'
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

/** Statuses that mean "try again later" rather than "this will never work". */
const GATEWAY_STATUSES = new Set([502, 503, 504])

/** A failure that should be retried, carrying what to say if the budget runs out. */
class RetryableFailure extends Error {}

/**
 * The most specific thing we can say about why the connection failed.
 *
 * Node's `fetch` rejects with a bare `TypeError: fetch failed` and puts the
 * detail on `cause` — an `AggregateError` carrying `code: 'ECONNREFUSED'` for a
 * refusal, `UND_ERR_SOCKET` for a reset, or only a message ('bad port') when
 * there is no code. Without unwrapping it, every network failure reads the same.
 */
function describeNetworkError(error: unknown): string {
  const cause = (error as { cause?: { code?: string; message?: string } } | undefined)?.cause
  if (cause?.code) return cause.code
  if (cause?.message) return cause.message
  return error instanceof Error ? error.message : String(error)
}

/**
 * The message for a watch that has stopped. A network failure gets the same
 * wording every other command gives for an unreachable server — a bare `fetch`
 * rejection would otherwise surface as `TypeError: fetch failed`, since only the
 * generated client's rejections pass through the shared translation.
 */
function giveUp(server: string, received: boolean, lastFailure: string | undefined): CliError {
  if (lastFailure) {
    return new CliError(
      `Cannot reach LinkWeave server at ${server} (${lastFailure}). ` +
        'Check your network connection and server URL.',
    )
  }
  return received
    ? new CliError('The server closed the connection.')
    : new CliError('Lost the connection to the server and gave up reconnecting.')
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
    // Normalised first: an intermediary that rewrites line endings would
    // otherwise stall the parser forever, since '\r\n\r\n' contains no '\n\n'.
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, '\n')
    let separator = buffer.indexOf('\n\n')
    while (separator !== -1) {
      const frame = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      const data = frame
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice('data:'.length).trim())
        // Newline, per the SSE spec: consecutive data: lines are one payload
        // split across lines, not one line to concatenate.
        .join('\n')
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
  // Deliberately not parseInt, which reads '6.5' as 6 and '0x6' as 0 — a budget
  // silently different from what was typed is worse than a rejected one.
  const retriesText = options.retries ?? String(DEFAULT_RETRIES)
  if (!/^\d+$/.test(retriesText)) {
    throw new CliError(`--retries must be a non-negative whole number, got '${retriesText}'`)
  }
  const maxRetries = Number(retriesText)
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
  let lastFailure: string | undefined
  for (;;) {
    let received = false
    try {
      const response = await fetch(url, {
        headers: { 'X-API-Key': config.apiKey ?? '', Accept: 'text/event-stream' },
      })
      // Authorization is re-checked on every reconnect, so a key revoked or an
      // access removed mid-watch stops the loop instead of retrying into a wall.
      if (response.status === 401) throw new CliError(AUTH_FAILED_MESSAGE)
      if (response.status === 403) throw new CliError(COLLECTION_FORBIDDEN_MESSAGE)
      // A gateway status is the shape of a server restarting behind a proxy —
      // the blip this command exists to survive. Anything else is a bug or a
      // wrong URL, which waiting will not fix.
      if (GATEWAY_STATUSES.has(response.status)) {
        throw new RetryableFailure(`server responded with ${response.status}`)
      }
      if (!response.ok) throw new CliError(`Server responded with ${response.status}.`)

      received = await consume(response, json, () =>
        process.stderr.write(`Watching collection ${collectionId}. Press Ctrl-C to stop.\n`),
      )
      lastFailure = undefined
    } catch (error) {
      // Deliberate stops (auth, a URL that will never work) must not be retried.
      if (error instanceof CliError) throw error
      // Nor a certificate the client will keep rejecting; --insecure or a fixed
      // cert is the fix, not patience.
      if (isTlsError(error)) throw new CliError(TLS_FAILED_MESSAGE)
      // Everything else is the network: a refused connection, DNS, a TLS reset,
      // Wi-Fi coming back, or a read that failed mid-stream. Those are exactly
      // what the budget is for, and they arrive as a raw rejection from `fetch`
      // rather than through the generated client's error handling.
      lastFailure = error instanceof RetryableFailure ? error.message : describeNetworkError(error)
    }

    // Only a clean end resets the budget. A stream that delivered frames and
    // then errored does not: a server that accepts, sends, and drops in a loop
    // would otherwise be retried forever, which is the same trap `--retries 0`
    // fell into before.
    if (received) attempt = 0
    if (attempt >= maxRetries) throw giveUp(config.server, received, lastFailure)
    attempt++
    await new Promise((resolve) => setTimeout(resolve, reconnectDelay(attempt)))
  }
}
