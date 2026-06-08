<script setup lang="ts">
import type { BookmarkJson, PropertyDefinitionJson } from '@/api/generated'
import BookmarkFavicon from '@/components/bookmark/BookmarkFavicon.vue'
import BookmarkPreview from '@/components/bookmark/BookmarkPreview.vue'
import { DropdownMenuContentCl, DropdownMenuItemCl } from '@/components/ui'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmark } from '@/composables/useDragState'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useShowPropertyBadges, useShowPreviewPopup } from '@/composables/usePropertyDisplayPrefs'
import { useRelativeTime } from '@/composables/useRelativeTime'
import { decodePropertyValue } from '@/lib/propertyValueMapper'
import { matchesPropertyToken, parsePropertyValue } from '@/lib/searchQueryProperty'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { usePropertyStore } from '@/stores/property'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { useTagStore } from '@/stores/tag'
import { useUiStore } from '@/stores/ui'
import { Clock, ExternalLink, Folder, MoreHorizontal, MousePointerClick } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BookmarkPropertyBadge from './BookmarkPropertyBadge.vue'
import { useBookmarkPreviewHover } from '@/composables/useBookmarkPreviewHover'

const { t } = useI18n()
const tagStore = useTagStore()
const folderStore = useFolderStore()
const bookmarkStore = useBookmarkStore()
const searchQueryStore = useSearchQueryStore()
const propertyStore = usePropertyStore()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()
const ui = useUiStore()
const isTouch = useMediaQuery('(hover: none) and (pointer: coarse)')
const { formatRelativeTime } = useRelativeTime()
const showPropertyBadges = useShowPropertyBadges()
const showPreviewPopup = useShowPreviewPopup()

const props = defineProps<{
  bookmark: BookmarkJson
  // 'list' draws the leading thumbnail + stats; 'grid' draws the cover-image
  // card. Other layouts (grouped) use their own row component.
  layout: 'grid' | 'list'
  showStats?: boolean
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

// Previews compose the global toolbar toggle with the per-collection
// `screenshotEnabled` flag (which also gates server-side capture). When the
// collection has previews disabled there's no captured image to show, so we
// also skip the frame entirely — same DOM as before previews existed.
const previewsVisible = computed(
  () => ui.previewsEnabled && (collectionStore.collectionInfo?.screenshotEnabled ?? false),
)

// Bumped on a successful refresh so the preview component reloads from the
// server with a cache-busting query param.
const previewNonce = ref(0)

function getTagById(tagId: string) {
  return tagStore.tags.find((t) => t.id === tagId)
}

let didDrag = false

function onBookmarkDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  didDrag = true
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
  setDraggingBookmark(true)
}

function onBookmarkDragEnd() {
  setDraggingBookmark(false)
  // Reset after the synthetic click that fires when a drag ends without a drop
  setTimeout(() => {
    didDrag = false
  }, 0)
}

function onCardLinkClick(event: MouseEvent) {
  if (didDrag) {
    event.preventDefault()
    return
  }
  bookmarkStore.trackClick(props.bookmark.id)
}

function getFolderName(): string | null {
  const folderId = props.bookmark.data.folderId
  if (!folderId) return null
  const folder = folderStore.folders.find((f) => f.id === folderId)
  return folder?.data.name ?? null
}

// Note: we don't render an "excluded" state here. A bookmark tagged `#draft`
// is filtered out by `-#draft`, so a card chip never reaches that state. The
// excluded style lives in `FilterPill` (the strip), where it is needed.
function tagClass(tagId: string): string {
  const tag = getTagById(tagId)
  if (!tag) return ''
  if (searchQueryStore.isTagActive(tag.data.name)) {
    return 'bg-[color-mix(in_oklab,var(--tag-color)_22%,var(--color-secondary))] border-[var(--tag-color)]'
  }
  return ''
}

function tagTitle(tagId: string): string {
  const tag = getTagById(tagId)
  const name = tag?.data.name ?? ''
  if (searchQueryStore.isTagActive(name)) return `Remove filter: #${name}`
  return `Filter by tag: #${name} (⌥/⇧+click to exclude)`
}

function onTagClick(event: MouseEvent, tagId: string) {
  event.preventDefault()
  event.stopPropagation()
  const tag = getTagById(tagId)
  if (!tag) return
  const modifier = event.altKey || event.shiftKey ? 'exclude' : undefined
  searchQueryStore.toggleQueryToken({ kind: 'tag', value: tag.data.name, neg: false }, modifier)
}

