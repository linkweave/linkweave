import type { BookmarkJson } from '@/api/generated'
import { isNeverOpened, sortBookmarks, SYSTEM_DEFAULT_SORT, type SortPref } from './bookmarkSort'

function bm(opts: {
  id: string
  title?: string
  createdAt?: string
  clickCount?: number
  lastClickedAt?: string | null
}): BookmarkJson {
  return {
    id: opts.id,
    entityInfo: {
      timestampErstellt: new Date(opts.createdAt ?? '2026-01-01T00:00:00Z'),
      timestampMutiert: new Date(opts.createdAt ?? '2026-01-01T00:00:00Z'),
      userErstellt: 'u',
      userMutiert: 'u',
    },
    data: {
      title: opts.title ?? opts.id,
      url: `https://example.com/${opts.id}`,
    } as BookmarkJson['data'],
    clickCount: opts.clickCount ?? 0,
    lastClickedAt: opts.lastClickedAt ? new Date(opts.lastClickedAt) : undefined,
    propertyValues: [],
  }
}

const ASC: (f: SortPref['field']) => SortPref = (field) => ({ field, direction: 'ASC' })
const DESC: (f: SortPref['field']) => SortPref = (field) => ({ field, direction: 'DESC' })

describe('sortBookmarks', () => {
  it('sorts titles A→Z case-insensitively', () => {
    const list = [bm({ id: '1', title: 'banana' }), bm({ id: '2', title: 'Apple' }), bm({ id: '3', title: 'cherry' })]
    expect(sortBookmarks(list, ASC('TITLE')).items.map((b) => b.id)).toEqual(['2', '1', '3'])
  })

  it('sorts DATE_ADDED newest first by default (DESC)', () => {
    const list = [
      bm({ id: 'old', createdAt: '2026-01-01T00:00:00Z' }),
      bm({ id: 'new', createdAt: '2026-03-01T00:00:00Z' }),
      bm({ id: 'mid', createdAt: '2026-02-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, DESC('DATE_ADDED')).items.map((b) => b.id)).toEqual(['new', 'mid', 'old'])
  })

  it('tie-breaks on createdAt desc when sort values match', () => {
    const list = [
      bm({ id: 'a', title: 'same', createdAt: '2026-01-01T00:00:00Z' }),
      bm({ id: 'b', title: 'same', createdAt: '2026-03-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, ASC('TITLE')).items.map((b) => b.id)).toEqual(['b', 'a'])
  })

  it('sinks never-clicked bookmarks to the end when sorting by LAST_CLICKED', () => {
    const list = [
      bm({ id: 'never1', lastClickedAt: null, createdAt: '2026-03-01T00:00:00Z' }),
      bm({ id: 'recent', lastClickedAt: '2026-04-01T00:00:00Z' }),
      bm({ id: 'never2', lastClickedAt: null, createdAt: '2026-02-01T00:00:00Z' }),
      bm({ id: 'old', lastClickedAt: '2026-02-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, DESC('LAST_CLICKED')).items.map((b) => b.id)).toEqual([
      'recent',
      'old',
      'never1',
      'never2',
    ])
  })

  it('sinks zero-click bookmarks to the end when sorting by CLICK_COUNT', () => {
    const list = [
      bm({ id: 'zero', clickCount: 0, createdAt: '2026-03-01T00:00:00Z' }),
      bm({ id: 'lots', clickCount: 10 }),
      bm({ id: 'some', clickCount: 3 }),
    ]
    expect(sortBookmarks(list, DESC('CLICK_COUNT')).items.map((b) => b.id)).toEqual(['lots', 'some', 'zero'])
  })

  it('never-opened group is ordered by createdAt desc regardless of direction', () => {
    const list = [
      bm({ id: 'newNever', lastClickedAt: null, createdAt: '2026-04-01T00:00:00Z' }),
      bm({ id: 'oldNever', lastClickedAt: null, createdAt: '2026-01-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, ASC('LAST_CLICKED')).items.map((b) => b.id)).toEqual(['newNever', 'oldNever'])
    expect(sortBookmarks(list, DESC('LAST_CLICKED')).items.map((b) => b.id)).toEqual(['newNever', 'oldNever'])
  })
})

describe('sortBookmarks neverOpenedCount', () => {
  it('returns 0 for non-click-based sorts', () => {
    const list = [bm({ id: '1', clickCount: 0, lastClickedAt: null })]
    expect(sortBookmarks(list, ASC('TITLE')).neverOpenedCount).toBe(0)
    expect(sortBookmarks(list, DESC('DATE_ADDED')).neverOpenedCount).toBe(0)
  })

  it('counts bookmarks with no lastClickedAt for LAST_CLICKED', () => {
    const list = [
      bm({ id: '1', lastClickedAt: null }),
      bm({ id: '2', lastClickedAt: '2026-01-01T00:00:00Z' }),
      bm({ id: '3', lastClickedAt: null }),
    ]
    expect(sortBookmarks(list, DESC('LAST_CLICKED')).neverOpenedCount).toBe(2)
  })

  it('counts zero-click bookmarks for CLICK_COUNT', () => {
    const list = [bm({ id: '1', clickCount: 0 }), bm({ id: '2', clickCount: 5 })]
    expect(sortBookmarks(list, DESC('CLICK_COUNT')).neverOpenedCount).toBe(1)
  })
})

describe('isNeverOpened', () => {
  it('is always false for non-click-based fields', () => {
    const never = bm({ id: '1', lastClickedAt: null, clickCount: 0 })
    expect(isNeverOpened(never, 'TITLE')).toBe(false)
    expect(isNeverOpened(never, 'DATE_ADDED')).toBe(false)
  })

  it('LAST_CLICKED: true only when never clicked', () => {
    expect(isNeverOpened(bm({ id: '1', lastClickedAt: null }), 'LAST_CLICKED')).toBe(true)
    expect(isNeverOpened(bm({ id: '1', lastClickedAt: '2026-01-01T00:00:00Z' }), 'LAST_CLICKED')).toBe(false)
  })

  it('CLICK_COUNT: true when zero or missing clicks', () => {
    expect(isNeverOpened(bm({ id: '1', clickCount: 0 }), 'CLICK_COUNT')).toBe(true)
    expect(isNeverOpened(bm({ id: '1', clickCount: 4 }), 'CLICK_COUNT')).toBe(false)
    expect(isNeverOpened({ ...bm({ id: '1' }), clickCount: undefined } as unknown as BookmarkJson, 'CLICK_COUNT')).toBe(
      true,
    )
  })
})

describe('sortBookmarks edge cases', () => {
  it('SYSTEM_DEFAULT_SORT is DATE_ADDED DESC', () => {
    expect(SYSTEM_DEFAULT_SORT).toEqual({ field: 'DATE_ADDED', direction: 'DESC' })
  })

  it('returns an empty result for an empty list (click-based and not)', () => {
    expect(sortBookmarks([], DESC('TITLE'))).toEqual({ items: [], neverOpenedCount: 0 })
    expect(sortBookmarks([], DESC('CLICK_COUNT'))).toEqual({ items: [], neverOpenedCount: 0 })
  })

  it('sorts DATE_ADDED oldest first when ASC', () => {
    const list = [
      bm({ id: 'new', createdAt: '2026-03-01T00:00:00Z' }),
      bm({ id: 'old', createdAt: '2026-01-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, ASC('DATE_ADDED')).items.map((b) => b.id)).toEqual(['old', 'new'])
  })

  it('sorts clicked bookmarks ascending by CLICK_COUNT when ASC', () => {
    const list = [bm({ id: 'lots', clickCount: 10 }), bm({ id: 'few', clickCount: 2 })]
    expect(sortBookmarks(list, ASC('CLICK_COUNT')).items.map((b) => b.id)).toEqual(['few', 'lots'])
  })

  it('sorts clicked bookmarks ascending by LAST_CLICKED when ASC', () => {
    const list = [
      bm({ id: 'recent', lastClickedAt: '2026-04-01T00:00:00Z' }),
      bm({ id: 'older', lastClickedAt: '2026-01-01T00:00:00Z' }),
    ]
    expect(sortBookmarks(list, ASC('LAST_CLICKED')).items.map((b) => b.id)).toEqual(['older', 'recent'])
  })

  it('treats a missing createdAt as the oldest (0)', () => {
    const dated = bm({ id: 'dated', createdAt: '2026-01-01T00:00:00Z' })
    const noDate = { ...bm({ id: 'nodate' }), entityInfo: undefined } as unknown as BookmarkJson
    expect(sortBookmarks([noDate, dated], DESC('DATE_ADDED')).items.map((b) => b.id)).toEqual(['dated', 'nodate'])
  })

  it('treats a missing title as an empty string', () => {
    const titled = bm({ id: 'titled', title: 'Apple' })
    const untitled = { ...bm({ id: 'untitled' }), data: { url: 'https://x' } as BookmarkJson['data'] }
    expect(sortBookmarks([titled, untitled], ASC('TITLE')).items.map((b) => b.id)).toEqual(['untitled', 'titled'])
  })
})
