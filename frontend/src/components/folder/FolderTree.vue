<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'
import { computed } from 'vue'
import type { FolderJson } from '@/api/generated'
import FolderTreeNode from '@/components/folder/FolderTreeNode.vue'

const { t } = useI18n()
const folderStore = useFolderStore()

interface Props {
  className?: string
}

defineProps<Props>()

const emit = defineEmits<{
  createSubfolder: [parentId: string]
}>()

interface FolderNode {
  folder: FolderJson
  children: FolderNode[]
}

const tree = computed<FolderNode[]>(() => {
  const folders = folderStore.folders
  const byParentId = new Map<string, FolderJson[]>()
  const roots: FolderJson[] = []

  for (const f of folders) {
    const pid = f.data.parentId
    if (!pid) {
      roots.push(f)
    } else {
      const list = byParentId.get(pid) ?? []
      list.push(f)
      byParentId.set(pid, list)
    }
  }

  function buildNodes(parentFolders: FolderJson[]): FolderNode[] {
    return parentFolders.map(folder => ({
      folder,
      children: buildNodes(byParentId.get(folder.id) ?? []),
    }))
  }

  return buildNodes(roots)
})
</script>

<template>
  <div :class="className">
    <div v-if="folderStore.loading" class="text-sm text-muted-foreground p-2">
      {{ t('common.loading') }}
    </div>
    <div v-else-if="folderStore.folders.length === 0" class="text-sm text-muted-foreground p-2">
      {{ t('folder.none') }}
    </div>
    <FolderTreeNode
      v-else
      :nodes="tree"
      @create-subfolder="emit('createSubfolder', $event)"
    />
  </div>
</template>
