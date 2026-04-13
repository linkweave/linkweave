<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { ButtonCl, DialogCl } from '@/components/ui'
import { ArrowLeft, Plus, Pencil, Trash2, Star } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import router from '@/router'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

const collections = computed(() => collectionStore.collections)
const createOpen = ref(false)
const editOpen = ref(false)
const deleteOpen = ref(false)
const name = ref('')
const editingId = ref<string | null>(null)
const deletingId = ref<string | null>(null)
const deletingName = ref('')
const confirmName = ref('')
const loading = ref(false)

onMounted(async () => {
  await collectionStore.fetchCollections()
})

function openCreate() {
  name.value = ''
  createOpen.value = true
}

async function handleCreate() {
  if (!name.value.trim()) {
    notification.warning(t('collectionManage.nameRequired'))
    return
  }
  loading.value = true
  const result = await collectionStore.createCollection(name.value.trim())
  loading.value = false
  if (result) {
    createOpen.value = false
    notification.success(t('collectionManage.createTitle'))
  }
}

function openEdit(id: string, currentName: string) {
  editingId.value = id
  name.value = currentName
  editOpen.value = true
}

async function handleEdit() {
  if (!name.value.trim() || !editingId.value) {
    notification.warning(t('collectionManage.nameRequired'))
    return
  }
  loading.value = true
  const ok = await collectionStore.updateCollection(editingId.value, name.value.trim())
  loading.value = false
  if (ok) {
    editOpen.value = false
    notification.success(t('collectionManage.editTitle'))
  }
}

function openDelete(id: string, collectionName: string) {
  deletingId.value = id
  deletingName.value = collectionName
  confirmName.value = ''
  deleteOpen.value = true
}

async function handleDelete() {
  if (!deletingId.value) return
  if (confirmName.value !== deletingName.value) {
    notification.warning(t('collectionManage.nameMismatchWarning', { name: deletingName.value }))
    return
  }
  loading.value = true
  const ok = await collectionStore.deleteCollection(deletingId.value)
  loading.value = false
  if (ok) {
    deleteOpen.value = false
    notification.success(t('collectionManage.deleteTitle'))
  }
}

async function handleSetDefault(id: string) {
  await collectionStore.setDefaultCollection(id)
}

function goBack() {
  router.go(-1)
}
</script>

