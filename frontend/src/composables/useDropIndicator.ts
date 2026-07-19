import { ref, watch } from 'vue'
import type { Placement } from '@/api/generated'
import { isDraggingBookmark, isDraggingFolder } from '@/composables/useDragState'

// Module-level state for the drop indicator (UC-102). The folder tree renders
// recursively, so the rows and gap strips of different levels live in different
// component instances — sharing the active target here guarantees exactly one
// indicator is visible at a time and lets hysteresis work across levels.

/** A drop into the gap between two rows: insert relative to an anchor sibling. */
export interface GapDropTarget {
  kind: 'gap'
  /** Unique per strip; used to match the active target to its strip. */
  key: string
  parentId: string | undefined
  anchorFolderId: string
  placement: Placement
  /**
   * Viewport y of the strip's center, set when it claims the indicator.
   * Hysteresis keeps the gap only while the pointer stays near this — a fast
   * drag away must release it immediately instead of sticking rows behind
   * the cursor.
   */
  centerY?: number
}

interface NestDropTarget {
  kind: 'nest'
  folderId: string
}

export type DropTarget = GapDropTarget | NestDropTarget

export const activeDropTarget = ref<DropTarget | null>(null)

export function setDropTarget(target: DropTarget | null) {
  // Hovering a target re-claims it on every dragover (~60/s). Skipping the
  // no-op writes keeps the tree from re-rendering while nothing changed —
  // main-thread headroom the indicator needs to track a fast drag.
  const current = activeDropTarget.value
  if (target?.kind === 'gap' && current?.kind === 'gap' && current.key === target.key) return
  if (
    target?.kind === 'nest'
    && current?.kind === 'nest'
    && current.folderId === target.folderId
  ) {
    return
  }
  activeDropTarget.value = target
}

// ── Spring-loaded folders ───────────────────────────────────────────────────
// Hovering a collapsed folder during a drag for the dwell time auto-expands it.

const SPRING_DWELL_MS = 700
export const armingFolderId = ref<string | null>(null)
let springTimer: ReturnType<typeof setTimeout> | undefined

export function armSpring(folderId: string, expand: () => void) {
  if (armingFolderId.value === folderId) return
  cancelSpring()
  armingFolderId.value = folderId
  springTimer = setTimeout(() => {
    armingFolderId.value = null
    expand()
  }, SPRING_DWELL_MS)
}

/** Without an argument cancels unconditionally; with one, only if that folder is arming. */
export function cancelSpring(folderId?: string) {
  if (folderId !== undefined && armingFolderId.value !== folderId) return
  clearTimeout(springTimer)
  armingFolderId.value = null
}

// ── Landing feedback ────────────────────────────────────────────────────────
// The row that received a successful drop plays a brief settle animation.

const LANDED_MS = 450
export const landedFolderId = ref<string | null>(null)
let landedTimer: ReturnType<typeof setTimeout> | undefined

export function markLanded(folderId: string) {
  clearTimeout(landedTimer)
  landedFolderId.value = folderId
  navigator.vibrate?.(14)
  landedTimer = setTimeout(() => {
    landedFolderId.value = null
  }, LANDED_MS)
}

// Clear transient state when a drag ends anywhere (drop elsewhere or Esc).
watch([isDraggingFolder, isDraggingBookmark], ([folder, bookmark]) => {
  if (!folder && !bookmark) {
    activeDropTarget.value = null
    cancelSpring()
  }
})
