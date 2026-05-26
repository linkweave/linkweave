<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, Filter, MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { useI18n } from 'vue-i18n'
import { CollapsibleCl, DropdownMenuContentCl, DropdownMenuItemCl } from '@/components/ui'
import { useSidebarSectionExpandedPref } from '@/composables/useSidebarSectionExpandedPref'
import { useNotificationStore } from '@/stores/notification'
import { useSavedSearchStore } from '@/stores/savedSearch'
import SavedSearchPopover from './SavedSearchPopover.vue'
import type { SavedSearchJson } from '@/api/generated'

const { t } = useI18n()
const savedSearchStore = useSavedSearchStore()
const notification = useNotificationStore()

const expanded = useSidebarSectionExpandedPref('smartCollectionsExpanded')

/** Id of the saved search whose rename popover is currently open, or null. */
const renameTargetId = ref<string | null>(null)

function toggle() {
  expanded.value = !expanded.value
}

function activate(saved: SavedSearchJson) {
  savedSearchStore.toggleSavedSearch(saved)
}

function isRenameOpen(savedId: string): boolean {
  return renameTargetId.value === savedId
}

function setRenameOpen(savedId: string, open: boolean) {
  renameTargetId.value = open ? savedId : null
}

async function deleteSaved(saved: SavedSearchJson) {
  try {
    await savedSearchStore.deleteSavedSearch(saved.id)
    notification.success(t('savedSearch.deleted', { name: saved.data.name }))
  } catch (err) {
    notification.handleApiError(err, t('savedSearch.deleteError'))
  }
}
</script>

<template>
  <div class="min-h-0 flex flex-col border-t border-border max-h-[35%]">
    <button
      type="button"
      class="w-full p-3 flex items-center justify-between shrink-0 text-left hover:bg-accent/50 transition-colors"
      :aria-expanded="expanded"
      data-testid="smart-collections-toggle"
      @click="toggle"
    >
      <span class="text-[10px] font-semibold uppercase tracking-[.06em] text-muted-foreground inline-flex items-center gap-2">
        <Filter class="h-3 w-3" :style="{ color: 'var(--color-ss)' }" />
        {{ t('sidebar.smartCollections') }}
      </span>
      <ChevronDown
        class="h-3.5 w-3.5 text-muted-foreground transition-transform duration-200"
        :class="expanded ? 'rotate-180' : ''"
        aria-hidden="true"
      />
    </button>

    <CollapsibleCl :open="expanded">
      <ul class="px-2 pb-2 overflow-y-auto min-h-0" data-testid="smart-collections-list">
        <li
          v-if="savedSearchStore.savedSearches.length === 0"
          class="px-2 py-1.5 text-xs text-muted-foreground/70"
        >
          {{ t('savedSearch.empty') }}
        </li>
        <li
          v-for="saved in savedSearchStore.savedSearches"
          :key="saved.id"
          class="group relative"
        >
          <div
            class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors"
            :class="
              savedSearchStore.activeSavedSearchId === saved.id
                ? 'bg-[color-mix(in_oklab,var(--color-primary)_12%,var(--color-secondary))] text-primary'
                : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
            "
            :data-testid="`smart-collection-row-${saved.data.name}`"
            :data-active="
              savedSearchStore.activeSavedSearchId === saved.id ? 'true' : 'false'
            "
            @click="activate(saved)"
          >
            <Filter
              class="h-3 w-3 shrink-0"
              :style="{
                color: 'var(--color-ss)',
                fill: 'color-mix(in oklab, var(--color-ss) 12%, transparent)',
              }"
            />
            <span class="flex-1 truncate">{{ saved.data.name }}</span>
            <DropdownMenuRoot>
              <DropdownMenuTrigger as-child>
                <button
                  type="button"
                  class="ml-auto h-6 w-6 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground"
                  :aria-label="t('savedSearch.moreActions')"
                  :data-testid="`smart-collection-more-${saved.data.name}`"
                  @click.stop
                >
                  <MoreHorizontal class="h-3.5 w-3.5" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContentCl class="min-w-[140px] z-50">
                <DropdownMenuItemCl @select="setRenameOpen(saved.id, true)">
                  {{ t('savedSearch.renameAction') }}
                </DropdownMenuItemCl>
                <DropdownMenuItemCl variant="destructive" @select="deleteSaved(saved)">
                  {{ t('savedSearch.deleteAction') }}
                </DropdownMenuItemCl>
              </DropdownMenuContentCl>
            </DropdownMenuRoot>
          </div>
          <SavedSearchPopover
            mode="rename"
            :saved-search="saved"
            :open="isRenameOpen(saved.id)"
            @update:open="(v: boolean) => setRenameOpen(saved.id, v)"
          />
        </li>
      </ul>
    </CollapsibleCl>
  </div>
</template>
