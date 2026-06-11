<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import { hostnameOf } from '@/lib/favicon-allowlist'
import { useCollectionStore } from '@/stores/collection'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BookmarkFavicon from './BookmarkFavicon.vue'

type Variant = 'grid' | 'list' | 'zoom'

const props = defineProps<{
  bookmark: BookmarkJson
  variant: Variant
  // Bumped by the parent on refresh to retrigger the image load.
  nonce?: number
  // UC-074 selected treatment: the capture shrinks inside its fixed frame
  // (inset shrink) — the frame itself never resizes, so no reflow.
  selected?: boolean
}>()

const { t } = useI18n()
const collectionStore = useCollectionStore()

// Three rendered states for the frame:
//   - 'loading': shimmer (the <img> is in flight or about to start)
//   - 'ok'     : the image loaded successfully
//   - 'fallback': the image failed / server returned 204
// Capturing is represented by 'loading'; there's no separate "Stale" state
// since the backend doesn't surface capture age in this iteration.
const status = ref<'loading' | 'ok' | 'fallback'>('loading')

const screenshotSrc = computed<string | null>(() => {
  const cid = collectionStore.currentCollectionId
  if (!cid) return null
  const base = `/api/collections/${encodeURIComponent(cid)}/bookmarks/${encodeURIComponent(props.bookmark.id)}/screenshot`
  // The nonce is only appended when present so a fresh card-mount doesn't
  // add a noisy query param to every screenshot URL the browser caches.
  return props.nonce ? `${base}?n=${props.nonce}` : base
})

// Re-arm the loading state whenever the URL changes (collection switch, or
// the parent bumping the nonce after a refresh). The <img> @load/@error
// will then transition us out of 'loading' again.
watch(screenshotSrc, () => { status.value = 'loading' })

const host = computed(() => hostnameOf(props.bookmark.data.url) ?? '')

// Stable hash → hue → oklch color, derived from hostname. Same domain always
// gets the same color so the fallback tile is recognizable, and oklch lets us
// pin lightness/chroma so the hue reads consistently across domains.
const fallbackHue = computed(() => {
  let h = 0
  for (const ch of host.value) h = (h * 31 + (ch.codePointAt(0) ?? 0)) >>> 0
  return h % 360
})

// The hue is kept only as a faint tint: chroma is dialed right down and the
// lightness is anchored near the surrounding surface so the tile reads as a
// quiet backdrop rather than a billboard that fights the favicon on top.
// `light-dark()` resolves off the `color-scheme` set in main.css, so the tile
// follows the theme toggle (very light tint in light mode, very dark in dark)
// with no store wiring.
const fallbackGradient = computed(() => {
  const hue = fallbackHue.value
  const inner = `light-dark(oklch(96% 0.02 ${hue}), oklch(26% 0.03 ${hue}))`
  const outer = `light-dark(oklch(90% 0.03 ${hue}), oklch(18% 0.02 ${hue}))`
  return `radial-gradient(circle at 50% 38%, ${inner} 0%, ${outer} 78%)`
})

// Variant tuning. Grid is the only variant with the floating favicon chip;
// list and zoom keep the favicon inline in the row title (or omit it for the
// zoom pop, where the row title sits directly above the popup anyway).
const showChip = computed(() => props.variant === 'grid')
const faviconSize = computed(() => (props.variant === 'zoom' ? 56 : 38))
const showDomain = computed(() => props.variant !== 'list')

function onImgLoad() { status.value = 'ok' }
function onImgError() { status.value = 'fallback' }
</script>

