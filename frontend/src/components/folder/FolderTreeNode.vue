<script setup lang="ts">
import { requireValue } from '@/lib/nullish.ts'
import { Folder, FolderOpen, ChevronRight, MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { DropdownMenuContentLw, DropdownMenuItemLw } from '@/components/ui'
import type { FolderJson, FolderPositionJson } from '@/api/generated'
import { Placement } from '@/api/generated'
import { computed, reactive } from 'vue'
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
import {
  activeDropTarget,
  armingFolderId,
  armSpring,
  cancelSpring,
  landedFolderId,
  markLanded,
  setDropTarget,
  type GapDropTarget,
} from '@/composables/useDropIndicator'
import { useDndMove } from '@/composables/useDndMove'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { setCompactDragImage } from '@/lib/dragImage'

const folderStore = useFolderStore()
const isTouch = useMediaQuery('(hover: none) and (pointer: coarse)')
const { moveBookmarkWithUndo, moveFolderWithUndo } = useDndMove()

interface FolderNode {
  folder: FolderJson
  children: FolderNode[]
}

const props = withDefaults(defineProps<{
  nodes: FolderNode[]
  depth?: number
}>(), { depth: 0 })

const emit = defineEmits<{
  createSubfolder: [parentId: string]
  rename: [folder: FolderJson]
  delete: [folder: FolderJson]
}>()

const expanded = reactive<Record<string, boolean>>({})

// Drop model (UC-102): the gap strips between rows own reorder (insertion line
// with an anchor dot), the row body owns nest (filled tint). Bookmarks only
// ever nest — the strips ignore them. The active target lives in
// useDropIndicator so it is shared across the recursive tree levels.

// An already-chosen gap keeps priority over the row body while the pointer
// stays within this distance of the gap's center (hysteresis against flicker
// at the strip↔row boundary). Scoped to the gap's position on purpose: a fast
// drag that skips past must release the indicator immediately. The band
// reaches ~6px into the adjacent rows — just past the strip's own overlap.
// Tied to the .dnd-gap geometry: the strip's center sits 1px off each row
// edge, so this is the 6px reach plus that 1px.
const GAP_STICKY_PX = 7

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

// ── Drag source (folder being dragged) ──────────────────────────────────────

function onFolderDragStart(event: DragEvent, folder: FolderJson) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_FOLDER, folder.id)
  setCompactDragImage(event, folder.data.name, 'folder')
  setDraggingFolderId(folder.id)
  event.stopPropagation()
}

function onFolderDragEnd() {
  setDraggingFolderId(null)
}

// ── Gap strips (reorder) ────────────────────────────────────────────────────

function gapBefore(node: FolderNode): GapDropTarget {
  return {
    kind: 'gap',
    key: `before-${node.folder.id}`,
    parentId: node.folder.data.parentId,
    anchorFolderId: node.folder.id,
    placement: Placement.Before,
  }
}

// One trailing strip after the last root row ("insert at the very end");
// deeper levels don't need one — nesting into the parent already appends.
const trailingGap = computed<GapDropTarget | null>(() => {
  const last = props.nodes[props.nodes.length - 1]
  if (props.depth !== 0 || !last) return null
  return {
    kind: 'gap',
    key: 'end',
    parentId: undefined,
    anchorFolderId: last.folder.id,
    placement: Placement.After,
  }
})

// ── Level-scoped indicator state ────────────────────────────────────────────
// Each recursive level is its own component instance, and a drag updates the
// module-level indicator refs ~60/s. Reading those refs directly in the
// template would re-render EVERY level on every indicator move. These
// computeds map the global state to "what applies to THIS level" — their
// values only change when this level is affected, so (per computed stability)
// untouched levels never re-render during a drag.

const levelIds = computed(() => new Set(props.nodes.map(n => n.folder.id)))

