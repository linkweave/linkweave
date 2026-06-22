<script setup lang="ts">
import { ref } from 'vue'
import { MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { DropdownMenuContentLw, DropdownMenuItemLw } from '@/components/ui'
import { useI18n } from 'vue-i18n'
import type { BookmarkJson } from '@/api/generated'

// Shared row-action menu. Previously this exact lazy-mount + dropdown markup
// was duplicated inline in BookmarkCard (with the "Refresh preview" item) and
// GroupedBookmarkRow (without it); the hover-preview popup (UC-093) needs the
// same set of actions, so the three call sites now share this component.
//
// The trigger's positioning + hover-reveal classes are caller-specific
// (BookmarkCard reveals on `group-hover`, the grouped row on `group-hover/row`,
// the popup footer is always visible). Tailwind still picks those classes up
// because they appear literally in each caller's template string.
withDefaults(
  defineProps<{
    bookmark: BookmarkJson
    // Render the "Refresh preview" item (BookmarkCard + popup footer only).
    showRefreshPreview?: boolean
    // Classes applied to the trigger button. Callers pass their positioning +
    // hover-reveal tokens (e.g. `ml-auto ... group-hover:opacity-100`).
    triggerClass?: string
    // MoreHorizontal icon size; the grouped row uses a slightly smaller mark.
    iconClass?: string
  }>(),
  {
    showRefreshPreview: false,
    triggerClass:
      'h-8 w-8 inline-flex items-center justify-center rounded hover:bg-primary hover:text-primary-foreground',
    iconClass: 'h-4 w-4',
  },
)

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  refreshPreview: [bookmark: BookmarkJson]
  // Fired when the dropdown opens/closes. The preview popup pins itself open
  // while the menu is open so selecting an item or moving to the dropdown
  // content doesn't dismiss the popup mid-interaction (UC-093 A2).
  'open-change': [open: boolean]
}>()

const { t } = useI18n()

// Lazy radix mount: a plain trigger button until first click, then the full
// DropdownMenuRoot (auto-opened). With many cards on screen this avoids
// mounting an open-able menu per card up front.
const menuActivated = ref(false)

function activate() {
  menuActivated.value = true
  emit('open-change', true)
}

function onRootUpdateOpen(open: boolean) {
  if (!open) {
    menuActivated.value = false
  }
  emit('open-change', open)
}
</script>

<template>
  <button
    v-if="!menuActivated"
    type="button"
    data-testid="bookmark-menu-button"
    :class="triggerClass"
    @click.stop="activate"
  >
    <MoreHorizontal :class="iconClass" />
  </button>
  <DropdownMenuRoot v-else :default-open="true" @update:open="onRootUpdateOpen">
    <DropdownMenuTrigger as-child>
      <button
        type="button"
        data-testid="bookmark-menu-button"
        :class="triggerClass"
        @click.stop
      >
        <MoreHorizontal :class="iconClass" />
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContentLw class="min-w-[160px] z-50">
      <DropdownMenuItemLw @select="emit('edit', bookmark)">
        {{ t('common.edit') }}
      </DropdownMenuItemLw>
      <DropdownMenuItemLw @select="emit('move', bookmark)">
        {{ t('bookmark.moveToFolder') }}
      </DropdownMenuItemLw>
      <DropdownMenuItemLw v-if="showRefreshPreview" @select="emit('refreshPreview', bookmark)">
        {{ t('bookmark.refreshPreview') }}
      </DropdownMenuItemLw>
      <DropdownMenuItemLw variant="destructive" @select="emit('delete', bookmark)">
        {{ t('common.delete') }}
      </DropdownMenuItemLw>
    </DropdownMenuContentLw>
  </DropdownMenuRoot>
</template>
