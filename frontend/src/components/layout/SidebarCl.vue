<script setup lang="ts">
import { FolderTree, CreateFolderDialog } from '@/components/folder'
import TagList from '@/components/tag/TagList.vue'
import { ButtonCl } from '@/components/ui'
import BuildversionCl from '@/components/ui/BuildversionCl.vue'
import { Folder, Plus, Tag } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { computed, ref, watch } from 'vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const folderStore = useFolderStore()

const collectionId = computed(() => collectionStore.currentCollectionId ?? '')
const showCreateFolder = ref(false)
const subfolderParentId = ref<string | undefined>(undefined)

watch(collectionId, (id) => {
  if (id) {
    folderStore.fetchFolders()
  }
}, { immediate: true })

watch(showCreateFolder, (open) => {
  if (!open) {
    subfolderParentId.value = undefined
  }
})

function handleCreateSubfolder(parentId: string) {
  subfolderParentId.value = parentId
  showCreateFolder.value = true
}

interface Props {
  class?: string
}

const props = defineProps<Props>()
</script>

<template>
  <div :class="['flex flex-col h-full', props.class]">
    <div class="p-4 border-b border-border">
      <h2 class="text-lg font-semibold text-foreground">{{ t('sidebar.bookmarks') }}</h2>
    </div>

    <div class="flex-1 overflow-y-auto p-2">
      <div
        class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors mb-1"
        :class="folderStore.selectedFolderId === null
          ? 'bg-accent text-accent-foreground'
          : 'hover:bg-accent hover:text-accent-foreground text-muted-foreground'"
        @click="folderStore.selectFolder(null)"
      >
        <Folder class="h-4 w-4 text-primary" />
        <span>{{ t('sidebar.allBookmarks') }}</span>
      </div>

      <FolderTree class-name="mt-2" @create-subfolder="handleCreateSubfolder" />

      <ButtonCl
        v-if="collectionId"
        variant="ghost"
        size="sm"
        class="w-full justify-start text-muted-foreground hover:text-foreground mt-2"
        @click="subfolderParentId = undefined; showCreateFolder = true"
      >
        <Plus class="h-4 w-4 mr-2" />
        {{ t('sidebar.newFolder') }}
      </ButtonCl>

      <CreateFolderDialog
        v-if="collectionId"
        v-model:open="showCreateFolder"
        :collection-id="collectionId"
        :parent-id="subfolderParentId"
      />
    </div>

    <!-- Tags Section -->
    <div class="border-t border-border">
      <div class="p-3 flex items-center justify-between">
        <span class="text-sm font-medium text-muted-foreground flex items-center gap-2">
          <Tag class="h-4 w-4" />
          {{ t('sidebar.tags') }}
        </span>
      </div>
      <TagList class-name="px-2 pb-2" />
    </div>
    <div class="border-t border-border">
      <div class="p-3 flex items-center justify-between">
        <BuildversionCl />
      </div>
    </div>
  </div>
</template>
