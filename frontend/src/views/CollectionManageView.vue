<script setup lang="ts">
import CreateCollectionDialog from '@/components/collection/CreateCollectionDialog.vue'
import EditCollectionDialog from '@/components/collection/EditCollectionDialog.vue'
import DeleteCollectionDialog from '@/components/collection/DeleteCollectionDialog.vue'
import ShareCollectionDialog from '@/components/collection/ShareCollectionDialog.vue'
import {MainLayout} from '@/components/layout'
import {ButtonCl} from '@/components/ui'
import router from '@/router'
import {useCollectionStore} from '@/stores/collection'
import {ArrowLeft, Pencil, Plus, Star, Trash2, Users} from 'lucide-vue-next'
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const collectionStore = useCollectionStore()

const collections = computed(() => collectionStore.collections)
const createOpen = ref(false)
const editOpen = ref(false)
const deleteOpen = ref(false)
const editingId = ref<string | null>(null)
const editingName = ref('')
const editingIsOwner = ref(false)
const deletingId = ref<string | null>(null)
const deletingName = ref('')
const shareOpen = ref(false)
const sharingCollectionId = ref('')
const sharingCollectionName = ref('')

onMounted(async () => {
  await collectionStore.fetchCollections()
})

function openEdit(id: string, currentName: string, isOwner: boolean) {
  editingId.value = id
  editingName.value = currentName
  editingIsOwner.value = isOwner
  editOpen.value = true
}

function openDelete(id: string, collectionName: string) {
  deletingId.value = id
  deletingName.value = collectionName
  deleteOpen.value = true
}

function openShare(id: string, collectionName: string) {
  sharingCollectionId.value = id
  sharingCollectionName.value = collectionName
  shareOpen.value = true
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
      <ButtonCl data-testid="collection-manage-create-btn" @click="createOpen = true">
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
            <span
              v-if="col.shared"
              class="text-[9px] bg-blue-500/25 text-blue-500 px-1.5 py-0.5 rounded inline-flex items-center gap-0.5 shrink-0"
            >
              <Users class="h-2.5 w-2.5" />
              {{ t('collectionManage.shared') }}
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
            :data-testid="`collection-share-btn-${col.id}`"
            :title="t('collectionManage.shareTitle')"
            @click="openShare(col.id!, col.name ?? '')"
          >
            <Users class="h-4 w-4" />
          </ButtonCl>
          <ButtonCl
            v-if="col.role === 'OWNER'"
            variant="ghost"
            size="icon"
            :data-testid="`collection-edit-btn-${col.id}`"
            :title="t('common.edit')"
             @click="openEdit(col.id!, col.name ?? '', true)"
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

    <CreateCollectionDialog
      :open="createOpen"
      @update:open="createOpen = $event"
    />

    <EditCollectionDialog
      :open="editOpen"
      :collection-id="editingId ?? ''"
      :current-name="editingName"
      :is-owner="editingIsOwner"
      @update:open="editOpen = $event"
    />

    <DeleteCollectionDialog
      :open="deleteOpen"
      :collection-id="deletingId ?? ''"
      :collection-name="deletingName"
      @update:open="deleteOpen = $event"
    />

    <ShareCollectionDialog
      :open="shareOpen"
      :collection-id="sharingCollectionId"
      :collection-name="sharingCollectionName"
      @update:open="shareOpen = $event"
    />
    </div>
  </MainLayout>
</template>
