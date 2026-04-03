import { defineStore } from 'pinia'
import { ref } from 'vue'
import { TagResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { TagJson, TagSaveJson } from '@/api/generated'

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
    } catch {
      tags.value = []
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
    tags.value = tags.value.filter(t => t.id !== tagId)
    selectedTagIds.value.delete(tagId)
    selectedTagIds.value = new Set(selectedTagIds.value)
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
