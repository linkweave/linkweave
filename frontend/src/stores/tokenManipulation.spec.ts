// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { useFolderStore } from '@/stores/folder'
import { useCollectionStore } from '@/stores/collection'
import { useTagStore } from '@/stores/tag'

function seedFolders(folders: Array<{ id: string; name: string; parentId?: string | null }>) {
  const collectionStore = useCollectionStore()
  // Minimal stand-in for CollectionInfoJson — selectFolder only reads `folders`.
  collectionStore.collectionInfo = {
    folders: folders.map(f => ({
      id: f.id,
      data: { name: f.name, parentId: f.parentId ?? null },
    })),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any
}

describe('bookmark store – removeTokensWhere', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('removes matching tokens and updates searchQuery', () => {
    const store = useSearchQueryStore()
    store.searchQuery = '#a folder:b c'
    store.removeTokensWhere(t => t.kind === 'tag')
    expect(store.searchQuery).toBe('folder:b c')
  })

  it('removes all matching tokens', () => {
    const store = useSearchQueryStore()
    store.searchQuery = '#a #b #c'
    store.removeTokensWhere(t => t.kind === 'tag')
    expect(store.searchQuery).toBe('')
  })

  it('preserves non-matching tokens', () => {
    const store = useSearchQueryStore()
    store.searchQuery = '#a folder:b -c'
    store.removeTokensWhere(t => t.kind === 'text')
    expect(store.searchQuery).toBe('#a folder:b')
  })

  it('removes under: tokens by predicate', () => {
    const store = useSearchQueryStore()
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
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    folderStore.selectFolder('f-123')
    expect(searchQueryStore.searchQuery).toBe('under:f-123')
  })

  it('replaces an existing under: token when a different folder is selected', () => {
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    searchQueryStore.searchQuery = 'under:f-old #a'
    folderStore.selectFolder('f-new')
    expect(searchQueryStore.searchQuery).toBe('#a under:f-new')
  })

  it('removes the under: token when deselecting with null', () => {
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    searchQueryStore.searchQuery = 'under:f-1 #a'
    folderStore.selectFolder(null)
    expect(searchQueryStore.searchQuery).toBe('#a')
  })

  it('re-selecting the same folder is a no-op (toggle lives in the caller)', () => {
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    searchQueryStore.searchQuery = 'under:f-1'
    folderStore.selectFolder('f-1')
    expect(searchQueryStore.searchQuery).toBe('under:f-1')
  })

  it('leaves folder: tokens untouched', () => {
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    searchQueryStore.searchQuery = 'folder:work under:f-1'
    folderStore.selectFolder('f-2')
    expect(searchQueryStore.searchQuery).toBe('folder:work under:f-2')
  })

  it('uses the folder name when it is unique', () => {
    seedFolders([
      { id: 'f-1', name: 'Inbox' },
      { id: 'f-2', name: 'Archive' },
    ])
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    folderStore.selectFolder('f-1')
    expect(searchQueryStore.searchQuery).toBe('under:Inbox')
  })

  it('quotes the folder name when it contains whitespace', () => {
    seedFolders([{ id: 'f-1', name: 'My Stuff' }])
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    folderStore.selectFolder('f-1')
    expect(searchQueryStore.searchQuery).toBe('under:"My Stuff"')
  })

  it('falls back to the folder id when the name is duplicated', () => {
    seedFolders([
      { id: 'f-prod', name: 'prod', parentId: 'svc-a' },
      { id: 'f-prod-2', name: 'prod', parentId: 'svc-b' },
    ])
    const searchQueryStore = useSearchQueryStore()
    const folderStore = useFolderStore()
    folderStore.selectFolder('f-prod')
    expect(searchQueryStore.searchQuery).toBe('under:f-prod')
  })
})

describe('tag store – clearTagFilter', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('removes all tag tokens', () => {
    const searchQueryStore = useSearchQueryStore()
    const tagStore = useTagStore()
    searchQueryStore.searchQuery = '#a #b folder:work'
    tagStore.clearTagFilter()
    expect(searchQueryStore.searchQuery).toBe('folder:work')
  })

  it('removes negated tag tokens too', () => {
    const searchQueryStore = useSearchQueryStore()
    const tagStore = useTagStore()
    searchQueryStore.searchQuery = '#a -#b note:test'
    tagStore.clearTagFilter()
    expect(searchQueryStore.searchQuery).toBe('note:test')
  })

  it('results in empty query when only tags were present', () => {
    const searchQueryStore = useSearchQueryStore()
    const tagStore = useTagStore()
    searchQueryStore.searchQuery = '#alpha -#beta'
    tagStore.clearTagFilter()
    expect(searchQueryStore.searchQuery).toBe('')
  })
})
