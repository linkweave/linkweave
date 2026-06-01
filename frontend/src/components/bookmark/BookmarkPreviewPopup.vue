<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BookmarkPreview from './BookmarkPreview.vue'
import type { PreviewHoverController } from '@/composables/useBookmarkPreviewHover'

const props = defineProps<{ controller: PreviewHoverController }>()

// Width is fixed; height derives from 16/9 + the caption.
// 8px margins keep the popup off the edges of the content pane.
const POPUP_W = 340
const VIEWPORT_PAD = 8

const left = ref(0)
const top = ref(0)
const shown = ref(false)
const rootEl = ref<HTMLElement | null>(null)

const active = computed(() => props.controller.active.value)

// `popupHeight` is read off the rendered element after the first paint
// (the preview frame is 16/9 plus the URL caption). Until then we use a
// conservative estimate so the first clamp doesn't push the popup off-screen.
function currentHeight(): number {
  return rootEl.value?.offsetHeight ?? Math.ceil((POPUP_W * 9) / 16) + 22
}

// The popup is teleported to <body>, so it must be clamped to the *content
// area* (the scrollable pane the rows live in), not the raw viewport —
// otherwise a cramped layout shoves it left onto the fixed sidebar. We find
// the row's nearest scroll container and use its rect as the bounds.
function contentBounds(row: HTMLElement): { left: number; right: number; top: number; bottom: number } {
  let el: HTMLElement | null = row.parentElement
  while (el && el !== document.body && el !== document.documentElement) {
    const oy = getComputedStyle(el).overflowY
    if (oy === 'auto' || oy === 'scroll') {
      const b = el.getBoundingClientRect()
      return { left: b.left, right: b.right, top: b.top, bottom: b.bottom }
    }
    el = el.parentElement
  }
  return { left: 0, right: window.innerWidth, top: 0, bottom: window.innerHeight }
}

function measure() {
  const a = active.value
  if (!a) return
  const r = a.row.getBoundingClientRect()
  const cb = contentBounds(a.row)
  const vw = window.innerWidth
  const h = currentHeight()

  // A right-aligned column, pinned to the right edge of the content pane.
  // When the centered list leaves a real right gutter the popup sits beside
  // the row; when it's cramped it overlaps the row's *right* side (menu /
  // stats — the least important cells), which is the chosen trade-off. It
  // never flips left onto the fixed sidebar. pointer-events:none keeps the
  // row clickable underneath.
  const desiredLeft = cb.right - POPUP_W - VIEWPORT_PAD
  const minLeft = Math.max(VIEWPORT_PAD, cb.left + VIEWPORT_PAD)
  const maxLeft = Math.max(minLeft, Math.min(vw, cb.right) - POPUP_W - VIEWPORT_PAD)
  left.value = Math.round(Math.max(minLeft, Math.min(desiredLeft, maxLeft)))

  // Vertical: track the row centered, clamped within the content area so the
  // popup never rides up under the sticky header or past the pane bottom.
  const desiredTop = r.top + (r.height - h) / 2
  const minTop = Math.max(VIEWPORT_PAD, cb.top + VIEWPORT_PAD)
  const maxTop = Math.max(minTop, cb.bottom - h - VIEWPORT_PAD)
  top.value = Math.round(Math.max(minTop, Math.min(desiredTop, maxTop)))
}

// Re-measure on window resize so a layout shift doesn't strand the popup
// next to an outdated row position.
function onResize() { measure() }
window.addEventListener('resize', onResize)
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

watch(active, async (activeRow, previousRow) => {
  if (!activeRow) {
    // No row is active anymore — hide the popup.
    shown.value = false
    return
  }

  measure()

  if (previousRow) {
    // Warm switch (moving between rows): the popup is already on screen, so
    // the CSS `transition: top` carries it to the new row.
    shown.value = true
    return
  }

  // Cold start (popup was hidden): paint in the un-shown state for one frame
  // so the opacity/scale transition has somewhere to start from.
  shown.value = false
  await nextTick()
  // Two rAFs: first to flush layout, second to ensure the browser observed
  // the initial state before we flip to `shown` and the entrance plays.
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      shown.value = true
      // Re-measure now that the rendered height is known.
      measure()
    }),
  )
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="active"
      ref="rootEl"
      class="zoom-pop bg-card border border-border"
      :class="shown ? 'shown' : ''"
      :style="{ left: `${left}px`, top: `${top}px`, width: `${POPUP_W}px` }"
      data-testid="bookmark-preview-popup"
      aria-hidden="true"
    >
      <BookmarkPreview :bookmark="active.bookmark" variant="zoom" />
      <div class="url-caption font-mono bg-card text-muted-foreground border-t border-border">
        {{ active.bookmark.data.url }}
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* Appearance (bg-card, border-border) is on Tailwind utilities in the
 * template so the theme tokens resolve even though the popup is teleported to
 * <body>. The scoped rules handle only placement, clipping, and animation. */
.zoom-pop {
  position: fixed;
  z-index: 50;
  pointer-events: none;
  opacity: 0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.55);
  /* Right-aligned column: grows out of its right edge and slides in from the
   * right, so it reads as emerging from the pane's right gutter. */
  transform-origin: right center;
  transform: translateX(8px) scale(0.96);
  transition:
    opacity 0.15s,
    transform 0.15s,
    top 0.18s cubic-bezier(0.2, 0.7, 0.3, 1),
    left 0.18s cubic-bezier(0.2, 0.7, 0.3, 1);
}

.zoom-pop.shown {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.url-caption {
  font-size: 11px;
  line-height: 1.2;
  padding: 6px 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (prefers-reduced-motion: reduce) {
  .zoom-pop {
    transition: opacity 0.15s, top 0.18s, left 0.18s;
    transform: none;
  }
  .zoom-pop.shown {
    transform: none;
  }
}
</style>
