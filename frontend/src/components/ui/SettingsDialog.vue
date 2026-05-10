<script setup lang="ts">
import ImportCollectionDialog from '@/components/bookmark/ImportCollectionDialog.vue'
import { ButtonCl, DialogCl } from '@/components/ui'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { useUiStore, type Theme, type BookmarkLayout } from '@/stores/ui'
import { downloadBlobDirectly, extractFilenameFromContentDispositionHeader } from '@/utils/download'
import { Download, LayoutGrid, LayoutList, Monitor, Moon, Sun, Upload, Layers } from 'lucide-vue-next'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ui = useUiStore()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

interface Props {
  open?: boolean
}

withDefaults(defineProps<Props>(), {
  open: false,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const isImporting = ref(false)
const isExporting = ref(false)

const themes: { value: Theme; icon: typeof Sun; labelKey: string }[] = [
  { value: 'light', icon: Sun, labelKey: 'settings.themeLight' },
  { value: 'dark', icon: Moon, labelKey: 'settings.themeDark' },
  { value: 'system', icon: Monitor, labelKey: 'settings.themeSystem' },
]

const layouts: { value: BookmarkLayout; icon: typeof LayoutList; labelKey: string }[] = [
  { value: 'list', icon: LayoutList, labelKey: 'settings.layoutList' },
  { value: 'grid', icon: LayoutGrid, labelKey: 'settings.layoutGrid' },
  { value: 'grouped', icon: Layers, labelKey: 'settings.layoutGrouped' },
]

async function handleImported() {
  if (collectionStore.currentCollectionId) {
    await collectionStore.fetchCollectionInfo(collectionStore.currentCollectionId)
  }
}

async function handleExport() {
  if (!collectionStore.currentCollectionId) return

  isExporting.value = true
  try {
    const response = await fetch(`/api/collections/${collectionStore.currentCollectionId}/export`, {
      credentials: 'include',
    })
    if (!response.ok) throw new Error('Export failed')

    const contentDisposition = response.headers.get('Content-Disposition')
    const filename =
      extractFilenameFromContentDispositionHeader(contentDisposition) ?? 'bookmarks.html'
    const blob = await response.blob()
    downloadBlobDirectly(blob, filename)
  } catch (err) {
    void notification.handleApiError(err, t('settings.exportError'))
  } finally {
    isExporting.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('settings.title') }}</template>

    <div class="space-y-6">
      <div>
        <h3 class="text-sm font-medium text-foreground mb-3">
          {{ t('settings.appearance') }}
        </h3>
        <p class="text-sm text-muted-foreground mb-3">
          {{ t('settings.theme') }}
        </p>
        <div class="flex gap-2">
          <button
            v-for="option in themes"
            :key="option.value"
            class="flex flex-1 flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm transition-colors"
            :class="
              ui.theme === option.value
                ? 'border-primary bg-primary/10 text-foreground'
                : 'border-border bg-card text-muted-foreground hover:bg-accent'
            "
            @click="ui.setTheme(option.value)"
          >
            <component :is="option.icon" class="h-5 w-5" />
            {{ t(option.labelKey) }}
          </button>
        </div>
      </div>

      <div>
        <h3 class="text-sm font-medium text-foreground mb-1">
          {{ t('settings.defaultLayout') }}
        </h3>
        <p class="text-sm text-muted-foreground mb-3">
          {{ t('settings.defaultLayoutHint') }}
        </p>
        <div class="flex gap-2">
          <button
            v-for="option in layouts"
            :key="option.value"
            class="flex flex-1 flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm transition-colors"
            :class="
              ui.bookmarkLayout === option.value
                ? 'border-primary bg-primary/10 text-foreground'
                : 'border-border bg-card text-muted-foreground hover:bg-accent'
            "
            @click="ui.setBookmarkLayout(option.value)"
          >
            <component :is="option.icon" class="h-5 w-5" />
            {{ t(option.labelKey) }}
          </button>
        </div>
      </div>

      <div v-if="collectionStore.currentCollectionId">
        <h3 class="text-sm font-medium text-foreground mb-3">
          {{ t('settings.dataManagement') }}
        </h3>
        <div class="flex flex-col gap-2">
          <ButtonCl variant="outline" :disabled="isExporting" @click="handleExport">
            <Download class="mr-2 h-4 w-4" />
            {{ t('settings.exportCollection') }}
          </ButtonCl>
          <ButtonCl variant="outline" @click="isImporting = true">
            <Upload class="mr-2 h-4 w-4" />
            {{ t('settings.importCollection') }}
          </ButtonCl>
        </div>
      </div>
    </div>
  </DialogCl>

  <ImportCollectionDialog
    v-if="collectionStore.currentCollectionId"
    v-model:open="isImporting"
    :collection-id="collectionStore.currentCollectionId"
    @imported="handleImported"
  />
</template>
