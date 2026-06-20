<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BookmarkPreview from './BookmarkPreview.vue'
import BookmarkFavicon from './BookmarkFavicon.vue'
import BookmarkRowMenu from './BookmarkRowMenu.vue'
import { hostnameOf } from '@/lib/favicon-allowlist'
import { useScreenshotRefresh } from '@/composables/useScreenshotRefresh'
import { useStickyToolbar } from '@/composables/useStickyToolbar'
import { useBookmarkStore } from '@/stores/bookmark'
import type { PreviewHoverController } from '@/composables/useBookmarkPreviewHover'
import type { BookmarkJson } from '@/api/generated'

const props = defineProps<{ controller: PreviewHoverController }>()

// Actions taken on the footer are delegated up to BookmarkList, which owns the
// edit / move / delete dialogs. Refresh preview is handled in this component locally
const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
}>()

// Width is fixed; height derives from 16/9 + the action footer.
// 8px margins keep the popup off the edges of the content pane.
const POPUP_W = 340
const VIEWPORT_PAD = 8

const left = ref(0)
const top = ref(0)
const shown = ref(false)
const rootEl = ref<HTMLElement | null>(null)

const active = computed(() => props.controller.active.value)

// The sticky toolbar registers its own root element here (via CollectionView's
// provide); the popup clamps below it so it never covers the toolbar's links.
const stickyToolbar = useStickyToolbar()
// One-shot dev warning: the popup only ever renders under CollectionView, which
// always mounts the toolbar, this should warn if the 'anchor' is missing
let warnedMissingToolbar = false
function toolbarBottom(fallback: number): number {
  const el = stickyToolbar?.value ?? null
  if (el) return el.getBoundingClientRect().bottom
  if (import.meta.env.DEV && !warnedMissingToolbar) {
    warnedMissingToolbar = true
    console.warn(
      '[BookmarkPreviewPopup] sticky toolbar element not registered; the popup ' +
        'may overlap and block the toolbar (UC-093 BR-093-6). Is provideStickyToolbar ' +
        'mounted above both the toolbar and the list?',
    )
  }
  return fallback
}

// Bumped after a successful refresh so the preview <img> reloads from the
// server with a cache-busting query param.
const popupNonce = ref<number | undefined>(undefined)
const refreshScreenshot = useScreenshotRefresh()

async function onRefreshPreview(bookmark: BookmarkJson) {
  if (await refreshScreenshot(bookmark.id)) {
    popupNonce.value = Date.now()
  }
}

function onMenuOpenChange(open: boolean) {
  // Keep the popup pinned open while the footer dropdown is open so moving
  // from the trigger onto the (teleported) menu content doesn't dismiss it
  // mid-interaction (UC-093 A2). On close we only unpin — whether the popup
  // then hides is left to normal hover: closing via Escape/click-away or after
  // a Refresh keeps it up while the pointer is still over it. The hide for the
  // dialog-opening actions is scheduled explicitly in onEdit/onMove/onDelete.
  if (open) {
    props.controller.pin()
  } else {
    props.controller.unpin()
  }
}

// Edit/Move/Delete each open a dialog in BookmarkList that takes over the
// screen, so the popup should get out of the way. We unpin (the menu is
// closing) and schedule a hide; the brief grace lets radix finish its
// select→close before the popup unmounts.
function onEdit(bookmark: BookmarkJson) {
  emit('edit', bookmark)
  props.controller.unpin()
  props.controller.onPopupLeave()
}
function onMove(bookmark: BookmarkJson) {
  emit('move', bookmark)
  props.controller.unpin()
  props.controller.onPopupLeave()
}
function onDelete(bookmark: BookmarkJson) {
  emit('delete', bookmark)
  props.controller.unpin()
  props.controller.onPopupLeave()
}

const bookmarkStore = useBookmarkStore()

// The popup is a solid pointer-events:auto overlay, so it owns the click on its
// capture (the row's stretched open-link is covered by it). Open the bookmark
// explicitly to preserve "click the screenshot to open the page" — consistently
// across the whole capture, not just where it happens to overlap the row.
function onOpenBookmark() {
  const bookmark = active.value?.bookmark
  if (!bookmark) return
  window.open(bookmark.data.url, '_blank', 'noopener,noreferrer')
  bookmarkStore.trackClick(bookmark.id)
}

