<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Trash2, Sparkles, EyeOff, Folder as FolderIcon } from '@lucide/vue'
import { MainLayout } from '@/components/layout'
import { ButtonCl, ConfirmDialog, SelectCl } from '@/components/ui'
import { useCleanupSuggestionsStore } from '@/stores/cleanupSuggestions'
import { useNotificationStore } from '@/stores/notification'
import { useRoute, useRouter } from 'vue-router'

const { t, locale } = useI18n()
const store = useCleanupSuggestionsStore()
const notify = useNotificationStore()
const route = useRoute()
const router = useRouter()

const moveToTrashOpen = ref(false)
const collectionId = route.query.collectionId as string

onMounted(async () => {
  if (!collectionId) {
    notify.error(t('cleanupSuggestions.noCollection'))
    router.go(-1)
    return
  }
  await store.fetchThresholds()
  await store.refresh(collectionId)
})

watch(
  () => store.thresholdMonths,
  async () => {
    if (collectionId) {
      await store.refresh(collectionId)
    }
  },
)

function formatDate(value: Date | string | null | undefined): string {
  if (!value) return ''
  const date = value instanceof Date ? value : new Date(value)
  return date.toLocaleDateString(locale.value, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function formatRelativeMonths(months: number): string {
  return t('cleanupSuggestions.thresholdMonths', { months })
}

async function handleDismiss(id: string) {
  try {
    await store.dismissSuggestion(id)
    notify.success(t('cleanupSuggestions.dismissed'))
  } catch {
    notify.error(t('cleanupSuggestions.dismissError'))
  }
}

function confirmMoveToTrash() {
  moveToTrashOpen.value = true
}

async function executeMoveToTrash() {
  const count = store.selectedCount
  try {
    await store.moveSelectedToTrash(collectionId)
    notify.success(t('cleanupSuggestions.movedToTrash', { count }))
  } catch {
    notify.error(t('cleanupSuggestions.moveToTrashError'))
  }
}

function goBack() {
  router.go(-1)
}
</script>

<template>
  <MainLayout :hide-sidebar="true">
    <div class="container mx-auto max-w-4xl px-4 py-6">
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <ButtonCl variant="ghost" size="icon" @click="goBack" :aria-label="$t('common.back')">
            <ArrowLeft class="h-4 w-4" />
          </ButtonCl>
          <Sparkles class="h-5 w-5 text-muted-foreground" />
          <h1 class="text-2xl font-semibold">{{ $t('cleanupSuggestions.title') }}</h1>
        </div>

        <div class="flex items-center gap-3">
          <SelectCl
            :model-value="store.thresholdMonths"
            class="w-auto"
            :aria-label="$t('cleanupSuggestions.thresholdLabel')"
            @update:model-value="(v: string) => (store.thresholdMonths = Number(v))"
          >
            <option v-for="m in store.thresholds" :key="m" :value="m">
              {{ formatRelativeMonths(m) }}
            </option>
          </SelectCl>

          <ButtonCl
            variant="destructive"
            :disabled="store.selectedCount === 0"
            data-testid="cleanup-move-to-trash-btn"
            @click="confirmMoveToTrash"
          >
            <Trash2 class="mr-2 h-4 w-4" />
            {{ $t('cleanupSuggestions.moveToTrash') }}
            <span v-if="store.selectedCount > 0" class="ml-1">({{ store.selectedCount }})</span>
          </ButtonCl>
        </div>
      </div>

      <div v-if="store.loading" class="text-muted-foreground">{{ $t('common.loading') }}</div>

      <div
        v-else-if="store.isEmpty"
        class="rounded-md border border-dashed p-8 text-center text-muted-foreground"
      >
        {{ $t('cleanupSuggestions.empty') }}
      </div>

      <template v-else>
        <div class="mb-3 flex items-center justify-between">
          <ButtonCl
            variant="outline"
            size="sm"
            @click="store.allSelected ? store.clearSelection() : store.selectAll()"
          >
            {{
              store.allSelected
                ? $t('cleanupSuggestions.deselectAll')
                : $t('cleanupSuggestions.selectAll')
            }}
          </ButtonCl>
          <span class="text-sm text-muted-foreground">
            {{ store.suggestions.length }} {{ $t('cleanupSuggestions.suggestions') }}
          </span>
        </div>

        <ul class="divide-y rounded-md border">
          <li
            v-for="suggestion in store.suggestions"
            :key="suggestion.id"
            class="flex items-center gap-3 p-3 transition-colors hover:bg-muted/50"
            :data-testid="`cleanup-suggestion-${suggestion.id}`"
          >
            <input
              type="checkbox"
              :checked="store.selectedIds.has(suggestion.id)"
              class="h-4 w-4 rounded border-input"
              @change="store.toggleSelection(suggestion.id)"
            />

            <div class="min-w-0 flex-1">
              <div class="truncate font-medium">{{ suggestion.title }}</div>
              <div class="truncate text-sm text-muted-foreground">{{ suggestion.url }}</div>
              <div
                class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground"
              >
                <span v-if="suggestion.folderName" class="inline-flex items-center gap-1">
                  <FolderIcon class="h-3 w-3" />
                  {{ suggestion.folderName }}
                </span>
                <span>
                  {{
                    suggestion.neverClicked
                      ? $t('cleanupSuggestions.neverClicked')
                      : $t('cleanupSuggestions.lastClicked', {
                          date: formatDate(suggestion.lastClickedAt),
                        })
                  }}
                </span>
                <span>{{
                  $t('cleanupSuggestions.clickCount', { count: suggestion.clickCount })
                }}</span>
              </div>
            </div>

            <ButtonCl
              variant="ghost"
              size="sm"
              :title="$t('cleanupSuggestions.dismiss')"
              @click="handleDismiss(suggestion.id)"
            >
              <EyeOff class="h-4 w-4" />
            </ButtonCl>
          </li>
        </ul>
      </template>
    </div>

    <ConfirmDialog
      v-model:open="moveToTrashOpen"
      :message="$t('cleanupSuggestions.confirmMoveToTrash', { count: store.selectedCount })"
      :confirm-label="$t('cleanupSuggestions.moveToTrash')"
      @confirmed="executeMoveToTrash"
    />
  </MainLayout>
</template>
