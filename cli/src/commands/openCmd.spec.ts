import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CliError } from '../errors'
import { browserCommand, runOpen } from './openCmd'

vi.mock('node:child_process', () => ({ spawn: vi.fn() }))

const { spawn } = await import('node:child_process')

const COLLECTION = '11111111-2222-3333-4444-555555555555'
const BOOKMARK_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

function cmdWith() {
  return { optsWithGlobals: () => ({ server: 'https://example.test', apiKey: 'lw_key' }) } as never
}

/** Shaped as the wire format: the generated model maps `propertyValues`. */
function bookmarkPayload(id: string, title: string, url: string) {
  return {
    id,
    data: { title, url, collectionId: COLLECTION, tagIds: [] },
    propertyValues: [],
  }
}

/** Routes the generated client's calls by URL, so one stub serves every step. */
function stubApi(bookmarks: ReturnType<typeof bookmarkPayload>[]) {
  const clicks: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const json = (body: unknown) =>
        new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        })
      if (url.includes('track-click')) {
        clicks.push(url)
        return new Response(null, { status: 204 })
      }
      if (url.includes('/api/tags')) return json({ tagList: [] })
      if (url.includes('/api/bookmarks?')) return json({ bookmarkList: bookmarks })
      if (url.includes(`/api/bookmarks/${BOOKMARK_ID}`)) return json(bookmarks[0])
      return new Response('unexpected ' + url + (init?.method ?? ''), { status: 404 })
    }),
  )
  return clicks
}

