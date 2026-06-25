<script setup lang="ts">
import ImportTree from '@/components/import/ImportTree.vue'
import { MainLayout } from '@/components/layout'
import { ButtonLw, FolderSelectLw } from '@/components/ui'
import { useImportSelection } from '@/composables/useImportSelection'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useImportStore } from '@/stores/import'
import { useNotificationStore } from '@/stores/notification'
import { ArrowLeft, Loader2, Upload } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useImportStore()
const collectionStore = useCollectionStore()
const folderStore = useFolderStore()
const notify = useNotificationStore()

const collectionId = route.params.id as string
const fileInput = ref<HTMLInputElement | null>(null)

const selection = useImportSelection(toRef(store, 'tree'))

const collectionName = computed(() => collectionStore.collectionName ?? '')
const isAllDuplicates = computed(
  () => store.totalBookmarks > 0 && selection.duplicateCount.value === store.totalBookmarks,
)

onMounted(async () => {
  const pending = store.takePendingFile()
  if (pending) {
    await runPreview(pending)
  }
})

onBeforeUnmount(() => store.clear())

async function runPreview(file: File) {
  try {
    await store.preview(collectionId, file)
  } catch (err) {
    void notify.handleApiError(err, t('importReview.parseError'))
  }
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) void runPreview(file)
}

async function onImport() {
  const nodes = selection.selectedTree()
  try {
    const result = await store.commit(collectionId, nodes, selection.skipDuplicates.value)
    await collectionStore.fetchCollectionInfo(collectionId)
    notify.success(
      t('importReview.success', { count: result.imported, collection: collectionName.value }),
    )
    await router.push({ name: 'collection', params: { id: collectionId } })
  } catch (err) {
    void notify.handleApiError(err, t('importReview.commitError'))
  }
}

function goBack() {
  router.go(-1)
}
</script>

<template>
  <MainLayout :hide-sidebar="true">
    <div class="container mx-auto flex h-full max-w-3xl flex-col px-4 py-6">
      <!-- Header -->
      <div class="mb-4 flex items-center gap-3">
        <ButtonLw variant="ghost" size="icon" :aria-label="t('common.back')" @click="goBack">
          <ArrowLeft class="h-4 w-4" />
        </ButtonLw>
        <Upload class="h-5 w-5 text-muted-foreground" />
        <h1 class="text-2xl font-semibold">{{ t('importReview.title') }}</h1>
        <span v-if="store.fileName" class="truncate text-sm text-muted-foreground">
          {{ store.fileName }}
        </span>
      </div>

      <!-- Parsing -->
      <div v-if="store.parsing" class="space-y-2 py-8 text-center">
        <Loader2 class="mx-auto h-6 w-6 animate-spin text-muted-foreground" />
        <p class="text-sm text-muted-foreground">{{ t('importReview.parsing') }}</p>
      </div>

      <!-- Error -->
      <div
        v-else-if="store.error"
        class="rounded-md border border-destructive/40 bg-destructive/10 p-4 text-sm"
      >
        <p class="mb-3">{{ t('importReview.malformed') }}</p>
        <ButtonLw variant="outline" size="sm" @click="fileInput?.click()">
          {{ t('importReview.rePick') }}
        </ButtonLw>
      </div>

      <!-- Empty manifest (no bookmarks) -->
      <div
        v-else-if="store.hasManifest && store.totalBookmarks === 0"
        class="rounded-md border border-border p-6 text-center text-sm text-muted-foreground"
      >
        <p class="mb-1">{{ t('importReview.empty') }}</p>
        <p class="mb-3 text-xs">{{ t('importReview.acceptedFormat') }}</p>
        <ButtonLw variant="outline" size="sm" @click="fileInput?.click()">
          {{ t('importReview.rePick') }}
        </ButtonLw>
      </div>

      <!-- Dropzone (initial / no file handed off) -->
      <div
        v-else-if="!store.hasManifest"
        class="flex flex-1 flex-col items-center justify-center rounded-md border border-dashed border-border p-10 text-center"
      >
        <Upload class="mb-3 h-8 w-8 text-muted-foreground" />
        <p class="mb-3 text-sm text-muted-foreground">{{ t('importReview.dropzone') }}</p>
        <ButtonLw variant="outline" @click="fileInput?.click()">
          {{ t('importReview.choose') }}
        </ButtonLw>
      </div>

      <!-- Review -->
      <template v-else>
        <!-- Destination + bulk actions -->
        <div class="mb-3 flex flex-wrap items-center gap-3 rounded-md border border-border p-3">
          <div class="flex items-center gap-2 text-sm">
            <span class="text-muted-foreground">{{ t('importReview.importInto') }}</span>
            <span class="font-medium">{{ collectionName }}</span>
          </div>
          <FolderSelectLw
            v-model="store.destinationFolderId"
            :folders="folderStore.folders"
            :placeholder="t('importReview.rootFolder')"
            class="min-w-[12rem]"
          />
          <div class="ml-auto flex items-center gap-2">
            <ButtonLw variant="ghost" size="sm" @click="selection.selectAll()">
              {{ t('importReview.selectAll') }}
            </ButtonLw>
            <ButtonLw variant="ghost" size="sm" @click="selection.clear()">
              {{ t('importReview.clear') }}
            </ButtonLw>
          </div>
        </div>

        <!-- Skip-duplicates pill -->
        <label
          v-if="selection.duplicateCount.value > 0"
          class="mb-3 inline-flex cursor-pointer items-center gap-2 rounded-full border border-border px-3 py-1 text-xs"
        >
          <input
            type="checkbox"
            :checked="selection.skipDuplicates.value"
            class="accent-primary"
            data-testid="import-skip-duplicates"
            @change="selection.setSkipDuplicates(($event.target as HTMLInputElement).checked)"
          />
          {{ t('importReview.skipDuplicates', { count: selection.duplicateCount.value }) }}
        </label>

        <!-- Unsupported-URL note -->
        <p v-if="store.unsupportedCount > 0" class="mb-3 text-xs text-muted-foreground">
          {{ t('importReview.unsupported', { count: store.unsupportedCount }) }}
        </p>

        <!-- Tree -->
        <div
          class="min-h-0 flex-1 overflow-y-auto rounded-md border border-border p-2"
          :aria-label="t('importReview.title')"
        >
          <ImportTree :nodes="store.tree" :model="selection" />
        </div>

        <!-- Footer -->
        <div class="mt-4 flex items-center justify-between gap-4" aria-live="polite">
          <p class="text-sm text-muted-foreground">
            <template v-if="store.committing">
              {{ t('importReview.importing', { count: selection.selectedCount.value }) }}
            </template>
            <template v-else-if="isAllDuplicates && selection.selectedCount.value === 0">
              {{ t('importReview.allDuplicates') }}
            </template>
            <template v-else>
              {{
                t('importReview.willImport', {
                  count: selection.selectedCount.value,
                  collection: collectionName,
                })
              }}
            </template>
          </p>
          <ButtonLw
            :disabled="selection.selectedCount.value === 0 || store.committing"
            data-testid="import-review-submit"
            @click="onImport"
          >
            <Loader2 v-if="store.committing" class="mr-2 h-4 w-4 animate-spin" />
            {{ t('importReview.submit', { count: selection.selectedCount.value }) }}
          </ButtonLw>
        </div>
      </template>

      <input
        ref="fileInput"
        type="file"
        accept=".html,.htm"
        class="hidden"
        data-testid="import-file-input"
        @change="onFileChange"
      />
    </div>
  </MainLayout>
</template>
