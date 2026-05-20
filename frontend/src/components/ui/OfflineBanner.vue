<script setup lang="ts">
import { CloudOff, WifiOff } from '@lucide/vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOfflineStore } from '@/stores/offline'

const { t } = useI18n()
const offline = useOfflineStore()

const message = computed(() =>
  offline.offlineReason === 'server' ? t('offline.bannerServer') : t('offline.bannerBrowser'),
)
</script>

<template>
  <div
    v-if="offline.isOffline"
    class="bg-amber-500/90 text-amber-950 dark:bg-amber-600/90 dark:text-amber-50 px-4 py-2 text-center text-sm flex items-center justify-center gap-2 shrink-0"
  >
    <CloudOff v-if="offline.offlineReason === 'server'" class="h-4 w-4 shrink-0" />
    <WifiOff v-else class="h-4 w-4 shrink-0" />
    <span>{{ message }}</span>
    <span v-if="offline.timeSinceSync" class="text-amber-800/70 dark:text-amber-200/70 text-xs">
      {{ t('offline.lastSynced', { time: offline.timeSinceSync }) }}
    </span>
  </div>
</template>
