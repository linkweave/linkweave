import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCollectionStore = defineStore('collection', () => {
  const currentCollectionId = ref<string | null>(null)

  function setCurrentCollectionId(id: string | null) {
    currentCollectionId.value = id
  }


  return {
    currentCollectionId,
    setCurrentCollectionId,
  }
})