<template>
  <!-- Two-layer root: a transparent positioning wrapper so the favicon
       chip can sit at `bottom: -12px` without being clipped, and an inner
       frame that *does* have overflow:hidden so the image is properly
       cropped to its rounded 16:9 box. Width is intentionally not set on
       the wrapper — block-level fills the parent on grid/zoom, and the
       list variant gets a 124px width override from the parent (which
       class fallthrough would lose to a baked-in `w-full` here). -->
  <div class="relative" :data-testid="`bookmark-preview-${variant}`">
    <div
      class="relative aspect-[16/9] overflow-hidden"
      :class="[
        variant === 'list' ? 'rounded-md border' : 'rounded-none',
        variant === 'list'
          ? (selected
            ? 'border-primary shadow-[0_0_0_1px_color-mix(in_oklab,var(--color-primary)_53%,transparent)]'
            : 'border-border')
          : '',
      ]"
    >
      <!-- Placeholder backdrop. Sits behind every state so the frame never
           flashes the card background even while the <img> swaps. It also
           shows through the gap when the selected capture insets, so it
           follows the theme via light-dark() (resolved off `color-scheme`,
           like the fallback tile). The light value carries a slight primary
           tint and extra depth so the gap reads as selection next to white
           captures (in dark the near-black token contrasts on its own). -->
      <div
        class="absolute inset-0 bg-[light-dark(color-mix(in_oklab,var(--color-primary)_9%,#dde2e9),#0e1014)]"
        aria-hidden="true"
      />

      <!-- Capture wrapper: animates the inset shrink when selected. -->
      <div
        class="capture-wrap"
        :class="[`capture-wrap--${variant}`, { 'is-inset': selected }]"
      >
        <!-- The capture itself. Always rendered (when we have a URL) so the
             browser can begin fetching immediately; it's just invisible
             until @load fires. Hidden again on error so the fallback tile
             shows through. -->
        <img
          v-if="screenshotSrc"
          :key="screenshotSrc"
          :src="screenshotSrc"
          :alt="t('bookmark.previewAlt', { title: bookmark.data.title })"
          loading="lazy"
          decoding="async"
          class="absolute inset-0 h-full w-full object-cover object-top transition-opacity duration-150"
          :class="status === 'ok' ? 'opacity-100' : 'opacity-0'"
          @load="onImgLoad"
          @error="onImgError"
        />

        <!-- Fallback tile: radial gradient + favicon + monospace domain.
             Only renders once we know the capture failed, so a slow-loading
             image never shows this state by accident. -->
        <div
          v-if="status === 'fallback'"
          class="absolute inset-0 flex flex-col items-center justify-center gap-2 px-3 text-center"
          :style="{ background: fallbackGradient }"
          aria-hidden="true"
        >
          <BookmarkFavicon
            :bookmark-id="bookmark.id"
            :url="bookmark.data.url"
            :size="faviconSize"
          />
          <span
            v-if="showDomain && host"
            class="font-mono text-[11px] tracking-tight text-muted-foreground truncate max-w-full"
          >
            {{ host }}
          </span>
        </div>

        <!-- Shimmer overlay while loading. The keyframes are inline so this
             component is self-contained — no global stylesheet edits. -->
        <div
          v-if="status === 'loading'"
          class="absolute inset-0 preview-shimmer"
          aria-hidden="true"
        />
      </div>
    </div>

    <!-- Favicon chip overlay (grid only). Lives *outside* the clipped
         frame so its `bottom: -12px` overhang isn't trimmed. The
         consuming card must add padding-top on the body to clear it. -->
    <div
      v-if="showChip"
      class="absolute left-[10px] bottom-[-12px] rounded-lg bg-card p-1 shadow-[0_2px_8px_rgba(0,0,0,0.4)] pointer-events-none"
    >
      <BookmarkFavicon
        :bookmark-id="bookmark.id"
        :url="bookmark.data.url"
        :size="22"
      />
    </div>
  </div>
</template>

<style scoped>
.capture-wrap {
  position: absolute;
  inset: 0;
  overflow: hidden;
  transition: inset 0.15s cubic-bezier(0.2, 0.7, 0.3, 1), border-radius 0.15s;
}

/* Hairline edge on the inset capture: light screenshots would otherwise
   melt into the light-theme gap (in dark theme the near-black gap provides
   the contrast by itself, so the edge stays barely-there). */
.capture-wrap.is-inset {
  box-shadow: 0 0 0 1px light-dark(rgba(15, 23, 42, 0.14), rgba(255, 255, 255, 0.07));
}

/* Resting corner radii are concentric with each clipping container:
   inner radius = outer radius − container border − the 1px inset.
   Grid cover sits in the card (rounded-lg 8px, 1px border → 6px, top
   corners only — the cover's bottom edge is mid-card). List thumb has its
   own rounded-md 6px / 1px-border frame (→ 4px, all corners). The zoom
   popup clips at 10px with no border (→ 9px, top corners). */
.capture-wrap--grid {
  border-radius: 6px 6px 0 0;
}

.capture-wrap--list {
  border-radius: 4px;
}

.capture-wrap--zoom {
  border-radius: 9px 9px 0 0;
}

.capture-wrap--grid.is-inset {
  inset: 10px 10px 4px;
  border-radius: 6px;
}

.capture-wrap--list.is-inset {
  inset: 5px;
  border-radius: 4px;
}

@media (prefers-reduced-motion: reduce) {
  .capture-wrap {
    transition: none;
  }
}

.preview-shimmer {
  background: linear-gradient(
    100deg,
    rgba(255, 255, 255, 0) 30%,
    rgba(255, 255, 255, 0.06) 50%,
    rgba(255, 255, 255, 0) 70%
  );
  background-size: 200% 100%;
  animation: preview-shimmer-slide 1.4s ease-in-out infinite;
}

@keyframes preview-shimmer-slide {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
