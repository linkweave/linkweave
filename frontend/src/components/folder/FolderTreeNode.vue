<script setup lang="ts">
import { requireValue } from '@/lib/nullish.ts'
import { Folder, FolderOpen, ChevronRight, MoreHorizontal } from 'lucide-vue-next'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'radix-vue'
import type { FolderJson } from '@/api/generated'
import { reactive, ref } from 'vue'
import { useFolderStore } from '@/stores/folder'
import {
  DRAG_TYPE_BOOKMARK,
  DRAG_TYPE_FOLDER,
  draggingFolderId,
  getDraggingFolderId,
  isDraggingBookmark,
  isDraggingFolder,
  setDraggingFolderId,
} from '@/composables/useDragState'
import { useDndMove } from '@/composables/useDndMove'
import { useMediaQuery } from '@/composables/useMediaQuery'

const folderStore = useFolderStore()
const isTouch = useMediaQuery('(hover: none) and (pointer: coarse)')
const { moveBookmarkWithUndo, moveFolderWithUndo } = useDndMove()

withDefaults(defineProps<{
  nodes: FolderNode[]
  depth?: number
}>(), { depth: 0 })

const emit = defineEmits<{
  createSubfolder: [parentId: string]
  rename: [folder: FolderJson]
  delete: [folder: FolderJson]
}>()

interface FolderNode {
  folder: FolderJson
  children: FolderNode[]
}

const expanded = reactive<Record<string, boolean>>({})
const dragOverFolderId = ref<string | null>(null)

function isExpanded(folderId: string): boolean {
  return expanded[folderId] ?? true
}

function toggleExpand(folderId: string) {
  expanded[folderId] = !isExpanded(folderId)
}

// ── Available drop target indicator ─────────────────────────────────────────

function isAvailableTarget(folderId: string): boolean {
  if (isDraggingBookmark.value) return true
  if (isDraggingFolder.value) return draggingFolderId.value !== folderId
  return false
}

// ── Drag source (folder being dragged) ──────────────────────────────────────

function onFolderDragStart(event: DragEvent, folder: FolderJson) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_FOLDER, folder.id)
  setDraggingFolderId(folder.id)
  event.stopPropagation()
}

function onFolderDragEnd() {
  setDraggingFolderId(null)
}

// ── Drop target helpers ──────────────────────────────────────────────────────

function isDescendant(ancestorId: string, targetId: string): boolean {
  // Returns true if targetId is ancestorId or any descendant of ancestorId
  if (targetId === ancestorId) return true
  const folder = folderStore.folders.find(f => f.id === targetId)
  if (!folder?.data.parentId) return false
  return isDescendant(ancestorId, folder.data.parentId)
}

function isDragAccepted(event: DragEvent, targetFolderId: string): boolean {
  const types = event.dataTransfer?.types ?? []
  if (types.includes(DRAG_TYPE_BOOKMARK)) return true
  if (types.includes(DRAG_TYPE_FOLDER)) {
    const draggingId = getDraggingFolderId()
    // Prevent dropping a folder onto itself or one of its descendants
    if (!draggingId) return false
    if (draggingId === targetFolderId) return false
    return !isDescendant(draggingId, targetFolderId);

  }
  return false
}

function onDragOver(event: DragEvent, folder: FolderJson) {
  if (!isDragAccepted(event, folder.id)) return
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverFolderId.value = folder.id
}

function onDragLeave(event: DragEvent, folder: FolderJson) {
  // Only clear highlight when leaving the element entirely, not entering a child
  const related = event.relatedTarget as Node | null
  const el = event.currentTarget as HTMLElement
  if (related && el.contains(related)) return
  if (dragOverFolderId.value === folder.id) {
    dragOverFolderId.value = null
  }
}

async function onDrop(event: DragEvent, targetFolder: FolderJson) {
  event.preventDefault()
  event.stopPropagation()
  dragOverFolderId.value = null

  const types = event.dataTransfer?.types ?? []

  if (types.includes(DRAG_TYPE_BOOKMARK)) {
    const bookmarkId = event.dataTransfer!.getData(DRAG_TYPE_BOOKMARK)
    if (bookmarkId) await moveBookmarkWithUndo(bookmarkId, targetFolder.id)
    return
  }

  if (types.includes(DRAG_TYPE_FOLDER)) {
    const folderId = event.dataTransfer!.getData(DRAG_TYPE_FOLDER)
    if (!folderId) return
    if (folderId === targetFolder.id) return
    if (isDescendant(folderId, targetFolder.id)) return
    await moveFolderWithUndo(folderId, targetFolder.id)
  }
}
</script>

<template>
  <ul class="space-y-0.5">
    <li v-for="node in nodes" :key="node.folder.id">
      <DropdownMenuRoot>
        <div
          :draggable="!isTouch"
          class="group flex items-center gap-1 rounded-md py-1.5 pr-2 text-sm cursor-pointer transition-colors"
          :class="[
            folderStore.selectedFolderId === node.folder.id
              ? 'bg-accent text-accent-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
            dragOverFolderId === node.folder.id
              ? 'ring-2 ring-primary ring-inset'
              : isAvailableTarget(node.folder.id) ? 'ring-1 ring-primary/30' : '',
          ]"
          :style="{ paddingLeft: `${requireValue(depth) * 16 + 8}px` }"
          @click="folderStore.selectFolder(node.folder.id)"
          @dragstart="onFolderDragStart($event, node.folder)"
          @dragend="onFolderDragEnd"
          @dragover="onDragOver($event, node.folder)"
          @dragleave="onDragLeave($event, node.folder)"
          @drop="onDrop($event, node.folder)"
        >
          <button
            class="p-0.5 rounded transition-transform"
            :class="{ 'invisible': node.children.length === 0 }"
            @click.stop="toggleExpand(node.folder.id)"
          >
            <ChevronRight
              class="h-3.5 w-3.5 transition-transform"
              :class="{ 'rotate-90': isExpanded(node.folder.id) && node.children.length > 0 }"
            />
          </button>
          <component
            :is="isExpanded(node.folder.id) && node.children.length > 0 ? FolderOpen : Folder"
            class="h-4 w-4 shrink-0"
            :class="node.folder.data.color ? '' : 'text-primary'"
            :style="node.folder.data.color ? { color: node.folder.data.color } : undefined"
          />
          <span class="flex-1 truncate">{{ node.folder.data.name }}</span>
          <DropdownMenuTrigger as-child>
            <button
              class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground"
              @click.stop
            >
              <MoreHorizontal class="h-3.5 w-3.5" />
            </button>
          </DropdownMenuTrigger>
        </div>
        <DropdownMenuPortal>
          <DropdownMenuContent
            class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
            align="end"
            :side-offset="4"
          >
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('createSubfolder', node.folder.id)"
            >
              {{ $t('folder.createSubfolder') }}
            </DropdownMenuItem>
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('rename', node.folder)"
            >
              {{ $t('common.edit') }}
            </DropdownMenuItem>
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors text-destructive focus:text-destructive data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('delete', node.folder)"
            >
              {{ $t('common.delete') }}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>

      <FolderTreeNode
        v-if="node.children.length > 0 && isExpanded(node.folder.id)"
        :nodes="node.children"
        :depth="requireValue(depth) + 1"
        @create-subfolder="emit('createSubfolder', $event)"
        @rename="emit('rename', $event)"
        @delete="emit('delete', $event)"
      />
    </li>
  </ul>
</template>
