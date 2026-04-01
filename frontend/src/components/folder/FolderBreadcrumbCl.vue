<script setup lang="ts">
import { useFolderStore } from '@/stores/folder'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const folderStore = useFolderStore()
</script>

<template>
  <div v-if="folderStore.selectedFolderPath.length > 0" class="flex items-center gap-2 text-sm">
    <button
      class="text-muted-foreground hover:text-foreground transition-colors"
      @click="folderStore.selectFolder(null)"
    >
      {{ t('sidebar.allBookmarks') }}
    </button>
    <div v-for="(folder, index) in folderStore.selectedFolderPath" :key="folder.id" class="flex items-center gap-2">
      <span class="text-muted-foreground">/</span>
      <button
        :class="index === folderStore.selectedFolderPath.length - 1
          ? 'text-foreground font-medium'
          : 'text-muted-foreground hover:text-foreground transition-colors'"
        @click="folderStore.selectFolder(folder.id)"
      >
        {{ folder.data.name }}
      </button>
    </div>
  </div>
</template>
