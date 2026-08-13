import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { BookmarkJson } from '../api'
import { matchesQuery, runSearch } from './searchCmd'

function bookmark(title: string, url: string, tagIds: string[] = []): BookmarkJson {
  return { id: 'bm', data: { title, url, tagIds: new Set(tagIds) } } as unknown as BookmarkJson
}

const TAG_NAMES = new Map([
  ['t1', 'reading'],
  ['t2', 'rust'],
])

describe('matchesQuery', () => {
  it('finds a bookmark by part of its title, case-insensitively', () => {
    expect(matchesQuery(bookmark('Vue docs', 'https://vuejs.org'), ['vue'], TAG_NAMES)).toBe(true)
  })

  it('finds a bookmark by part of its URL', () => {
    // The half-remembered domain is often all the user has
    expect(matchesQuery(bookmark('Docs', 'https://tokio.rs/guide'), ['tokio'], TAG_NAMES)).toBe(true)
  })

  it('finds a bookmark by tag name', () => {
    expect(matchesQuery(bookmark('Docs', 'https://example.com', ['t2']), ['rust'], TAG_NAMES)).toBe(
      true,
    )
  })

  it('requires every term to match, not just one', () => {
    // ARRANGE — 'vue' hits the title, 'rust' hits nothing on this bookmark
    const target = bookmark('Vue docs', 'https://vuejs.org', ['t1'])

    // ACT / ASSERT — AND, so that adding a word narrows the result as expected
    expect(matchesQuery(target, ['vue'], TAG_NAMES)).toBe(true)
    expect(matchesQuery(target, ['vue', 'rust'], TAG_NAMES)).toBe(false)
  })

  it('matches terms that span different fields', () => {
    const target = bookmark('Async book', 'https://rust-lang.github.io', ['t1'])
    expect(matchesQuery(target, ['async', 'github', 'reading'], TAG_NAMES)).toBe(true)
  })

  it('matches everything when no terms were given', () => {
    // `search ""` should not be a way to get nothing back
    expect(matchesQuery(bookmark('Anything', 'https://example.com'), [], TAG_NAMES)).toBe(true)
  })

  it('does not let a term match across a field boundary', () => {
    // ARRANGE — fields are joined for the search; without a separator between
    // them, 'docshttps' would match the join of title and URL
    const target = bookmark('Docs', 'https://example.com')

    // ACT / ASSERT
    expect(matchesQuery(target, ['docshttps'], TAG_NAMES)).toBe(false)
  })

  it('ignores tag ids it cannot name', () => {
    // A tag id with no entry in the map must not be searchable as a raw UUID
    const target = bookmark('Docs', 'https://example.com', ['unknown-id'])
    expect(matchesQuery(target, ['unknown-id'], TAG_NAMES)).toBe(false)
  })
})

const COLLECTION = '11111111-2222-3333-4444-555555555555'

function cmdWith() {
  return { optsWithGlobals: () => ({ server: 'https://example.test', apiKey: 'lw_key' }) } as never
}

function payload(id: string, title: string, url: string) {
  return { id, data: { title, url, collectionId: COLLECTION, tagIds: [] }, propertyValues: [] }
}

describe('runSearch', () => {
  let out: string[]
  let err: string[]

  beforeEach(() => {
    out = []
    err = []
    vi.spyOn(console, 'log').mockImplementation((line: string) => void out.push(line))
    vi.spyOn(process.stderr, 'write').mockImplementation((line) => {
      err.push(String(line))
      return true
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const json = (body: unknown) =>
          new Response(JSON.stringify(body), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          })
        if (String(input).includes('/api/tags')) return json({ tagList: [] })
        return json({
          bookmarkList: [
            payload('id-vue', 'Vue docs', 'https://vuejs.org'),
            payload('id-rust', 'Rust book', 'https://doc.rust-lang.org'),
          ],
        })
      }),
    )
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('prints only the matches as a table', async () => {
    // ACT
    await runSearch(['vue'], { collection: COLLECTION }, cmdWith())

    // ASSERT — one row plus the header, and the other bookmark absent
    const table = out.join('\n')
    expect(table).toContain('Vue docs')
    expect(table).not.toContain('Rust book')
  })

  it('emits the matches as JSON', async () => {
    // ACT
    await runSearch(['rust'], { collection: COLLECTION, format: 'json' }, cmdWith())

    // ASSERT
    const parsed = JSON.parse(out.join('\n')) as { id: string }[]
    expect(parsed).toHaveLength(1)
    expect(parsed[0]!.id).toBe('id-rust')
  })

  it('emits one ID per line for piping', async () => {
    await runSearch(['docs'], { collection: COLLECTION, format: 'ids' }, cmdWith())
    expect(out).toEqual(['id-vue'])
  })

  it('reports an empty result on stderr and leaves stdout clean', async () => {
    // ACT
    await runSearch(['nothing-matches-this'], { collection: COLLECTION }, cmdWith())

    // ASSERT — like grep: not an error, and nothing on stdout for a pipeline
    expect(out).toEqual([])
    expect(err.join('')).toContain('No bookmarks matched')
  })

  it('says nothing at all on an empty result when piping', async () => {
    // ACT
    await runSearch(['nothing-matches-this'], { collection: COLLECTION, format: 'ids' }, cmdWith())

    // ASSERT — a `while read` loop must see zero lines, not a message
    expect(out).toEqual([])
  })
})
