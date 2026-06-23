<script setup lang="ts">
import type { ApiKeyJson } from '@/api/generated/models/api-key-json'
import ButtonLw from '@/components/ui/ButtonLw.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { useApiKeyStore } from '@/stores/apiKey'
import { useNotificationStore } from '@/stores/notification'
import { Key, Plus } from '@lucide/vue'
import { storeToRefs } from 'pinia'
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ApiKeyCreateDialog from './ApiKeyCreateDialog.vue'
import ApiKeyRevealDialog from './ApiKeyRevealDialog.vue'

const { t } = useI18n()
const notification = useNotificationStore()
const store = useApiKeyStore()
const { visibleKeys, loading, maxReached } = storeToRefs(store)

const showCreate = ref(false)
const revealKey = ref<string | null>(null)
const confirmRevoke = ref<ApiKeyJson | null>(null)
const revoking = ref(false)

function onKeyCreated(rawKey: string) {
  revealKey.value = rawKey
}

async function revokeConfirmed() {
  const key = confirmRevoke.value
  if (!key) return
  revoking.value = true
  try {
    await store.revoke(key.id)
    notification.success(t('apiKeys.revokeSuccess', { name: key.name }))
  } catch {
    notification.handleApiError(null, t('apiKeys.revokeError'))
  } finally {
    revoking.value = false
    confirmRevoke.value = null
  }
}

function formatDate(date?: Date | null): string {
  if (!date) return t('apiKeys.never')
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(date))
}

onMounted(async () => {
  try {
    await store.load()
  } catch {
    notification.handleApiError(null, t('apiKeys.loadError'))
  }
})
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-3">
      <h3 class="text-sm font-medium text-foreground">{{ t('apiKeys.sectionTitle') }}</h3>
      <ButtonLw
        size="sm"
        :disabled="maxReached"
        :title="maxReached ? t('apiKeys.maxReachedHint') : undefined"
        data-testid="create-api-key-btn"
        @click="showCreate = true"
      >
        <Plus class="h-4 w-4" />
        {{ t('apiKeys.create') }}
      </ButtonLw>
    </div>

    <p v-if="loading" class="text-sm text-muted-foreground py-2">{{ t('common.loading') }}</p>

    <p v-else-if="visibleKeys.length === 0" class="text-sm text-muted-foreground py-2">
      {{ t('apiKeys.empty') }}
    </p>

    <div v-else class="space-y-1.5">
      <div
        v-for="key in visibleKeys"
        :key="key.id"
        class="flex items-start gap-2 rounded-md px-2.5 py-2 text-sm"
        :class="store.isExpired(key) ? 'bg-secondary/40 opacity-70' : 'bg-secondary/60'"
      >
        <Key class="h-4 w-4 shrink-0 mt-0.5 text-muted-foreground" />
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium truncate max-w-[200px]">{{ key.name }}</span>
            <code class="text-xs text-muted-foreground font-mono">lw_{{ key.prefix }}…</code>
            <span
              v-if="store.isExpired(key)"
              class="text-xs rounded px-1.5 py-0.5 bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300"
            >
              {{ t('apiKeys.expired') }}
            </span>
          </div>
          <div class="text-xs text-muted-foreground mt-0.5 flex gap-3 flex-wrap">
            <span>{{ t('apiKeys.created') }}: {{ formatDate(key.createdAt) }}</span>
            <span v-if="key.expiresAt">{{ t('apiKeys.expires') }}: {{ formatDate(key.expiresAt) }}</span>
            <span>{{ t('apiKeys.lastUsed') }}: {{ formatDate(key.lastUsedAt) }}</span>
          </div>
        </div>
        <ButtonLw
          size="sm"
          variant="ghost"
          class="shrink-0 text-destructive hover:text-destructive hover:bg-destructive/10"
          :data-testid="`revoke-api-key-${key.id}`"
          @click="confirmRevoke = key"
        >
          {{ t('apiKeys.revoke') }}
        </ButtonLw>
      </div>
    </div>

    <p v-if="maxReached && visibleKeys.length > 0" class="mt-2 text-xs text-muted-foreground">
      {{ t('apiKeys.maxReachedHint') }}
    </p>
  </div>

  <ApiKeyCreateDialog
    :open="showCreate"
    @update:open="showCreate = $event"
    @created="onKeyCreated"
  />

  <ApiKeyRevealDialog
    v-if="revealKey"
    :open="true"
    :raw-key="revealKey"
    @update:open="(v: boolean) => { if (!v) revealKey = null }"
  />

  <ConfirmDialog
    :open="!!confirmRevoke"
    :title="t('apiKeys.revokeTitle')"
    :message="t('apiKeys.revokeConfirm', { name: confirmRevoke?.name ?? '', prefix: confirmRevoke?.prefix ?? '' })"
    :confirm-label="t('apiKeys.revoke')"
    @update:open="(v: boolean) => { if (!v) confirmRevoke = null }"
    @confirmed="revokeConfirmed"
  />
</template>