describe('runOpen', () => {
  let out: string[]

  beforeEach(() => {
    out = []
    vi.spyOn(console, 'log').mockImplementation((line: string) => void out.push(line))
    vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
    // restoreAllMocks() does not reset a vi.mock() factory's fn, so its call
    // history would otherwise leak into the next test's "was never called".
    vi.mocked(spawn).mockReset()
    vi.mocked(spawn).mockReturnValue({ on: vi.fn(), unref: vi.fn() } as never)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('opens the single bookmark matching the words given', async () => {
    // ARRANGE
    stubApi([
      bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org'),
      bookmarkPayload('other', 'Rust book', 'https://doc.rust-lang.org'),
    ])

    // ACT
    await runOpen(['vue'], { collection: COLLECTION }, cmdWith())

    // ASSERT — handed to the desktop, not printed
    expect(vi.mocked(spawn).mock.calls[0]?.[1]).toContain('https://vuejs.org')
    expect(out).toEqual([])
  })

  it('records the click, like clicking it in the web UI does', async () => {
    // ARRANGE
    const clicks = stubApi([bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org')])

    // ACT
    await runOpen(['vue'], { collection: COLLECTION }, cmdWith())

    // ASSERT — otherwise opening from a terminal quietly makes "never opened"
    // and click counts wrong
    expect(clicks).toHaveLength(1)
    expect(clicks[0]).toContain(BOOKMARK_ID)
  })

  it('refuses to guess when the words match more than one bookmark', async () => {
    // ARRANGE
    stubApi([
      bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org'),
      bookmarkPayload('second', 'Vue router', 'https://router.vuejs.org'),
    ])

    // ACT / ASSERT — opening the wrong page is a failure the user notices late
    await expect(runOpen(['vue'], { collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /matches 2 bookmarks/,
    )
    expect(spawn).not.toHaveBeenCalled()
  })

  it('reports a query that matches nothing', async () => {
    stubApi([bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org')])
    await expect(runOpen(['nothing'], { collection: COLLECTION }, cmdWith())).rejects.toBeInstanceOf(
      CliError,
    )
  })

  it('opens by ID without searching', async () => {
    // ARRANGE — the documented primary form, which takes a different path
    // through the client (fetch one bookmark, no list, no tag lookup)
    stubApi([bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org')])

    // ACT
    await runOpen([BOOKMARK_ID], { collection: COLLECTION }, cmdWith())

    // ASSERT
    expect(vi.mocked(spawn).mock.calls[0]?.[1]).toContain('https://vuejs.org')
  })

  it('still reports success when recording the click fails', async () => {
    // ARRANGE — the page is already open by then
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('track-click')) return new Response(null, { status: 500 })
        if (url.includes('/api/tags')) {
          return new Response(JSON.stringify({ tagList: [] }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          })
        }
        return new Response(
          JSON.stringify({ bookmarkList: [bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org')] }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        )
      }),
    )

    // ACT / ASSERT — erroring here would report a failure for something that worked
    await expect(runOpen(['vue'], { collection: COLLECTION }, cmdWith())).resolves.toBeUndefined()
    expect(spawn).toHaveBeenCalled()
  })

  it('refuses to hand a non-web URL to the operating system', async () => {
    // ARRANGE — in a shared collection the URL is somebody else's input, and
    // the platform handler will open far more than web pages
    stubApi([bookmarkPayload(BOOKMARK_ID, 'Local file', 'file:///etc/passwd')])

    // ACT / ASSERT
    await expect(runOpen(['local'], { collection: COLLECTION }, cmdWith())).rejects.toThrow(
      /only http and https/i,
    )
    expect(spawn).not.toHaveBeenCalled()
  })

  it('still prints a non-web URL, which is only text', async () => {
    // ARRANGE
    stubApi([bookmarkPayload(BOOKMARK_ID, 'Local file', 'file:///etc/passwd')])

    // ACT
    await runOpen(['local'], { collection: COLLECTION, print: true }, cmdWith())

    // ASSERT — printing hands nothing to the OS; the user decides what to do
    expect(out).toEqual(['file:///etc/passwd'])
  })

  it('opens an ordinary URL with a query string', async () => {
    // ARRANGE — the common case the Windows path used to truncate
    stubApi([bookmarkPayload(BOOKMARK_ID, 'Search', 'https://x.test/p?a=1&b=2')])

    // ACT
    await runOpen(['search'], { collection: COLLECTION }, cmdWith())

    // ASSERT
    expect(vi.mocked(spawn).mock.calls[0]?.[1]).toContain('https://x.test/p?a=1&b=2')
  })

  it('prints the URL without opening or recording a click', async () => {
    // ARRANGE
    const clicks = stubApi([bookmarkPayload(BOOKMARK_ID, 'Vue docs', 'https://vuejs.org')])

    // ACT
    await runOpen(['vue'], { collection: COLLECTION, print: true }, cmdWith())

    // ASSERT — the URL may be piped somewhere that never visits it
    expect(out).toEqual(['https://vuejs.org'])
    expect(spawn).not.toHaveBeenCalled()
    expect(clicks).toHaveLength(0)
  })
})

describe('browserCommand', () => {
  const realPlatform = process.platform

  function onPlatform(platform: NodeJS.Platform) {
    Object.defineProperty(process, 'platform', { value: platform, configurable: true })
  }

  afterEach(() => onPlatform(realPlatform))

  it('uses the platform opener', () => {
    onPlatform('darwin')
    expect(browserCommand('https://x.test')).toEqual({ command: 'open', args: ['https://x.test'] })

    onPlatform('linux')
    expect(browserCommand('https://x.test')).toEqual({
      command: 'xdg-open',
      args: ['https://x.test'],
    })
  })

  it('keeps a query string intact on Windows instead of handing it to cmd', () => {
    // ARRANGE — `cmd /c start` re-parses its command line, where & separates
    // commands: the URL would be truncated at best, and a bookmark saved by a
    // collaborator could run commands on the machine that opens it. Node's argv
    // quoting does not escape &, only whitespace and quotes.
    onPlatform('win32')

    // ACT
    const { command, args } = browserCommand('https://x.test/p?a=1&b=2')

    // ASSERT — no shell in between, and the whole URL is a single argument
    expect(command).toBe('rundll32.exe')
    expect(command).not.toContain('cmd')
    expect(args).toEqual(['url.dll,FileProtocolHandler', 'https://x.test/p?a=1&b=2'])
  })
})
