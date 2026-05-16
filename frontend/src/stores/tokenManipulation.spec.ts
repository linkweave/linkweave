// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'

describe('bookmark store – removeTokensWhere', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('removes matching tokens and updates searchQuery', () => {
    const store = useBookmarkStore()
    store.searchQuery = '#a folder:b c'
    store.removeTokensWhere(t => t.kind === 'tag')
    expect(store.searchQuery).toBe('folder:b c')
  })

  it('removes all matching tokens', () => {
    const store = useBookmarkStore()
    store.searchQuery = '#a #b #c'
    store.removeTokensWhere(t => t.kind === 'tag')
    expect(store.searchQuery).toBe('')
  })

  it('preserves non-matching tokens', () => {
    const store = useBookmarkStore()
    store.searchQuery = '#a folder:b -c'
    store.removeTokensWhere(t => t.kind === 'text')
    expect(store.searchQuery).toBe('#a folder:b')
  })

  it('removes under: tokens by predicate', () => {
    const store = useBookmarkStore()
    store.searchQuery = 'under:f1 #a folder:b'
    store.removeTokensWhere(t => t.kind === 'operator' && t.key === 'under')
    expect(store.searchQuery).toBe('#a folder:b')
  })
})

describe('folder store – selectFolder', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('adds an under: token for the selected folder', () => {
    const bookmarkStore = useBookmarkStore()
    const folderStore = useFolderStore()
    folderStore.selectFolder('f-123')
    expect(bookmarkStore.searchQuery).toBe('under:f-123')
  })

  it('replaces an existing under: token when a different folder is selected', () => {
    const bookmarkStore = useBookmarkStore()
    const folderStore = useFolderStore()
    bookmarkStore.searchQuery = 'under:f-old #a'
    folderStore.selectFolder('f-new')
    expect(bookmarkStore.searchQuery).toBe('#a under:f-new')
  })

  it('removes the under: token when deselecting with null', () => {
    const bookmarkStore = useBookmarkStore()
    const folderStore = useFolderStore()
    bookmarkStore.searchQuery = 'under:f-1 #a'
    folderStore.selectFolder(null)
    expect(bookmarkStore.searchQuery).toBe('#a')
  })

  it('re-selecting the same folder is a no-op (toggle lives in the caller)', () => {
    const bookmarkStore = useBookmarkStore()
    const folderStore = useFolderStore()
    bookmarkStore.searchQuery = 'under:f-1'
    folderStore.selectFolder('f-1')
    expect(bookmarkStore.searchQuery).toBe('under:f-1')
  })

  it('leaves folder: tokens untouched', () => {
    const bookmarkStore = useBookmarkStore()
    const folderStore = useFolderStore()
    bookmarkStore.searchQuery = 'folder:work under:f-1'
    folderStore.selectFolder('f-2')
    expect(bookmarkStore.searchQuery).toBe('folder:work under:f-2')
  })
})

describe('tag store – clearTagFilter', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('removes all tag tokens', () => {
    const bookmarkStore = useBookmarkStore()
    const tagStore = useTagStore()
    bookmarkStore.searchQuery = '#a #b folder:work'
    tagStore.clearTagFilter()
    expect(bookmarkStore.searchQuery).toBe('folder:work')
  })

  it('removes negated tag tokens too', () => {
    const bookmarkStore = useBookmarkStore()
    const tagStore = useTagStore()
    bookmarkStore.searchQuery = '#a -#b note:test'
    tagStore.clearTagFilter()
    expect(bookmarkStore.searchQuery).toBe('note:test')
  })

  it('results in empty query when only tags were present', () => {
    const bookmarkStore = useBookmarkStore()
    const tagStore = useTagStore()
    bookmarkStore.searchQuery = '#alpha -#beta'
    tagStore.clearTagFilter()
    expect(bookmarkStore.searchQuery).toBe('')
  })
})
