import { defineStore } from 'pinia'
import { computed } from 'vue'
import { PropertyDefinitionResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { PropertyDefinitionJson, PropertyDefinitionSaveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'

const propertyDefinitionApi = new PropertyDefinitionResourceApi(config)

export const usePropertyStore = defineStore('property', () => {
  const collectionStore = useCollectionStore()

  // Definitions are loaded as part of the collection's `info` payload, so the
  // store does not maintain its own fetch state — it derives from there and
  // patches the same source on writes. Matches the folder/tag store pattern.
  const definitions = computed<PropertyDefinitionJson[]>(() =>
    collectionStore.collectionInfo?.propertyDefinitions ?? [],
  )

  const loading = computed(() => collectionStore.loading)

  function patchDefinitions(updater: (list: PropertyDefinitionJson[]) => PropertyDefinitionJson[]) {
    const info = collectionStore.collectionInfo
    if (info) {
      info.propertyDefinitions = updater(info.propertyDefinitions ?? [])
    }
  }

  async function createDefinition(data: PropertyDefinitionSaveJson): Promise<PropertyDefinitionJson> {
    const created = await propertyDefinitionApi.apiPropertyDefinitionsPost({
      propertyDefinitionSaveJson: data,
    })
    patchDefinitions(list => [...list, created])
    return created
  }

  async function updateDefinition(
    definitionId: string,
    data: PropertyDefinitionSaveJson,
  ): Promise<PropertyDefinitionJson> {
    const updated = await propertyDefinitionApi.apiPropertyDefinitionsDefinitionIdPut({
      definitionId,
      propertyDefinitionSaveJson: data,
    })
    patchDefinitions(list => {
      const idx = list.findIndex(d => d.id === definitionId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function deleteDefinition(definitionId: string): Promise<void> {
    await propertyDefinitionApi.apiPropertyDefinitionsDefinitionIdDelete({ definitionId })
    patchDefinitions(list => list.filter(d => d.id !== definitionId))
  }

  async function fetchUsage(definitionId: string): Promise<number> {
    const usage = await propertyDefinitionApi.apiPropertyDefinitionsDefinitionIdUsageGet({ definitionId })
    return usage.affectedBookmarks ?? 0
  }

  return {
    definitions,
    loading,
    createDefinition,
    updateDefinition,
    deleteDefinition,
    fetchUsage,
  }
})
