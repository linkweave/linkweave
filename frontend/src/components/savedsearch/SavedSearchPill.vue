<script setup lang="ts">
import { computed } from 'vue'
import { Filter, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
import { useSavedSearchStore } from '@/stores/savedSearch'

const { t } = useI18n()
const savedSearchStore = useSavedSearchStore()
const notification = useNotificationStore()

const active = computed(() => {
  const id = savedSearchStore.activeSavedSearchId
  if (!id) return null
  return savedSearchStore.savedSearches.find((s) => s.id === id) ?? null
})

const isDirty = computed(() => active.value !== null && !savedSearchStore.currentMatchesActive)

async function onUpdate() {
  if (!active.value) return
  try {
    const updated = await savedSearchStore.updateActiveQuery()
    if (updated) {
      notification.success(t('savedSearch.updated', { name: updated.data.name }))
    }
  } catch (err) {
    notification.handleApiError(err, t('savedSearch.updateError'))
  }
}

function onDeselect() {
  savedSearchStore.deactivate()
}
</script>

<template>
  <span
    v-if="active"
    class="ss-pill inline-flex items-center gap-1.5 rounded-full text-[11.5px] font-medium transition-colors"
    :class="isDirty ? 'ss-pill--dirty' : ''"
    data-testid="saved-search-pill"
    :data-state="isDirty ? 'dirty' : 'matched'"
  >
    <Filter class="h-3 w-3" aria-hidden="true" />
    <span class="truncate max-w-[10rem]">{{ active.data.name }}</span>
    <button
      v-if="isDirty"
      type="button"
      class="ml-1 px-1.5 py-0.5 rounded-full text-[11px] font-semibold hover:bg-[color-mix(in_oklab,var(--color-ss)_18%,transparent)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      data-testid="saved-search-pill-update"
      @click="onUpdate"
    >
      {{ t('savedSearch.pillUpdate') }}
    </button>
    <button
      type="button"
      class="inline-flex h-4 w-4 items-center justify-center rounded-full hover:bg-[color-mix(in_oklab,var(--color-ss)_18%,transparent)] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      :aria-label="t('savedSearch.pillDeselect')"
      data-testid="saved-search-pill-deselect"
      @click="onDeselect"
    >
      <X class="h-3 w-3" aria-hidden="true" />
    </button>
  </span>
</template>

<style scoped>
.ss-pill {
  padding: 2px 4px 2px 8px;
  background: color-mix(in oklab, var(--color-ss) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-ss) 35%, transparent);
  color: var(--color-ss);
}
.ss-pill--dirty {
  background: color-mix(in oklab, var(--color-ss) 7%, transparent);
  border-style: dashed;
}
</style>
