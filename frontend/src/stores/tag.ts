import { defineStore } from 'pinia'
import { ref } from 'vue'
import { TagResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { TagJson, TagSaveJson } from '@/api/generated'
import { useBookmarkStore } from '@/stores/bookmark'
import { useNotificationStore } from '@/stores/notification'

const tagApi = new TagResourceApi(config)

export const useTagStore = defineStore('tag', () => {
  const tags = ref<TagJson[]>([])
  const loading = ref(false)
  const selectedTagIds = ref<Set<string>>(new Set())

  function toggleTag(tagId: string) {
    if (selectedTagIds.value.has(tagId)) {
      selectedTagIds.value.delete(tagId)
    } else {
      selectedTagIds.value.add(tagId)
    }
    selectedTagIds.value = new Set(selectedTagIds.value)
  }

  function clearTagFilter() {
    selectedTagIds.value = new Set()
  }

  async function fetchTags(collectionId: string) {
    loading.value = true
    try {
      const result = await tagApi.apiTagsGet({ collectionId })
      tags.value = result.tagList ?? []
    } catch (err) {
      tags.value = []
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load tags')
    } finally {
      loading.value = false
    }
  }

  async function createTag(data: TagSaveJson): Promise<TagJson> {
    const tag = await tagApi.apiTagsPost({ tagSaveJson: data })
    tags.value.push(tag)
    return tag
  }

  async function updateTag(tagId: string, data: TagSaveJson): Promise<TagJson> {
    const updated = await tagApi.apiTagsTagIdPut({ tagId, tagSaveJson: data })
    const idx = tags.value.findIndex(t => t.id === tagId)
    if (idx !== -1) tags.value[idx] = updated
    return updated
  }

  async function deleteTag(tagId: string): Promise<void> {
    await tagApi.apiTagsTagIdDelete({ tagId })
    const bookmarkStore = useBookmarkStore()
    tags.value = tags.value.filter(t => t.id !== tagId)
    selectedTagIds.value.delete(tagId)
    selectedTagIds.value = new Set(selectedTagIds.value)

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
    fetchTags,
    createTag,
    updateTag,
    deleteTag,
    toggleTag,
    clearTagFilter,
  }
})
