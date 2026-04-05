import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { TagResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { TagJson, TagSaveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useBookmarkStore } from '@/stores/bookmark'

const tagApi = new TagResourceApi(config)

export const useTagStore = defineStore('tag', () => {
  const collectionStore = useCollectionStore()

  const tags = computed<TagJson[]>(() =>
    collectionStore.collectionInfo?.tags ?? []
  )

  const loading = computed(() => collectionStore.loading)

  const selectedTagIds = ref<Set<string>>(new Set())

  function toggleTag(tagId: string) {
    const updated = new Set(selectedTagIds.value)
    if (updated.has(tagId)) {
      updated.delete(tagId)
    } else {
      updated.add(tagId)
    }
    selectedTagIds.value = updated
  }

  function clearTagFilter() {
    selectedTagIds.value = new Set()
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
    const bookmarkStore = useBookmarkStore()
    patchTags((list) => list.filter((t) => t.id !== tagId))

    selectedTagIds.value = new Set([...selectedTagIds.value].filter((id) => id !== tagId))

    // Remove the tag from all bookmarks in the store
    for (const bookmark of bookmarkStore.bookmarks) {
      if (bookmark.data.tagIds) {
        bookmark.data.tagIds.delete(tagId)
      }
    }
  }

  return {
    tags,
    loading,
    selectedTagIds,
    createTag,
    updateTag,
    deleteTag,
    toggleTag,
    clearTagFilter,
  }
})
