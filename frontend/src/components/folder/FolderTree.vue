<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { computed, ref } from 'vue'
import type { FolderJson } from '@/api/generated'
import FolderTreeNode from '@/components/folder/FolderTreeNode.vue'
import RenameFolderDialog from '@/components/folder/RenameFolderDialog.vue'
import { ConfirmDialog } from '@/components/ui'

const { t } = useI18n()
const folderStore = useFolderStore()
const notification = useNotificationStore()

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

const renamingFolder = ref<FolderJson | null>(null)
const showRenameDialog = ref(false)
const deletingFolder = ref<FolderJson | null>(null)
const showDeleteConfirm = ref(false)

const tree = computed<FolderNode[]>(() => {
  const folders = folderStore.folders
  const byParentId = new Map<string, FolderJson[]>()
  const roots: FolderJson[] = []

  for (const f of folders) {
    const pid = f.data.parentId
    if (pid) {
      const list = byParentId.get(pid) ?? []
      list.push(f)
      byParentId.set(pid, list)
    } else {
      roots.push(f)
    }
  }

  function buildNodes(parentFolders: FolderJson[]): FolderNode[] {
    return parentFolders.map((folder) => ({
      folder,
      children: buildNodes(byParentId.get(folder.id) ?? []),
    }))
  }

  return buildNodes(roots)
})

function handleCreateSubfolder(parentId: string) {
  emit('createSubfolder', parentId)
}

function handleRename(folder: FolderJson) {
  renamingFolder.value = folder
  showRenameDialog.value = true
}

function handleRenameDialogUpdate(open: boolean) {
  showRenameDialog.value = open
  if (!open) renamingFolder.value = null
}

function handleDelete(folder: FolderJson) {
  deletingFolder.value = folder
  showDeleteConfirm.value = true
}

function handleDeleteDialogUpdate(open: boolean) {
  showDeleteConfirm.value = open
  if (!open) deletingFolder.value = null
}

async function confirmDeleteFolder() {
  if (!deletingFolder.value) return
  try {
    await folderStore.deleteFolder(deletingFolder.value.id)
  } catch (err) {
    notification.handleApiError(err, t('folder.deleteError'))
  } finally {
    deletingFolder.value = null
    showDeleteConfirm.value = false
  }
}
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
      @create-subfolder="handleCreateSubfolder"
      @rename="handleRename"
      @delete="handleDelete"
    />

    <RenameFolderDialog
      :folder="renamingFolder"
      v-model:open="showRenameDialog"
      @update:open="handleRenameDialogUpdate"
      @saved="handleRenameDialogUpdate(false)"
    />

    <ConfirmDialog
      v-model:open="showDeleteConfirm"
      :title="t('folder.deleteTitle')"
      :message="t('folder.deleteConfirm')"
      @confirmed="confirmDeleteFolder"
      @update:open="handleDeleteDialogUpdate"
    />
  </div>
</template>