<template>
  <MainLayout>
    <template #header-title>
      <div class="flex items-center gap-2">
        <ButtonCl variant="ghost" size="icon" data-testid="collection-manage-back-btn" @click="goBack">
          <ArrowLeft class="h-4 w-4" />
        </ButtonCl>
        <span class="text-xl font-semibold text-foreground truncate">{{ t('collectionManage.title') }}</span>
      </div>
    </template>

    <template #header-actions>
      <ButtonCl data-testid="collection-manage-create-btn" @click="openCreate">
        <Plus class="h-4 w-4" />
        {{ t('common.create') }}
      </ButtonCl>
    </template>

    <div class="max-w-2xl mx-auto">
      <div v-if="collections.length === 0" class="text-center py-8 text-muted-foreground">
      {{ t('collectionManage.noCollections') }}
    </div>

    <div class="space-y-2">
      <div
        v-for="col in collections"
        :key="col.id"
        :data-testid="`collection-row-${col.id}`"
        class="flex items-center gap-3 p-3 rounded-lg border border-border bg-card hover:bg-accent/50 transition-colors"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium text-foreground truncate">{{ col.name }}</span>
            <span
              v-if="col.isDefault"
              class="text-[9px] bg-primary/25 text-primary px-1.5 py-0.5 rounded shrink-0"
            >
              {{ t('collectionManage.default') }}
            </span>
          </div>
          <div class="text-xs text-muted-foreground mt-0.5">
            {{ t('collectionManage.role') }}: {{ col.role }}
          </div>
        </div>

        <div class="flex items-center gap-1 shrink-0">
          <ButtonCl
            v-if="!col.isDefault"
            variant="ghost"
            size="icon"
            :data-testid="`collection-set-default-btn-${col.id}`"
            :title="t('collectionSwitcher.setAsDefault')"
            @click="handleSetDefault(col.id!)"
          >
            <Star class="h-4 w-4" />
          </ButtonCl>
          <ButtonCl
            v-if="col.role === 'OWNER'"
            variant="ghost"
            size="icon"
            :data-testid="`collection-edit-btn-${col.id}`"
            :title="t('common.edit')"
            @click="openEdit(col.id!, col.name ?? '')"
           >
             <Pencil class="h-4 w-4" />
           </ButtonCl>
           <ButtonCl
             v-if="col.role === 'OWNER'"
             variant="ghost"
             size="icon"
             :data-testid="`collection-delete-btn-${col.id}`"
             :title="t('common.delete')"
             @click="openDelete(col.id!, col.name ?? '')"
          >
            <Trash2 class="h-4 w-4 text-destructive" />
          </ButtonCl>
        </div>
      </div>
    </div>

    <DialogCl :open="createOpen" @update:open="createOpen = $event">
      <template #title>{{ t('collectionManage.createTitle') }}</template>
      <form @submit.prevent="handleCreate" class="space-y-4">
        <div class="space-y-2">
          <label for="create-collection-name" class="text-sm font-medium">{{ t('collectionManage.name') }} *</label>
          <input
            id="create-collection-name"
            v-model="name"
            type="text"
            required
            maxlength="255"
            data-testid="create-collection-name-input"
            :placeholder="t('collectionManage.namePlaceholder')"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
        <div class="flex justify-end gap-2">
          <ButtonCl type="button" variant="outline" @click="createOpen = false">
            {{ t('common.cancel') }}
          </ButtonCl>
          <ButtonCl type="submit" data-testid="collection-create-submit-btn" :disabled="loading">
            {{ loading ? t('common.loading') : t('common.create') }}
          </ButtonCl>
        </div>
      </form>
    </DialogCl>

    <DialogCl :open="editOpen" @update:open="editOpen = $event">
      <template #title>{{ t('collectionManage.editTitle') }}</template>
      <form @submit.prevent="handleEdit" class="space-y-4">
        <div class="space-y-2">
          <label for="edit-collection-name" class="text-sm font-medium">{{ t('collectionManage.name') }} *</label>
          <input
            id="edit-collection-name"
            v-model="name"
            type="text"
            required
            maxlength="255"
            data-testid="edit-collection-name-input"
            :placeholder="t('collectionManage.namePlaceholder')"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
        <div class="flex justify-end gap-2">
          <ButtonCl type="button" variant="outline" @click="editOpen = false">
            {{ t('common.cancel') }}
          </ButtonCl>
          <ButtonCl type="submit" data-testid="collection-edit-submit-btn" :disabled="loading">
            {{ loading ? t('common.loading') : t('common.save') }}
          </ButtonCl>
        </div>
      </form>
    </DialogCl>

    <DialogCl :open="deleteOpen" @update:open="deleteOpen = $event">
      <template #title>{{ t('collectionManage.deleteTitle') }}</template>
      <form class="space-y-4" @submit.prevent="handleDelete">
        <p class="text-sm text-muted-foreground">{{ t('collectionManage.deleteConfirm') }}</p>
        <div class="space-y-2">
          <label for="delete-confirm-name" class="text-sm font-medium">
            {{ t('collectionManage.typeToConfirm', { name: deletingName }) }}
          </label>
          <input
            id="delete-confirm-name"
            v-model="confirmName"
            type="text"
            data-testid="delete-confirm-name-input"
            :placeholder="deletingName"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
        <div class="flex justify-end gap-2">
          <ButtonCl type="button" variant="outline" @click="deleteOpen = false">
            {{ t('common.cancel') }}
          </ButtonCl>
          <ButtonCl
            type="submit"
            variant="destructive"
            data-testid="collection-delete-submit-btn"
            :disabled="loading || confirmName !== deletingName"
          >
            {{ loading ? t('common.loading') : t('common.delete') }}
          </ButtonCl>
        </div>
      </form>
    </DialogCl>
    </div>
  </MainLayout>
</template>
