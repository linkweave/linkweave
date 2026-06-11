import { useCollectionStore } from '@/stores/collection'
import { useUiStore, type BookmarkLayout } from '@/stores/ui'
import { computed, type ComputedRef } from 'vue'

/**
 * The layout the bookmark view actually renders: the per-collection setting
 * when present, otherwise the user's global toolbar preference. Single source
 * for CollectionView, BookmarkList, and the toolbar so the derivation cannot
 * drift between them.
 */
export function useEffectiveLayout(): ComputedRef<BookmarkLayout> {
  const collectionStore = useCollectionStore()
  const ui = useUiStore()
  return computed(() => collectionStore.settingsLayout ?? ui.bookmarkLayout)
}
