import type { ImportNodeJson } from '@/api/generated'
import { ImportNodeType } from '@/api/generated'
import { computed, ref, type Ref, watch } from 'vue'

export type FolderState = 'none' | 'some' | 'all'

/**
 * Transient tri-state selection model for the import review tree (UC-096).
 * Same spirit as UC-074's batch selection (`stores/selection.ts`): a `Set` of
 * bookmark ids, never persisted. Folders hold no state of their own — their
 * checkbox state is derived from their descendant bookmarks.
 *
 * Duplicates come straight from the server-computed {@link ImportNodeJson.duplicate}
 * flag — the client never re-normalizes URLs, so detection can't drift from the
 * server. Default selection is everything **except** duplicates (BR-181/BR-182):
 * when the tree changes, all non-duplicate bookmark ids are pre-selected.
 */
export function useImportSelection(tree: Ref<ImportNodeJson[]>) {
  const selected = ref<Set<string>>(new Set())
  const skipDuplicates = ref(true)

  // id -> node and folder -> descendant bookmark ids, rebuilt when the tree changes.
  const allBookmarkIds = computed(() => {
    const ids: string[] = []
    walk(tree.value, (n) => {
      if (n.type === ImportNodeType.Bookmark) ids.push(n.id)
    })
    return ids
  })

  /** Bookmark ids the server flagged as already in the destination collection. */
  const duplicateIds = computed(() => {
    const ids = new Set<string>()
    walk(tree.value, (n) => {
      if (n.type === ImportNodeType.Bookmark && n.duplicate) ids.add(n.id)
    })
    return ids
  })

  // Pre-select everything except duplicates whenever the source data changes.
  watch(
    [tree, duplicateIds],
    () => {
      const next = new Set<string>()
      for (const id of allBookmarkIds.value) {
        if (!(skipDuplicates.value && duplicateIds.value.has(id))) next.add(id)
      }
      selected.value = next
    },
    { immediate: true },
  )

  function isSelected(id: string): boolean {
    return selected.value.has(id)
  }

  function isDuplicate(id: string): boolean {
    return duplicateIds.value.has(id)
  }

  function bookmarkIdsUnder(node: ImportNodeJson): string[] {
    const ids: string[] = []
    walk([node], (n) => {
      if (n.type === ImportNodeType.Bookmark) ids.push(n.id)
    })
    return ids
  }

  function folderState(node: ImportNodeJson): FolderState {
    const ids = bookmarkIdsUnder(node)
    if (ids.length === 0) return 'none'
    const on = ids.filter((id) => selected.value.has(id)).length
    if (on === 0) return 'none'
    if (on === ids.length) return 'all'
    return 'some'
  }

  function toggleBookmark(id: string): void {
    const next = new Set(selected.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selected.value = next
  }

  /** All selected → deselect the subtree; otherwise → select the whole subtree. */
  function toggleFolder(node: ImportNodeJson): void {
    const ids = bookmarkIdsUnder(node)
    const next = new Set(selected.value)
    const allOn = ids.length > 0 && ids.every((id) => next.has(id))
    for (const id of ids) {
      if (allOn) next.delete(id)
      else next.add(id)
    }
    selected.value = next
  }

  function selectAll(): void {
    selected.value = new Set(allBookmarkIds.value)
  }

  function clear(): void {
    selected.value = new Set()
  }

  /** Toggle the "skip already in library" pill: add/remove all duplicate ids at once. */
  function setSkipDuplicates(on: boolean): void {
    skipDuplicates.value = on
    const next = new Set(selected.value)
    for (const id of duplicateIds.value) {
      if (on) next.delete(id)
      else next.add(id)
    }
    selected.value = next
  }

  const selectedCount = computed(() => selected.value.size)
  const totalBookmarks = computed(() => allBookmarkIds.value.length)
  const duplicateCount = computed(() => duplicateIds.value.size)

  /**
   * Build the pruned tree of kept nodes for the commit call: a folder is kept
   * iff it has at least one kept descendant bookmark; a bookmark iff selected.
   */
  function selectedTree(): ImportNodeJson[] {
    return prune(tree.value, selected.value)
  }

  return {
    skipDuplicates,
    isSelected,
    isDuplicate,
    folderState,
    bookmarkIdsUnder,
    toggleBookmark,
    toggleFolder,
    selectAll,
    clear,
    setSkipDuplicates,
    selectedCount,
    totalBookmarks,
    duplicateCount,
    selectedTree,
  }
}

function walk(nodes: ImportNodeJson[], visit: (n: ImportNodeJson) => void): void {
  for (const n of nodes) {
    visit(n)
    if (n.children) walk(n.children, visit)
  }
}

function prune(nodes: ImportNodeJson[], selected: Set<string>): ImportNodeJson[] {
  const out: ImportNodeJson[] = []
  for (const n of nodes) {
    if (n.type === ImportNodeType.Bookmark) {
      if (selected.has(n.id)) out.push(n)
    } else {
      const children = prune(n.children ?? [], selected)
      if (children.length > 0) out.push({ ...n, children })
    }
  }
  return out
}