// --- Property badges ------------------------------------------------------
// Definitions that have a value on this bookmark, in store order. We look up
// the wire entry by definitionId once (not per render) so the badge component
// can stay a pure presentational thing.
type VisibleProp = {
  def: PropertyDefinitionJson
  value: { definitionId: string; valueText?: string; valueNumber?: number; valueBoolean?: boolean }
}

function isEmptyDecoded(v: ReturnType<typeof decodePropertyValue>): boolean {
  if (v === undefined || v === '') return true
  if (Array.isArray(v) && v.length === 0) return true
  return false
}

const visibleProps = computed<VisibleProp[]>(() => {
  const wireById = new Map((props.bookmark.propertyValues ?? []).map((v) => [v.definitionId, v]))
  const out: VisibleProp[] = []
  for (const def of propertyStore.definitions) {
    const wire = wireById.get(def.id)
    if (!wire) continue
    if (isEmptyDecoded(decodePropertyValue(def.data.type, wire))) continue
    out.push({ def, value: wire })
  }
  return out
})

// A badge lights up purple whenever any positive `property:` token in the
// query actually matches this bookmark's value for that definition — covers
// `=`, `>`, `<`, `>=`, `<=`. Delegates to the same matcher the bookmark filter
// uses, so the visual state can never drift from the filter logic.
const propertyDefsByName = computed(
  () =>
    new Map(
      propertyStore.definitions.map((d) => [
        d.data.name.toLowerCase(),
        { id: d.id, type: d.data.type },
      ]),
    ),
)

function isPropertyTokenActive(name: string): boolean {
  const lower = name.toLowerCase()
  return searchQueryStore.queryTokens.some((t) => {
    if (t.kind !== 'operator' || t.key !== 'property' || t.neg) return false
    const parsed = parsePropertyValue(t.value)
    if (!parsed || parsed.key !== lower) return false
    return matchesPropertyToken(
      props.bookmark.propertyValues,
      propertyDefsByName.value,
      parsed,
    )
  })
}

function badgeValueAsTokenString(
  propDef: PropertyDefinitionJson,
  value: VisibleProp['value'],
): string {
  const decoded = decodePropertyValue(propDef.data.type, value)
  if (decoded === undefined) return ''
  if (Array.isArray(decoded)) return decoded.join(',')
  return String(decoded)
}

function onPropertyBadgeClick(propDef: PropertyDefinitionJson, value: VisibleProp['value']) {
  const tokenValue = `${propDef.data.name}=${badgeValueAsTokenString(propDef, value)}`
  searchQueryStore.toggleQueryToken({
    kind: 'operator',
    key: 'property',
    value: tokenValue,
    neg: false,
  })
}

function onToggleFolderFilter(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  const folderId = props.bookmark.data.folderId
  if (!folderId) return
  // Card "in folder X" navigates the sidebar — uses the hierarchical `under:`
  // operator (matches this folder + descendants) so the click and the sidebar
  // selection mean the same thing. `selectFolder` enforces exclusivity (only
  // one `under:` token at a time) and replaces any existing folder selection.
  const folderStore = useFolderStore()
  if (folderStore.selectedFolderId === folderId) {
    folderStore.selectFolder(null)
  } else {
    folderStore.selectFolder(folderId)
  }
}

async function refreshPreview() {
  const cid = collectionStore.currentCollectionId
  if (!cid) return
  try {
    const response = await fetch(
      `/api/collections/${encodeURIComponent(cid)}/bookmarks/${encodeURIComponent(props.bookmark.id)}/screenshot/refresh`,
      { method: 'POST', credentials: 'include' },
    )
    if (!response.ok) throw new Error(`Refresh failed: ${response.status}`)
    previewNonce.value = Date.now()
  } catch (err) {
    void notification.handleApiError(err, t('bookmark.refreshPreviewError'))
  }
}

// Lazy mount of the radix DropdownMenu. With N cards on screen, mounting an
// open-able menu per card (Portal context, popper, listeners) is the dominant
// cost of rendering the bookmark list. Most cards never have their menu
// opened, so we render a plain trigger button until first click and only then
// swap in the full radix tree (auto-opened so the user sees the menu on that
// very first click, just one tick later).
const menuActivated = ref(false)

// Hover-intent driver for the shared preview popup. The controller is
// provided by BookmarkList; when the bookmark list isn't the parent (e.g.
// the grouped layout uses its own row component) inject returns null and
// the handlers below quietly no-op.
const previewHover = useBookmarkPreviewHover()
const rowEl = ref<HTMLElement | null>(null)

