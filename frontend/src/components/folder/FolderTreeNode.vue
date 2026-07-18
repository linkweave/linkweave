<script setup lang="ts">
import { requireValue } from '@/lib/nullish.ts'
import { Folder, FolderOpen, ChevronRight, MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { DropdownMenuContentLw, DropdownMenuItemLw } from '@/components/ui'
import type { FolderJson, FolderPositionJson } from '@/api/generated'
import { Placement } from '@/api/generated'
import { reactive, ref, watch } from 'vue'
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

// Where a dragged folder would land on this row (UC-102): the top/bottom edge
// zones insert before/after (insertion line), the middle nests (UC-012).
// Bookmarks always nest — edge zones only exist while a folder is dragged.
type DropZone = 'before' | 'after' | 'into'
const dragOverZone = ref<DropZone | null>(null)
const EDGE_ZONE_RATIO = 0.25

// Clear stale hover state when a drag ends anywhere (drop elsewhere or Esc),
// otherwise the last hovered row keeps its highlight.
watch([isDraggingFolder, isDraggingBookmark], ([folder, bookmark]) => {
  if (!folder && !bookmark) {
    dragOverFolderId.value = null
    dragOverZone.value = null
  }
})

function isExpanded(folderId: string): boolean {
  return expanded[folderId] ?? true
}

function isGrouped(node: FolderNode): boolean {
  return folderStore.selectedFolderId === node.folder.id
    && node.children.length > 0
    && isExpanded(node.folder.id)
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

function zoneFor(event: DragEvent): DropZone {
  if (!isDraggingFolder.value) return 'into'
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const y = event.clientY - rect.top
  if (y < rect.height * EDGE_ZONE_RATIO) return 'before'
  if (y > rect.height * (1 - EDGE_ZONE_RATIO)) return 'after'
  return 'into'
}

function onDragOver(event: DragEvent, folder: FolderJson) {
  if (!isDragAccepted(event, folder.id)) return
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverFolderId.value = folder.id
  dragOverZone.value = zoneFor(event)
}

function onDragLeave(event: DragEvent, folder: FolderJson) {
  // Only clear highlight when leaving the element entirely, not entering a child
  const related = event.relatedTarget as Node | null
  const el = event.currentTarget as HTMLElement
  if (related && el.contains(related)) return
  if (dragOverFolderId.value === folder.id) {
    dragOverFolderId.value = null
    dragOverZone.value = null
  }
}

// The "after" edge of an expanded folder with children visually points at the
// gap above its first child, so it inserts there; everywhere else the edges
// insert among the target's siblings.
function edgeDropTarget(
  node: FolderNode,
  zone: 'before' | 'after',
): { parentId: string | undefined; position: FolderPositionJson } {
  const firstChild = node.children[0]
  if (zone === 'after' && firstChild && isExpanded(node.folder.id)) {
    return { //insert before first child of folder
      parentId: node.folder.id,
      position: { anchorFolderId: firstChild.folder.id, placement: Placement.Before },
    }
  }
  return {
    parentId: node.folder.data.parentId,
    position: {
      anchorFolderId: node.folder.id,
      placement: zone === 'before' ? Placement.Before : Placement.After,
    },
  }
}

// True when the drop would land the folder exactly where it already is —
// skipped silently so the user doesn't get a "moved" notification for a no-op.
function isSamePosition(
  folderId: string,
  drop: { parentId: string | undefined; position: FolderPositionJson },
): boolean {
  const folder = folderStore.folders.find(f => f.id === folderId)
  if (!folder || folder.data.parentId !== drop.parentId) return false
  const siblings = folderStore.folders.filter(f => f.data.parentId === drop.parentId)
  const anchorIndex = siblings.findIndex(f => f.id === drop.position.anchorFolderId)
  const selfIndex = siblings.findIndex(f => f.id === folderId)
  if (anchorIndex < 0 || selfIndex < 0) return false
  return drop.position.placement === Placement.Before
    ? selfIndex === anchorIndex - 1
    : selfIndex === anchorIndex + 1
}

// Indent in Npx for the insertion line so it aligns with the level it inserts at:
// one level deeper when the "after" edge means "first child" (see edgeDropTarget).
function dropLineIndent(node: FolderNode, depth: number): string {
  const deeper =
    dragOverZone.value === 'after' && node.children.length > 0 && isExpanded(node.folder.id)
  return `${(deeper ? depth + 1 : depth) * 16 + 8}px`
}

async function onDrop(event: DragEvent, targetNode: FolderNode) {
  event.preventDefault()
  event.stopPropagation()
  const zone = zoneFor(event)
  dragOverFolderId.value = null
  dragOverZone.value = null

  const types = event.dataTransfer?.types ?? []
  const targetFolder = targetNode.folder

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
    if (zone === 'into') {
      await moveFolderWithUndo(folderId, targetFolder.id)
      return
    }
    const drop = edgeDropTarget(targetNode, zone)
    if (isSamePosition(folderId, drop)) return
    await moveFolderWithUndo(folderId, drop.parentId, drop.position)
  }
}
</script>

<template>
  <ul class="space-y-0.5">
    <li v-for="node in nodes" :key="node.folder.id" :class="isGrouped(node) ? 'folder-group' : ''">
      <DropdownMenuRoot>
        <div
          :draggable="!isTouch"
          :data-testid="`folder-row-${node.folder.id}`"
          :data-folder-name="node.folder.data.name"
          :data-selected="folderStore.selectedFolderId === node.folder.id ? 'true' : 'false'"
          class="s-row group flex items-center gap-1 rounded-md py-1.5 pr-2 text-sm cursor-pointer transition-colors"
          :class="[
            folderStore.selectedFolderId === node.folder.id
              ? 's-row-active bg-accent text-accent-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
            dragOverFolderId === node.folder.id && dragOverZone === 'into'
              ? 'ring-2 ring-primary ring-inset'
              : isAvailableTarget(node.folder.id) ? 'ring-1 ring-primary/30' : '',
            {
              'drop-line-before': dragOverFolderId === node.folder.id && dragOverZone === 'before',
              'drop-line-after': dragOverFolderId === node.folder.id && dragOverZone === 'after',
            },
          ]"
          :style="{
            paddingLeft: `${requireValue(depth) * 16 + 8}px`,
            '--drop-line-indent': dropLineIndent(node, requireValue(depth)),
          }"
          @click="folderStore.selectFolder(node.folder.id)"
          @dragstart="onFolderDragStart($event, node.folder)"
          @dragend="onFolderDragEnd"
          @dragover="onDragOver($event, node.folder)"
          @dragleave="onDragLeave($event, node.folder)"
          @drop="onDrop($event, node)"
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
              class="ml-auto h-8 w-8 [@media(hover:hover)]:h-5 [@media(hover:hover)]:w-5 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground"
              @click.stop
            >
              <MoreHorizontal class="h-3.5 w-3.5" />
            </button>
          </DropdownMenuTrigger>
        </div>
        <DropdownMenuContentLw class="min-w-[160px] z-50">
          <DropdownMenuItemLw @select="emit('createSubfolder', node.folder.id)">
            {{ $t('folder.createSubfolder') }}
          </DropdownMenuItemLw>
          <DropdownMenuItemLw @select="emit('rename', node.folder)">
            {{ $t('common.edit') }}
          </DropdownMenuItemLw>
          <DropdownMenuItemLw variant="destructive" @select="emit('delete', node.folder)">
            {{ $t('common.delete') }}
          </DropdownMenuItemLw>
        </DropdownMenuContentLw>
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

