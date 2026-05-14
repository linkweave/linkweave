import type { BookmarkJson } from '@/api/generated'
import { sortBookmarks, type SortPref } from './bookmarkSort'

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
