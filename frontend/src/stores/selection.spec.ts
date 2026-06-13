// @vitest-environment happy-dom
import type { BookmarkJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useSelectionStore } from '@/stores/selection'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

// The collection store watches `currentCollectionId` and refetches the
// collection over HTTP — stub fetch so the last test doesn't leave a
// dangling request behind for happy-dom to abort at teardown.
vi.stubGlobal(
  'fetch',
  vi.fn(() =>
    Promise.resolve({ ok: false, status: 404, json: async () => ({}), text: async () => '' }),
  ),
)

// Five bookmarks with descending creation timestamps so the default sort
// (DATE_ADDED DESC) keeps them in insertion order: b0, b1, b2, b3, b4.
function bookmark(i: number): BookmarkJson {
  return {
    id: `b${i}`,
    data: {
      collectionId: 'c1',
      title: `Bookmark ${i}`,
      url: `https://example.com/${i}`,
      tagIds: new Set<string>(),
    },
    clickCount: 0,
    entityInfo: {
      timestampErstellt: new Date(Date.UTC(2026, 0, 10 - i)),
    },
  } as unknown as BookmarkJson
}

function seed(count: number) {
  const collectionStore = useCollectionStore()
  collectionStore.currentCollectionId = 'c1'
  collectionStore.collectionInfo = {
    bookmarks: Array.from({ length: count }, (_, i) => bookmark(i)),
  } as never
}

describe('selection store (UC-074)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts inactive and enters mode without selecting anything', () => {
    seed(3)
    const selection = useSelectionStore()

    expect(selection.selecting).toBe(false)
    selection.enterMode()
    expect(selection.selecting).toBe(true)
    expect(selection.count).toBe(0)
  })

  it('toggle selects/deselects, moves the anchor and implicitly enters the mode', () => {
    seed(3)
    const selection = useSelectionStore()

    selection.toggle('b1')
    expect(selection.selecting).toBe(true)
    expect(selection.isSelected('b1')).toBe(true)
    expect(selection.anchorId).toBe('b1')

    selection.toggle('b1')
    expect(selection.isSelected('b1')).toBe(false)
    // Mode stays on after deselecting the last item (selectMode is sticky).
    expect(selection.selecting).toBe(true)
  })

  it('shift-range adds from the anchor without moving it and never removes', () => {
    seed(5)
    const selection = useSelectionStore()

    selection.toggle('b1')
    selection.rangeSelectTo('b3')
    expect([...selection.selectedIds].sort()).toEqual(['b1', 'b2', 'b3'])
    // Anchor unchanged: extending the other way starts from b1 again.
    selection.rangeSelectTo('b0')
    expect([...selection.selectedIds].sort()).toEqual(['b0', 'b1', 'b2', 'b3'])
  })

  it('shift-range with no anchor degrades to a plain toggle', () => {
    seed(3)
    const selection = useSelectionStore()
    selection.enterMode()

    selection.rangeSelectTo('b2')
    expect([...selection.selectedIds]).toEqual(['b2'])
    expect(selection.anchorId).toBe('b2')
  })

  it('selectAll selects every rendered bookmark and reports allSelected', () => {
    seed(4)
    const selection = useSelectionStore()

    selection.selectAll()
    expect(selection.count).toBe(4)
    expect(selection.allSelected).toBe(true)
  })

  it('clearAndExit resets selection, anchor and mode', () => {
    seed(3)
    const selection = useSelectionStore()

    selection.toggle('b0')
    selection.clearAndExit()
    expect(selection.selecting).toBe(false)
    expect(selection.count).toBe(0)
    expect(selection.anchorId).toBeNull()
  })

  it('clears the selection when the collection changes', async () => {
    seed(3)
    const selection = useSelectionStore()
    const collectionStore = useCollectionStore()

    selection.toggle('b0')
    expect(selection.count).toBe(1)

    collectionStore.currentCollectionId = 'c2'
    await nextTick()
    expect(selection.count).toBe(0)
    expect(selection.selecting).toBe(false)
  })
})