<style scoped>
.s-row {
  position: relative;
  /* default for --drop-line-indent; overridden by inline :style binding */
  --drop-line-indent: 8px;
}

/* Insertion line for edge-zone drops (UC-102), drawn in the 2px gap between rows */
.drop-line-before::before,
.drop-line-after::after {
  content: '';
  position: absolute;
  left: var(--drop-line-indent, 8px);
  right: 8px;
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
  pointer-events: none;
  z-index: 1;
}

.drop-line-before::before {
  top: -2px;
}

.drop-line-after::after {
  bottom: -2px;
}

.folder-group {
  --group-border: color-mix(in oklab, var(--color-primary) 22%, transparent);
  --group-bg: color-mix(in oklab, var(--color-primary) 3.5%, var(--color-background));
  --group-active-bg: color-mix(in oklab, var(--color-primary) 15%, transparent);
  --group-hover-bg: color-mix(in oklab, var(--color-foreground) 5%, transparent);

  border-radius: 7px;
  /* inset shadow instead of border to avoid a 1px layout shift on selection */
  box-shadow: inset 0 0 0 1px var(--group-border);
  background: var(--group-bg);
  /* `clip` with margin lets focus outlines on the last child remain visible
     past the rounded corners, unlike `hidden`. */
  overflow: clip;
  overflow-clip-margin: 4px;
}

.folder-group :deep(.s-row) {
  border-radius: 0;
}

.folder-group :deep(.s-row-active) {
  background: var(--group-active-bg);
  color: var(--color-foreground);
}

.folder-group :deep(.s-row:not(.s-row-active):hover) {
  background: var(--group-hover-bg);
}
</style>