// Gate the wiring: previews must be on, layout must be the list (the only
// layout that benefits from a zoom — grid already shows a large cover),
// the device must have real hover, and the bookmark's preview must not be
// in a failed/capturing state we don't want to enlarge into a broken image.
const hoverPreviewActive = computed(
  () =>
    previewsVisible.value &&
    props.layout === 'list' &&
    !isTouch.value &&
    !!previewHover &&
    showPreviewPopup.value,
)

function onRowEnter() {
  if (!hoverPreviewActive.value || !rowEl.value) return
  previewHover!.onRowEnter(props.bookmark, rowEl.value)
}

function onRowLeave() {
  if (!hoverPreviewActive.value) return
  previewHover!.onRowLeave()
}
</script>

<template>
  <div
    ref="rowEl"
    :draggable="!isTouch"
    :data-testid="`bookmark-card-${props.bookmark.id}`"
    :data-bookmark-title="props.bookmark.data.title"
    :data-layout="props.layout"
    class="group relative rounded-lg border border-border bg-card hover:ring-2 hover:ring-primary/50 hover:border-primary/30 focus-within:ring-2 focus-within:ring-primary transition-[box-shadow,border-color,color] duration-150 text-muted-foreground hover:text-accent-foreground cursor-grab active:cursor-grabbing"
    :class="props.layout === 'grid' ? 'overflow-hidden' : ''"
    @dragstart="onBookmarkDragStart"
    @dragend="onBookmarkDragEnd"
    @mouseenter="onRowEnter"
    @mouseleave="onRowLeave"
  >
    <!-- Stretched link: covers the entire card so any click on the card body opens the URL.
         Sibling (not parent) of interactive children to keep HTML valid and Cmd-click working. -->
    <a
      :href="props.bookmark.data.url"
      target="_blank"
      rel="noopener noreferrer"
      :aria-label="props.bookmark.data.title"
      draggable="false"
      class="absolute inset-0 rounded-[inherit] z-0 outline-none"
      @click="onCardLinkClick"
    />

    <!-- Grid mode preview: 16:9 cover at the top with a favicon chip
         overlapping the bottom edge. The body wrapper below adds extra
         padding-top to clear the −12px overhang. -->
    <BookmarkPreview
      v-if="previewsVisible && props.layout === 'grid'"
      :bookmark="props.bookmark"
      variant="grid"
      :nonce="previewNonce"
      class="block pointer-events-none"
    />

    <div
      class="relative flex items-start gap-3 pointer-events-none p-4"
      :class="previewsVisible && props.layout === 'grid' ? 'pt-[18px]' : ''"
    >
      <!-- List mode preview: leading 124px thumb. Sits as the first flex
           child; the favicon then moves *inline* into the title row (see
           below), which is what makes the row feel like a single text
           block beside the thumb rather than two stacked columns. -->
      <BookmarkPreview
        v-if="previewsVisible && props.layout === 'list'"
        :bookmark="props.bookmark"
        variant="list"
        :nonce="previewNonce"
        class="w-[124px] shrink-0 mt-0.5"
      />
      <!-- Favicon-as-own-column only when previews aren't carrying the
           identity. With previews on:
             - grid: the chip overlay handles identity (no favicon here)
             - list: the favicon moves inline into the title row -->
      <BookmarkFavicon
        v-if="!previewsVisible"
        :bookmark-id="props.bookmark.id"
        :url="props.bookmark.data.url"
        :size="20"
        class="mt-0.5"
      />
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2">
          <BookmarkFavicon
            v-if="previewsVisible && props.layout === 'list'"
            :bookmark-id="props.bookmark.id"
            :url="props.bookmark.data.url"
            :size="16"
            class="shrink-0"
          />
          <h3 class="font-medium text-foreground truncate">
            {{ props.bookmark.data.title }}
          </h3>
          <!-- Lazy radix mount: see `menuActivated` in script -->
          <button
            v-if="!menuActivated"
            class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
            @click.stop="menuActivated = true"
          >
            <MoreHorizontal class="h-4 w-4" />
          </button>
          <DropdownMenuRoot v-else :default-open="true">
            <DropdownMenuTrigger as-child>
              <button
                class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
                @click.stop
              >
                <MoreHorizontal class="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContentCl class="min-w-[160px] z-50">
              <DropdownMenuItemCl @select="emit('edit', props.bookmark)">
                {{ $t('common.edit') }}
              </DropdownMenuItemCl>
              <DropdownMenuItemCl @select="emit('move', props.bookmark)">
                {{ $t('bookmark.moveToFolder') }}
              </DropdownMenuItemCl>
              <DropdownMenuItemCl
                v-if="previewsVisible"
                @select="refreshPreview"
              >
                {{ $t('bookmark.refreshPreview') }}
              </DropdownMenuItemCl>
              <DropdownMenuItemCl variant="destructive" @select="emit('delete', props.bookmark)">
                {{ $t('common.delete') }}
              </DropdownMenuItemCl>
            </DropdownMenuContentCl>
          </DropdownMenuRoot>
        </div>

        <div class="flex items-center gap-1 text-sm text-muted-foreground mt-0.5">
          <span class="truncate">{{ props.bookmark.data.url }}</span>
          <ExternalLink class="h-3 w-3 shrink-0" />
        </div>

        <p
          v-if="props.bookmark.data.description"
          class="text-sm text-muted-foreground mt-2 line-clamp-2"
        >
          {{ props.bookmark.data.description }}
        </p>

        <div
          v-if="
            (props.bookmark.data.tagIds && props.bookmark.data.tagIds.size > 0) || getFolderName()
          "
          class="flex flex-wrap items-center gap-1 mt-2"
        >
          <!--          Folder pill-->
          <button
            v-if="getFolderName()"
            type="button"
            data-testid="card-folder-pill"
            :data-folder-id="props.bookmark.data.folderId"
            class="pointer-events-auto relative z-10 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs text-muted-foreground border border-dashed border-border hover:text-foreground hover:border-foreground hover:bg-secondary transition-colors"
            :class="{
              'text-foreground border-solid border-foreground bg-secondary':
                folderStore.selectedFolderId === props.bookmark.data.folderId,
            }"
            :title="`Filter by folder: ${getFolderName()}`"
            @click="onToggleFolderFilter"
          >
            <Folder class="h-3 w-3" />
            <span>in {{ getFolderName() }}</span>
          </button>
          <!--           Tag Pills-->
          <button
            v-for="tagId in props.bookmark.data.tagIds"
            :key="tagId"
            type="button"
            data-testid="card-tag-pill"
            :data-tag-name="getTagById(tagId)?.data.name ?? ''"
            class="pointer-events-auto relative z-10 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs bg-secondary text-foreground border border-transparent hover:bg-[color-mix(in_oklab,var(--tag-color)_14%,var(--color-secondary))] hover:border-[var(--tag-color)] transition-colors"
            :class="tagClass(tagId)"
            :style="{ '--tag-color': getTagById(tagId)?.data.color ?? '#64748b' }"
            :title="tagTitle(tagId)"
            @click="onTagClick($event, tagId)"
          >
            <span
              class="h-2 w-2 rounded-sm"
              :style="{ background: getTagById(tagId)?.data.color ?? '#64748b' }"
            />
            {{ getTagById(tagId)?.data.name ?? tagId.substring(0, 8) }}
          </button>
        </div>
        <!--           Property badges row — hidden behind a global pref since they can be noisy. -->
        <div
          v-if="showPropertyBadges && visibleProps.length > 0"
          class="flex flex-wrap gap-1.5 mt-1.5 pt-1.5 border-t border-border/45 pointer-events-none"
        >
          <BookmarkPropertyBadge
            v-for="vp in visibleProps"
            :key="vp.def.id"
            :prop-def="vp.def"
            :value="vp.value"
            :active="isPropertyTokenActive(vp.def.data.name)"
            class="pointer-events-auto relative z-10"
            @click="onPropertyBadgeClick(vp.def, vp.value)"
          />
        </div>
      </div>
    </div>

    <div
      v-if="showStats"
      class="absolute bottom-3 right-4 hidden xl:flex flex-col items-end gap-0.5 text-xs text-muted-foreground/40"
    >
      <span class="group/clicks inline-flex items-center gap-1">
        <span
          class="max-w-0 overflow-hidden whitespace-nowrap transition-all duration-200 group-hover/clicks:max-w-20 group-hover/clicks:opacity-100 opacity-0"
          >{{ $t('bookmark.statClicks') }}</span
        >
        <MousePointerClick class="h-3 w-3 shrink-0" />
        {{ props.bookmark.clickCount }}
      </span>
      <span
        v-if="props.bookmark.lastClickedAt"
        class="group/lastClicked inline-flex items-center gap-1"
      >
        <span
          class="max-w-0 overflow-hidden whitespace-nowrap transition-all duration-200 group-hover/lastClicked:max-w-28 group-hover/lastClicked:opacity-100 opacity-0"
          >{{ $t('bookmark.statLastClicked') }}</span
        >
        <Clock class="h-3 w-3 shrink-0" />
        {{ formatRelativeTime(props.bookmark.lastClickedAt) }}
      </span>
    </div>

    <!-- Hover-to-enlarge popup is rendered once at BookmarkList level
         (see BookmarkPreviewPopup) and driven by the hover-intent
         controller; this row only reports enter/leave. -->
  </div>
</template>
