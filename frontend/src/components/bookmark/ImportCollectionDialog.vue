<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { Upload, Loader2 } from 'lucide-vue-next'
import { ImportResourceApi } from '@/api/generated'
import { config } from '@/api'
import { useNotificationStore } from '@/stores/notification'

const props = defineProps<{
  open: boolean
  collectionId: string
}>()

const emits = defineEmits<{
  'update:open': [value: boolean]
  'imported': []
}>()

const { t } = useI18n()
const notification = useNotificationStore()
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const isImporting = ref(false)

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    const file = target.files[0]
    if (file) {
      selectedFile.value = file
    }
  }
}

async function handleImport() {
  if (!selectedFile.value) return

  isImporting.value = true

  try {
    const api = new ImportResourceApi(config)
    await api.apiCollectionsCollectionIdImportPost({
      collectionId: props.collectionId,
      file: selectedFile.value
    })
    emits('imported')
    emits('update:open', false)
    selectedFile.value = null
  } catch (err) {
    void notification.handleApiError(err, t('import.error'))
  } finally {
    isImporting.value = false
  }
}
</script>

<template>
  <DialogCl :open="props.open" @update:open="emits('update:open', $event)">
    <template #title>{{ t('import.title') }}</template>
    <template #description>{{ t('import.description') }}</template>

    <div class="grid gap-4 py-4">
      <div class="grid w-full items-center gap-1.5">
        <label for="bookmark-file" class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
          {{ t('import.file') }}
        </label>
        <div
          class="flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          @click="fileInput?.click()"
        >
          <span :class="selectedFile ? 'text-foreground' : 'text-muted-foreground'">
            {{ selectedFile ? selectedFile.name : t('import.filePlaceholder') }}
          </span>
          <Upload class="h-4 w-4 text-muted-foreground" />
        </div>
        <input
          id="bookmark-file"
          ref="fileInput"
          type="file"
          accept=".html"
          class="hidden"
          @change="handleFileChange"
        />
      </div>
    </div>

    <div class="flex justify-end gap-3">
      <ButtonCl variant="outline" @click="emits('update:open', false)">
        {{ t('common.cancel') }}
      </ButtonCl>
      <ButtonCl :disabled="!selectedFile || isImporting" @click="handleImport">
        <Loader2 v-if="isImporting" class="mr-2 h-4 w-4 animate-spin" />
        {{ t('import.submit') }}
      </ButtonCl>
    </div>
  </DialogCl>
</template>
