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

/**
 * The shape Node's `fetch` really rejects with — verified against a live
 * refused connection: a bare `TypeError: fetch failed` whose `cause` is an
 * `AggregateError` carrying the code. Building it by hand as `{ code }` would
 * test a shape that never occurs, and pass while the real message stayed
 * useless.
 */
function networkFailure(code: string): TypeError {
  const failure = new TypeError('fetch failed')
  const cause = new AggregateError([], '')
  ;(cause as { code?: string }).code = code
  ;(failure as { cause?: unknown }).cause = cause
  return failure
}

const refusal = () => networkFailure('ECONNREFUSED')

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

  it('reads events from a stream with CRLF line endings', async () => {
    // ARRANGE — an intermediary that rewrites line endings would otherwise
    // stall the parser forever: '\r\n\r\n' contains no '\n\n'
    const body = sseBody('data:{"a":1}\r\n\r\ndata:{"b":2}\r\n\r\n')

    // ACT
    const frames: string[] = []
    for await (const data of parseSseFrames(body)) frames.push(data)

    // ASSERT
    expect(frames).toEqual(['{"a":1}', '{"b":2}'])
  })

  it('joins a payload split across several data lines with newlines', async () => {
    // ARRANGE — the SSE spec says consecutive data: lines are one payload
    // separated by newlines, not concatenated
    const body = sseBody('data:line one\ndata:line two\n\n')

    // ACT
    const frames: string[] = []
    for await (const data of parseSseFrames(body)) frames.push(data)

    // ASSERT
    expect(frames).toEqual(['line one\nline two'])
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

  it('retries a refused connection instead of dying on it', async () => {
    // ARRANGE — a transport-level rejection, which is what an unreachable
    // server, a DNS hiccup or Wi-Fi coming back actually looks like: `fetch`
    // rejects rather than resolving with a status
    let call = 0
    const connect = vi.fn(async () => {
      call++
      if (call === 1) throw refusal()
      if (call >= 3) return new Response(null, { status: 403 }) // ends the watch
      return new Response(
        sseBody(frame(HEARTBEAT), frame({
          collectionId: 'col-1',
          bookmarkId: 'bm-2',
          kind: 'BOOKMARK_ADDED',
          actorName: 'Ada',
        })),
        { status: 200, headers: { 'content-type': 'text/event-stream' } },
      )
    })
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '1', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — the blip was survived, which is the whole promise of --retries
    expect(connect.mock.calls.length).toBeGreaterThan(1)
    expect(out).toContain('bookmark added (bm-2) by Ada')
  })

  it('reports an unreachable server the way every other command does', async () => {
    // ARRANGE
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw refusal()
      }),
    )

    // ACT / ASSERT — not a raw 'TypeError: fetch failed', which is what a bare
    // fetch produces since only the generated client's rejections are translated
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /Cannot reach LinkWeave server.*ECONNREFUSED/s,
    )
  })

  it('retries a gateway status, since that is a server restarting', async () => {
    // ARRANGE
    let call = 0
    const connect = vi.fn(async () => {
      call++
      if (call === 1) return new Response(null, { status: 503 })
      if (call >= 3) return new Response(null, { status: 403 }) // ends the watch
      return new Response(sseBody(frame(HEARTBEAT)), {
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
      })
    })
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '1', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT
    expect(connect.mock.calls.length).toBeGreaterThan(1)
  })

  it('stops on a certificate it will never accept', async () => {
    // ARRANGE — waiting does not fix a bad cert; --insecure or a real one does
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw networkFailure('SELF_SIGNED_CERT_IN_CHAIN')
      }),
    )

    // ACT / ASSERT
    await expect(runWatch({ retries: '9', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /certificate/i,
    )
  })

  it('survives a read that fails mid-stream', async () => {
    // ARRANGE — the connection is accepted, then the body errors
    let call = 0
    const connect = vi.fn(async () => {
      call++
      if (call === 1) {
        return new Response(
          new ReadableStream({
            start(controller) {
              controller.enqueue(new TextEncoder().encode(frame(HEARTBEAT)))
              controller.error(new Error('connection reset'))
            },
          }),
          { status: 200, headers: { 'content-type': 'text/event-stream' } },
        )
      }
      if (call >= 3) return new Response(null, { status: 403 }) // ends the watch
      return new Response(sseBody(frame(HEARTBEAT)), {
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
      })
    })
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '1', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — a mid-stream failure is a reconnect, not a crash
    expect(connect.mock.calls.length).toBeGreaterThan(1)
  })

  it('counts a stream that failed mid-flight as an attempt, not a fresh start', async () => {
    // ARRANGE — a server that accepts, sends, and drops in a loop. Treating the
    // frames it delivered as "the connection worked" would reset the budget
    // every time and retry forever.
    const connect = vi.fn(async () => new Response(
      new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(frame(HEARTBEAT)))
          controller.error(new Error('socket destroyed'))
        },
      }),
      { status: 200, headers: { 'content-type': 'text/event-stream' } },
    ))
    vi.stubGlobal('fetch', connect)

    // ACT
    await expect(runWatch({ retries: '2', collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )

    // ASSERT — the budget is spent and the watch stops: one attempt plus two
    // retries, exactly as a live run against such a server behaves
    expect(connect).toHaveBeenCalledTimes(3)
  })

  it('names the network failure rather than repeating "fetch failed"', async () => {
    // ARRANGE — a cause with no code, as a blocked port produces
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        const failure = new TypeError('fetch failed')
        ;(failure as { cause?: unknown }).cause = new Error('bad port')
        throw failure
      }),
    )

    // ACT / ASSERT — the message is the only diagnosis the user gets
    await expect(runWatch({ retries: '0', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /bad port/,
    )
  })

  it('rejects a fractional retry budget rather than truncating it', async () => {
    // parseInt('6.5') would silently mean 6
    await expect(runWatch({ retries: '6.5', collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /--retries/,
    )
  })

  it('rejects a nonsense retry budget instead of silently defaulting', async () => {
    await expect(runWatch({ retries: 'lots', collection: COLLECTION }, cmdWith())).rejects.toThrow(/--retries/)
  })
})
