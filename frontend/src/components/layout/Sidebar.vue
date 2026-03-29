<script setup lang="ts">
import FolderTree from '@/components/folder/FolderTree.vue'
import TagList from '@/components/tag/TagList.vue'
import {Button} from '@/components/ui'
import Buildversion from '@/components/ui/Buildversion.vue';
import {Folder, Plus, Tag} from 'lucide-vue-next'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()

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

    <!-- Folder Tree -->
    <div class="flex-1 overflow-y-auto p-2">
      <div
        class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors bg-accent text-accent-foreground mb-1"
      >
        <Folder class="h-4 w-4 text-primary" />
        <span>{{ t('sidebar.allBookmarks') }}</span>
      </div>

      <FolderTree class-name="mt-2" />

      <Button
        variant="ghost"
        size="sm"
        class="w-full justify-start text-muted-foreground hover:text-foreground mt-2"
      >
        <Plus class="h-4 w-4 mr-2" />
        {{ t('sidebar.newFolder') }}
      </Button>
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
       <Buildversion/>
     </div>

    </div>
  </div>
</template>
