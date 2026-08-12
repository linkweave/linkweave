import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CliError } from '../errors'
import { parseSseFrames, reconnectTuning, runWatch } from './watchCmd'

/** A body stream that yields the given chunks, then ends — as a dropped connection does. */
function sseBody(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
}

function frame(event: Record<string, unknown>): string {
  return `data:${JSON.stringify(event)}\n\n`
}

const HEARTBEAT = { collectionId: 'col-1', bookmarkId: null, kind: 'HEARTBEAT' }

/** Minimal command stub: the CLI reads config through `optsWithGlobals`. */
function cmdWith(server = 'https://example.test', apiKey = 'lw_key') {
  return { optsWithGlobals: () => ({ server, apiKey }) } as never
}

/**
 * Always names the collection explicitly. `resolveCollectionId` short-circuits
 * on something that looks like an id, so no test here depends on the machine's
 * stored config or on a stubbed `/auth/me`.
 */
const COLLECTION = '11111111-2222-3333-4444-555555555555'

describe('parseSseFrames', () => {
  it('reads events that arrive split across chunk boundaries', async () => {
    // ARRANGE — the network decides where the packets break, not the server
    const body = sseBody('data:{"a":', '1}\n\ndata:{"b":2}\n\n')

    // ACT
    const frames: string[] = []
    for await (const data of parseSseFrames(body)) frames.push(data)

    // ASSERT
    expect(frames).toEqual(['{"a":1}', '{"b":2}'])
  })

  it('ignores fields other than data', async () => {
    // ARRANGE
    const body = sseBody(':keep-alive comment\n\nid:7\ndata:{"a":1}\n\n')

    // ACT
    const frames: string[] = []
    for await (const data of parseSseFrames(body)) frames.push(data)

    // ASSERT
    expect(frames).toEqual(['{"a":1}'])
  })
})