function scopedToLevel(source: () => string | null) {
  return computed<string | null>(() => {
    const id = source()
    return id !== null && levelIds.value.has(id) ? id : null
  })
}

const nestTargetId = scopedToLevel(() => {
  const t = activeDropTarget.value
  return t?.kind === 'nest' ? t.folderId : null
})
const dimmedFolderId = scopedToLevel(() => draggingFolderId.value)
const armingInLevel = scopedToLevel(() => armingFolderId.value)
const landedInLevel = scopedToLevel(() => landedFolderId.value)

/** The active gap key if the strip lives in this level, else null. */
const activeGapKey = computed<string | null>(() => {
  const t = activeDropTarget.value
  if (t?.kind !== 'gap') return null
  if (t.key === 'end') return props.depth === 0 ? 'end' : null
  return levelIds.value.has(t.key.slice('before-'.length)) ? t.key : null
})

// The anchor dot sits on the folder-icon column of the level the drop inserts
// at — that x-position is what disambiguates the target indent.
const gapLineLeft = computed(() => `${requireValue(props.depth) * 16 + 30}px`)

function isGapDropAllowed(draggingId: string, gap: GapDropTarget): boolean {
  // Anchor == dragged folder means "next to itself": a no-op position, and the
  // backend rejects self-anchors — never offer it.
  if (gap.anchorFolderId === draggingId) return false
  if (gap.parentId && isDescendant(draggingId, gap.parentId)) return false
  return !isSamePosition(draggingId, {
    parentId: gap.parentId,
    position: { anchorFolderId: gap.anchorFolderId, placement: gap.placement },
  })
}

function onGapDragOver(event: DragEvent, gap: GapDropTarget) {
  if (!isDraggingFolder.value) return
  const draggingId = getDraggingFolderId()
  if (!draggingId || !isGapDropAllowed(draggingId, gap)) return
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  cancelSpring()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  setDropTarget({ ...gap, centerY: rect.top + rect.height / 2 })
}

async function onGapDrop(event: DragEvent, gap: GapDropTarget) {
  event.preventDefault()
  event.stopPropagation()
  setDropTarget(null)
  cancelSpring()
  const types = event.dataTransfer?.types ?? []
  if (!types.includes(DRAG_TYPE_FOLDER)) return
  const folderId = event.dataTransfer!.getData(DRAG_TYPE_FOLDER)
  if (!folderId || !isGapDropAllowed(folderId, gap)) return
  await commitGapDrop(folderId, gap)
}

async function commitGapDrop(folderId: string, gap: GapDropTarget) {
  const position: FolderPositionJson = {
    anchorFolderId: gap.anchorFolderId,
    placement: gap.placement,
  }
  if (await moveFolderWithUndo(folderId, gap.parentId, position)) markLanded(folderId)
}

// ── Row (nest) ──────────────────────────────────────────────────────────────

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
    return !isDescendant(draggingId, targetFolderId)
  }
  return false
}

