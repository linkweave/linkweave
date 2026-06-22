<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Trash2, Undo2, ArrowLeft, Folder as FolderIcon, Bookmark as BookmarkIcon } from '@lucide/vue'
import { MainLayout } from '@/components/layout'
import { ButtonLw, ConfirmDialog, ResponsiveButton } from '@/components/ui'
import { useTrashbinStore } from '@/stores/trashbin'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { isNullish } from '@/lib/nullish'
import { useRouter } from 'vue-router'

const { t, locale } = useI18n()
const trashbin = useTrashbinStore()
const collectionStore = useCollectionStore()
const notify = useNotificationStore()
const router = useRouter()

type PurgeKind = 'bookmark' | 'folder'
const purgeOpen = ref(false)
const purgeKind = ref<PurgeKind | null>(null)
const purgeId = ref<string | null>(null)
const emptyOpen = ref(false)

onMounted(() => {
  trashbin.refresh()
})

function formatDate(value: Date | string | null | undefined): string {
  if (!value) return ''
  const date = typeof value === 'string' ? new Date(value) : value
  return date.toLocaleString(locale.value)
}

function collectionName(collectionId: string | undefined): string | undefined {
  if (!collectionId) return undefined
  return collectionStore.collections.find(c => c.id === collectionId)?.name
}

async function handleRestoreBookmark(id: string) {
  await trashbin.restoreBookmark(id)
  notify.success(t('trashbin.restored'))
}

async function handleRestoreFolder(id: string) {
  const originalParentId = trashbin.folders.find(f => f.id === id)?.data.parentId
  const restored = await trashbin.restoreFolder(id)
  if (!isNullish(originalParentId) && isNullish(restored.data.parentId)) {
    notify.success(t('trashbin.restoredToRoot', { name: restored.data.name }))
  } else {
    notify.success(t('trashbin.restored'))
  }
}

function askPurge(kind: PurgeKind, id: string) {
  purgeKind.value = kind
  purgeId.value = id
  purgeOpen.value = true
}

async function confirmPurge() {
  if (!purgeId.value || !purgeKind.value) return
  if (purgeKind.value === 'bookmark') {
    await trashbin.purgeBookmark(purgeId.value)
  } else {
    await trashbin.purgeFolder(purgeId.value)
  }
  purgeId.value = null
  purgeKind.value = null
  notify.success(t('trashbin.purged'))
}

async function confirmEmpty() {
  await trashbin.empty()
  notify.success(t('trashbin.emptied'))
}

function goBack() {
  router.go(-1)
}
</script>

<template>
  <MainLayout :hide-sidebar="true">
    <template #header-leading>
      <ButtonLw variant="ghost" size="icon" :aria-label="$t('common.back')" @click="goBack">
        <ArrowLeft class="h-4 w-4" />
      </ButtonLw>
    </template>
    <template #header-title>
      <h1 class="text-base font-semibold text-foreground truncate">{{ $t('trashbin.title') }}</h1>
    </template>
    <template #header-actions>
      <ResponsiveButton variant="destructive" :disabled="trashbin.isEmpty" :label="$t('trashbin.emptyTrashbin')" data-testid="trashbin-empty-btn" @click="emptyOpen = true">
        <Trash2 />
      </ResponsiveButton>
    </template>

    <div class="container mx-auto max-w-4xl px-4 py-6">

      <div v-if="trashbin.loading" class="text-muted-foreground">…</div>
      <div v-else-if="trashbin.isEmpty" class="rounded-md border border-dashed p-8 text-center text-muted-foreground">
        {{ $t('trashbin.empty') }}
      </div>
      <ul v-else class="divide-y rounded-md border">
        <li
          v-for="folder in trashbin.folders"
          :key="`folder-${folder.id}`"
          class="flex items-center gap-3 p-3"
          :data-testid="`trashbin-folder-${folder.id}`"
        >
          <FolderIcon class="h-5 w-5 shrink-0 text-muted-foreground" />
          <div class="min-w-0 flex-1">
            <div class="truncate font-medium">{{ folder.data.name }}</div>
            <div class="text-xs text-muted-foreground">
              {{ $t('trashbin.folderItem') }} · {{ $t('trashbin.deletedAt') }}: {{ formatDate(folder.deletedAt) }}<template v-if="collectionName(folder.data.collectionId)"> · {{ collectionName(folder.data.collectionId) }}</template>
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <ResponsiveButton variant="outline" :label="$t('trashbin.restore')" data-testid="trashbin-restore-folder-btn" @click="handleRestoreFolder(folder.id)">
              <Undo2 />
            </ResponsiveButton>
            <ResponsiveButton variant="destructive" :label="$t('trashbin.deletePermanently')" data-testid="trashbin-purge-folder-btn" @click="askPurge('folder', folder.id)">
              <Trash2 />
            </ResponsiveButton>
          </div>
        </li>
        <li
          v-for="item in trashbin.bookmarks"
          :key="`bookmark-${item.id}`"
          class="flex items-center gap-3 p-3"
          :data-testid="`trashbin-item-${item.id}`"
        >
          <BookmarkIcon class="h-5 w-5 shrink-0 text-muted-foreground" />
          <div class="min-w-0 flex-1">
            <div class="truncate font-medium">{{ item.data.title }}</div>
            <div class="truncate text-sm text-muted-foreground">{{ item.data.url }}</div>
            <div class="text-xs text-muted-foreground">
              {{ $t('trashbin.deletedAt') }}: {{ formatDate(item.deletedAt) }}<template v-if="collectionName(item.data.collectionId)"> · {{ collectionName(item.data.collectionId) }}</template>
            </div>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <ResponsiveButton variant="outline" :label="$t('trashbin.restore')" data-testid="trashbin-restore-btn" @click="handleRestoreBookmark(item.id)">
              <Undo2 />
            </ResponsiveButton>
            <ResponsiveButton variant="destructive" :label="$t('trashbin.deletePermanently')" data-testid="trashbin-purge-btn" @click="askPurge('bookmark', item.id)">
              <Trash2 />
            </ResponsiveButton>
          </div>
        </li>
      </ul>
    </div>

    <ConfirmDialog
      v-model:open="purgeOpen"
      :message="purgeKind === 'folder' ? $t('trashbin.confirmPurgeFolder') : $t('trashbin.confirmPurge')"
      :confirm-label="$t('trashbin.deletePermanently')"
      @confirmed="confirmPurge"
    />
    <ConfirmDialog
      v-model:open="emptyOpen"
      :message="$t('trashbin.confirmEmpty')"
      :confirm-label="$t('trashbin.emptyTrashbin')"
      @confirmed="confirmEmpty"
    />
  </MainLayout>
</template>