describe('runWatch', () => {
  let out: string[]
  let err: string[]

  const realBaseDelay = reconnectTuning.baseDelayMs

  beforeEach(() => {
    // Real backoff would make every reconnect case take seconds.
    reconnectTuning.baseDelayMs = 1
    out = []
    err = []
    vi.spyOn(console, 'log').mockImplementation((line: string) => void out.push(line))
    vi.spyOn(process.stderr, 'write').mockImplementation((line) => {
      err.push(String(line))
      return true
    })
    // Resolving the target collection goes through the generated client; the
    // stream itself is raw fetch, so both are stubbed on the same global.
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        return new Response(
          sseBody(frame(HEARTBEAT), frame({
            collectionId: 'col-1',
            bookmarkId: 'bm-9',
            kind: 'BOOKMARK_ADDED',
            actorName: 'Ada Lovelace',
          })),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }),
    )
  })

  afterEach(() => {
    reconnectTuning.baseDelayMs = realBaseDelay
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('prints changes and stays quiet about heartbeats', async () => {
    // ACT — one connection, which then ends; no retries so it returns
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(CliError)

    // ASSERT — the keep-alive frame is not news, but it is what confirms the
    // connection, reported on stderr so piped stdout stays clean
    expect(out).toEqual(['bookmark added (bm-9) by Ada Lovelace'])
    expect(err.join('')).toContain(`Watching collection ${COLLECTION}`)
  })

  it('emits one JSON object per change when asked', async () => {
    // ACT
    await expect(runWatch({ retries: '0', format: 'json', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(CliError)

    // ASSERT — JSONL, so `linkweave watch --format json | jq` works line by line
    expect(out).toHaveLength(1)
    expect(JSON.parse(out[0]!)).toMatchObject({ kind: 'BOOKMARK_ADDED', bookmarkId: 'bm-9' })
  })

  it('gives up with a clear error once the retry budget is spent', async () => {
    // ARRANGE — a server that accepts the connection and immediately drops it
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(sseBody(), {
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
      })),
    )

    // ACT / ASSERT — bounded, like the web client (BR-206/A4)
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toThrow(/gave up reconnecting/)
  })

  it('stops rather than retrying when access was revoked mid-watch', async () => {
    // ARRANGE
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 403 })),
    )

    // ACT / ASSERT — retrying into a wall would spin until the budget ran out
    // and then report the wrong reason
    await expect(runWatch({ retries: '5', collection: COLLECTION }, cmdWith())).rejects.toThrow(/access/i)
  })

  it('does not reconnect at all when the budget is zero', async () => {
    // ARRANGE — a server that keeps accepting, delivering and dropping. With a
    // budget that resets on every successful connection, an off-by-one here
    // reconnects forever instead of honouring `--retries 0`.
    const connect = vi.fn(async () => new Response(
      sseBody(frame(HEARTBEAT)),
      { status: 200, headers: { 'content-type': 'text/event-stream' } },
    ))
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /closed the connection/,
    )

    // ASSERT
    expect(connect).toHaveBeenCalledTimes(1)
  })

  it('reconnects after a drop when the budget allows it', async () => {
    // ARRANGE — first connection drops empty, second delivers
    // A fresh Response per call: a ReadableStream can only be consumed once, so
    // handing back the same object would fail the retry for the wrong reason.
    let call = 0
    const connect = vi.fn(async () => {
      call++
      const headers = { 'content-type': 'text/event-stream' }
      if (call === 1) return new Response(sseBody(), { status: 200, headers })
      if (call >= 3) return new Response(null, { status: 403 }) // ends the watch
      return new Response(
        sseBody(frame(HEARTBEAT), frame({
          collectionId: 'col-1',
          bookmarkId: 'bm-1',
          kind: 'BOOKMARK_REMOVED',
          actorName: 'Ada Lovelace',
        })),
        { status: 200, headers },
      )
    })
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '1', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — the drop was survived and the events from the retry were printed
    expect(connect.mock.calls.length).toBeGreaterThan(1)
    expect(out).toContain('bookmark removed (bm-1) by Ada Lovelace')
  })

  it('stops rather than retrying when the key was revoked', async () => {
    // ARRANGE
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))

    // ACT / ASSERT — an unauthenticated client will not become authenticated by
    // waiting, so the retry budget must not be spent on it
    await expect(runWatch({ retries: '5', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /API key/i,
    )
  })

  it('reports an unexpected status instead of treating it as a dropped stream', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 503 })))
    await expect(runWatch({ retries: '5', collection: COLLECTION }, cmdWith())).rejects.toThrow(/503/)
  })

  it('prints a kind it does not know rather than dropping the event', async () => {
    // ARRANGE — a newer server with a kind this build predates
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(
        sseBody(frame({ collectionId: 'col-1', kind: 'SOMETHING_NEW', actorName: 'Ada' })),
        { status: 200, headers: { 'content-type': 'text/event-stream' } },
      )),
    )

    // ACT
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — silence would make an upgraded server look broken
    expect(out).toEqual(['SOMETHING_NEW by Ada'])
  })

  it('prints a change with no actor behind it', async () => {
    // ARRANGE — the capture job is nobody
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(
        sseBody(frame({ collectionId: 'col-1', bookmarkId: 'bm-3', kind: 'SCREENSHOT_READY' })),
        { status: 200, headers: { 'content-type': 'text/event-stream' } },
      )),
    )

    // ACT
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — no trailing "by", which a naive template would leave behind
    expect(out).toEqual(['screenshot ready (bm-3)'])
  })

  it('skips a frame it cannot parse without ending the stream', async () => {
    // ARRANGE
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(
        sseBody('data:{not json\n\n', frame({ collectionId: 'col-1', bookmarkId: 'b', kind: 'BOOKMARK_ADDED', actorName: 'Ada' })),
        { status: 200, headers: { 'content-type': 'text/event-stream' } },
      )),
    )

    // ACT
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — one bad frame must not cost the events after it
    expect(out).toEqual(['bookmark added (b) by Ada'])
  })

  it('rejects a nonsense retry budget instead of silently defaulting', async () => {
    await expect(runWatch({ retries: 'lots', collection: COLLECTION }, cmdWith())).rejects.toThrow(/--retries/)
  })
})