// `popupHeight` is read off the rendered element after the first paint
// (the preview frame is 16/9 plus the action footer). Until then we use a
// conservative estimate so the first clamp doesn't push the popup off-screen.
function currentHeight(): number {
  return rootEl.value?.offsetHeight ?? Math.ceil((POPUP_W * 9) / 16) + 44
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

  // The sticky list toolbar lives at the top of the scroll container and the
  // popup (taller than the row, centered on it) would otherwise overlap it —
  // and since the popup is pointer-events:auto, overlapping the toolbar would
  // block its links. Clamp the top below the toolbar's bottom edge so the two
  // never share screen space. (The batch action bar only shows during
  // selection, which disables the popup, so the toolbar alone is the bound.)
  // The toolbar element is shared via provide/inject (useStickyToolbar), not a
  // DOM query, so a rename can't silently reintroduce the overlap.
  const topBound = toolbarBottom(cb.top)

  // A right-aligned column, pinned to the right edge of the content pane.
  // When the centered list leaves a real right gutter the popup sits beside
  // the row; when it's cramped it overlaps the row's right side — but the
  // row's actions now also live in this popup's footer (UC-093), so the
  // overlap no longer hides them. It never flips left onto the fixed sidebar.
  const desiredLeft = cb.right - POPUP_W - VIEWPORT_PAD
  const minLeft = Math.max(VIEWPORT_PAD, cb.left + VIEWPORT_PAD)
  const maxLeft = Math.max(minLeft, Math.min(vw, cb.right) - POPUP_W - VIEWPORT_PAD)
  left.value = Math.round(Math.max(minLeft, Math.min(desiredLeft, maxLeft)))

  // Vertical: track the row centered, clamped to [below the toolbar, pane
  // bottom] so the popup never rides up under the toolbar or past the bottom.
  const desiredTop = r.top + (r.height - h) / 2
  const minTop = Math.max(VIEWPORT_PAD, topBound + VIEWPORT_PAD)
  const maxTop = Math.max(minTop, cb.bottom - h - VIEWPORT_PAD)
  top.value = Math.round(Math.max(minTop, Math.min(desiredTop, maxTop)))
}

// Re-measure on window resize so a layout shift doesn't strand the popup
// next to an outdated row position.
function onResize() { measure() }
window.addEventListener('resize', onResize)
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

watch(active, async (activeRow, previousRow) => {
  popupNonce.value = undefined

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

const host = computed(() => (active.value ? hostnameOf(active.value.bookmark.data.url) ?? '' : ''))
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
      @mouseenter="props.controller.onPopupEnter"
      @mouseleave="props.controller.onPopupLeave"
    >
      <!-- The capture is the popup's largest surface and is pointer-events:auto
           (the whole popup is a solid overlay). A click opens the bookmark
           (onOpenBookmark) consistently across the whole capture. The popup
           never overlaps the sticky toolbar (see measure()), so it never blocks
           toolbar links. -->
      <div class="cursor-pointer" @click="onOpenBookmark">
        <BookmarkPreview :bookmark="active.bookmark" variant="zoom" :nonce="popupNonce" />
      </div>
      <!-- Action footer (UC-093): the popup that already covers the row hosts
           the row's actions, so the covered ⋯ never needs to be reached. -->
      <div class="flex items-center gap-2 h-11 px-2.5 border-t border-border bg-card">
        <BookmarkFavicon
          :bookmark-id="active.bookmark.id"
          :url="active.bookmark.data.url"
          :size="16"
        />
        <!-- A real anchor, not a JS window.open, so middle-click, right-click →
             copy/open, and keyboard focus all work; trackClick records the open.
             (The capture's onOpenBookmark is the mouse-only twin for clicking
             the screenshot itself.) -->
        <a
          v-if="host"
          :href="active.bookmark.data.url"
          target="_blank"
          rel="noopener noreferrer"
          class="font-mono text-[11px] tracking-tight text-muted-foreground hover:text-foreground hover:underline truncate flex-1 min-w-0"
          data-testid="bookmark-preview-popup-link"
          @click="bookmarkStore.trackClick(active.bookmark.id)"
        >
          {{ host }}
        </a>
        <!-- spacer keeps actions hard-right even when host is empty -->
        <span v-else class="flex-1" />
        <div class="flex items-center gap-0.5 shrink-0">
          <BookmarkRowMenu
            :bookmark="active.bookmark"
            :show-refresh-preview="true"
            trigger-class="h-8 w-8 inline-flex items-center justify-center rounded-md hover:bg-primary hover:text-primary-foreground"
            @edit="onEdit"
            @move="onMove"
            @delete="onDelete"
            @refresh-preview="onRefreshPreview"
            @open-change="onMenuOpenChange"
          />
        </div>
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
  /* Solid overlay: the popup owns clicks on its capture (click → open the
   * bookmark via onOpenBookmark) and stays alive on hover across its whole
   * area. Because it captures the pointer, the rows beneath never fire
   * mouseenter while it's up, so the preview can't be hijacked by an adjacent
   * row as the pointer travels to the footer. It is clamped to never overlap
   * the sticky toolbar (see measure()), so it never blocks toolbar links. */
  pointer-events: auto;
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