function isNestTarget(folderId: string): boolean {
  return activeDropTarget.value?.kind === 'nest' && activeDropTarget.value.folderId === folderId
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

function onDragOver(event: DragEvent, node: FolderNode) {
  if (!isDragAccepted(event, node.folder.id)) return
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  // Hysteresis: a gap indicator the pointer is still hovering next to doesn't
  // get stolen by the row edge — the drop still routes to the gap via
  // activeDropTarget. Gaps elsewhere release instantly.
  if (
    isDraggingFolder.value
    && activeDropTarget.value?.kind === 'gap'
    && Math.abs(event.clientY - (activeDropTarget.value.centerY ?? Infinity)) <= GAP_STICKY_PX
  ) {
    return
  }
  setDropTarget({ kind: 'nest', folderId: node.folder.id })
  if (node.children.length > 0 && !isExpanded(node.folder.id)) {
    armSpring(node.folder.id, () => {
      expanded[node.folder.id] = true
    })
  } else {
    cancelSpring()
  }
}

function onDragLeave(event: DragEvent, folder: FolderJson) {
  // Only react when leaving the element entirely, not entering a child
  const related = event.relatedTarget as Node | null
  const el = event.currentTarget as HTMLElement
  if (related && el.contains(related)) return
  if (isNestTarget(folder.id)) setDropTarget(null)
  cancelSpring(folder.id)
}

async function onDrop(event: DragEvent, targetNode: FolderNode) {
  event.preventDefault()
  event.stopPropagation()
  const target = activeDropTarget.value
  setDropTarget(null)
  cancelSpring()

  const types = event.dataTransfer?.types ?? []
  const targetFolder = targetNode.folder

  if (types.includes(DRAG_TYPE_BOOKMARK)) {
    const bookmarkId = event.dataTransfer!.getData(DRAG_TYPE_BOOKMARK)
    if (bookmarkId && await moveBookmarkWithUndo(bookmarkId, targetFolder.id)) {
      markLanded(targetFolder.id)
    }
    return
  }

  if (types.includes(DRAG_TYPE_FOLDER)) {
    const folderId = event.dataTransfer!.getData(DRAG_TYPE_FOLDER)
    if (!folderId) return
    // Hysteresis can keep a gap active while the pointer sits on a row edge —
    // the indicator is the promise, so the drop follows it.
    if (target?.kind === 'gap') {
      if (isGapDropAllowed(folderId, target)) await commitGapDrop(folderId, target)
      return
    }
    if (folderId === targetFolder.id) return
    if (isDescendant(folderId, targetFolder.id)) return
    if (await moveFolderWithUndo(folderId, targetFolder.id)) markLanded(folderId)
  }
}
</script>

<template>
  <ul :class="{ 'dnd-live': isDraggingFolder || isDraggingBookmark }">
    <template v-for="node in nodes" :key="node.folder.id">
      <li
        class="dnd-gap"
        :class="{ 'dnd-gap-live': isDraggingFolder }"
        :data-testid="`folder-gap-before-${node.folder.id}`"
        @dragenter="onGapDragOver($event, gapBefore(node))"
        @dragover="onGapDragOver($event, gapBefore(node))"
        @drop="onGapDrop($event, gapBefore(node))"
      >
        <div
          v-if="activeGapKey === `before-${node.folder.id}`"
          class="drop-line"
          :style="{ left: gapLineLeft, right: '8px' }"
        >
          <span class="dot" />
        </div>
      </li>
      <li :class="isGrouped(node) ? 'folder-group' : ''">
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
              {
                'nest-target': nestTargetId === node.folder.id,
                'is-dragging': dimmedFolderId === node.folder.id,
                'arming': armingInLevel === node.folder.id,
                'landed': landedInLevel === node.folder.id,
              },
            ]"
            :style="{ paddingLeft: `${requireValue(depth) * 16 + 8}px` }"
            @click="folderStore.selectFolder(node.folder.id)"
            @dragstart="onFolderDragStart($event, node.folder)"
            @dragend="onFolderDragEnd"
            @dragenter="onDragOver($event, node)"
            @dragover="onDragOver($event, node)"
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
            <span
              v-if="armingInLevel === node.folder.id"
              class="spring-bar"
              aria-hidden="true"
            />
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
    </template>
    <li
      v-if="trailingGap"
      class="dnd-gap"
      :class="{ 'dnd-gap-live': isDraggingFolder }"
      data-testid="folder-gap-end"
      @dragenter="onGapDragOver($event, trailingGap)"
      @dragover="onGapDragOver($event, trailingGap)"
      @drop="onGapDrop($event, trailingGap)"
    >
      <div
        v-if="activeGapKey === 'end'"
        class="drop-line"
        :style="{ left: gapLineLeft, right: '8px' }"
      >
        <span class="dot" />
      </div>
    </li>
  </ul>
</template>

<style scoped>
.s-row {
  position: relative;
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
