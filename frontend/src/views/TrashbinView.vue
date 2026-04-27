<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Trash2, Undo2, ArrowLeft, Folder as FolderIcon, Bookmark as BookmarkIcon } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl, ConfirmDialog } from '@/components/ui'
import { useTrashbinStore } from '@/stores/trashbin'
import { useNotificationStore } from '@/stores/notification'
import router from '@/router'
import { isNullish } from '@/lib/nullish'

const { t, locale } = useI18n()
const trashbin = useTrashbinStore()
const notify = useNotificationStore()

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
    <div class="container mx-auto max-w-4xl px-4 py-6">
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <ButtonCl variant="ghost" size="icon" @click="goBack" :aria-label="$t('common.back')">
            <ArrowLeft class="h-4 w-4" />
          </ButtonCl>
          <h1 class="text-2xl font-semibold">{{ $t('trashbin.title') }}</h1>
        </div>
        <ButtonCl
          variant="destructive"
          :disabled="trashbin.isEmpty"
          data-testid="trashbin-empty-btn"
          @click="emptyOpen = true"
        >
          <Trash2 class="mr-2 h-4 w-4" />
          {{ $t('trashbin.emptyTrashbin') }}
        </ButtonCl>
      </div>

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
              {{ $t('trashbin.folderItem') }} · {{ $t('trashbin.deletedAt') }}: {{ formatDate(folder.deletedAt) }}
            </div>
          </div>
          <ButtonCl
            variant="outline"
            size="sm"
            data-testid="trashbin-restore-folder-btn"
            @click="handleRestoreFolder(folder.id)"
          >
            <Undo2 class="mr-1 h-4 w-4" />
            {{ $t('trashbin.restore') }}
          </ButtonCl>
          <ButtonCl
            variant="destructive"
            size="sm"
            data-testid="trashbin-purge-folder-btn"
            @click="askPurge('folder', folder.id)"
          >
            <Trash2 class="mr-1 h-4 w-4" />
            {{ $t('trashbin.deletePermanently') }}
          </ButtonCl>
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
              {{ $t('trashbin.deletedAt') }}: {{ formatDate(item.deletedAt) }}
            </div>
          </div>
          <ButtonCl
            variant="outline"
            size="sm"
            data-testid="trashbin-restore-btn"
            @click="handleRestoreBookmark(item.id)"
          >
            <Undo2 class="mr-1 h-4 w-4" />
            {{ $t('trashbin.restore') }}
          </ButtonCl>
          <ButtonCl
            variant="destructive"
            size="sm"
            data-testid="trashbin-purge-btn"
            @click="askPurge('bookmark', item.id)"
          >
            <Trash2 class="mr-1 h-4 w-4" />
            {{ $t('trashbin.deletePermanently') }}
          </ButtonCl>
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
