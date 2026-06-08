import { defineStore } from 'pinia'
import { computed } from 'vue'
import { TagResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { TagJson, TagSaveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useSearchQueryStore } from '@/stores/searchQuery'

const tagApi = new TagResourceApi(config)

export const useTagStore = defineStore('tag', () => {
  const collectionStore = useCollectionStore()

  const tags = computed<TagJson[]>(() =>
    collectionStore.collectionInfo?.tags ?? []
  )

  const loading = computed(() => collectionStore.loading)

  // Sidebar tag selection is a derived view over the bookmark search query:
  // a tag is "selected" iff its name appears as an active (non-negated) `#tag`
  // token, and "excluded" iff it appears as a negated one. Toggling a row
  // writes back into the query string so pills and sidebar stay in sync.
  function tagIdsByTokenNeg(neg: boolean): Set<string> {
    const searchQueryStore = useSearchQueryStore()
    const names = new Set<string>()
    for (const t of searchQueryStore.queryTokens) {
      if (t.kind === 'tag' && t.neg === neg) names.add(t.value.toLowerCase())
    }
    const ids = new Set<string>()
    if (names.size === 0) return ids
    for (const tag of tags.value) {
      if (names.has(tag.data.name.toLowerCase())) ids.add(tag.id)
    }
    return ids
  }

  const selectedTagIds = computed<Set<string>>(() => tagIdsByTokenNeg(false))
  const excludedTagIds = computed<Set<string>>(() => tagIdsByTokenNeg(true))

  function toggleTag(tagId: string, modifier?: 'exclude') {
    const tag = tags.value.find(t => t.id === tagId)
    if (!tag) return
    const searchQueryStore = useSearchQueryStore()
    searchQueryStore.toggleQueryToken({ kind: 'tag', value: tag.data.name, neg: false }, modifier)
  }

  function clearTagFilter() {
    const searchQueryStore = useSearchQueryStore()
    searchQueryStore.removeTokensWhere(t => t.kind === 'tag')
  }

  function patchTags(updater: (list: TagJson[]) => TagJson[]) {
    const info = collectionStore.collectionInfo
    if (info) {
      info.tags = updater(info.tags ?? [])
    }
  }

  async function createTag(data: TagSaveJson): Promise<TagJson> {
    const tag = await tagApi.apiTagsPost({ tagSaveJson: data })
    patchTags(list => [...list, tag])
    return tag
  }

  async function updateTag(tagId: string, data: TagSaveJson): Promise<TagJson> {
    const updated = await tagApi.apiTagsTagIdPut({ tagId, tagSaveJson: data })
    patchTags(list => {
      const idx = list.findIndex(t => t.id === tagId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function deleteTag(tagId: string): Promise<void> {
    await tagApi.apiTagsTagIdDelete({ tagId })
    patchTags((list) => list.filter((t) => t.id !== tagId))

    // No need to clean selectedTagIds: it is derived from the query tokens, and
    // a token referencing the now-deleted tag name simply matches nothing.

    // Refetch so bookmarks drop their reference to the now-deleted tag. Mirrors
    // deleteFolder and avoids a tag -> bookmark store dependency.
    if (collectionStore.currentCollectionId) {
      void collectionStore.fetchCollectionInfo(collectionStore.currentCollectionId)
    }
  }

  return {
    tags,
    loading,
    selectedTagIds,
    excludedTagIds,
    createTag,
    updateTag,
    deleteTag,
    toggleTag,
    clearTagFilter,
  }
})
