<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCommitInfo } from '@/composables/useCommitInfo'
import { useI18n } from 'vue-i18n'

const version = ref('unknown')
const { t } = useI18n()

onMounted(async () => {
  const info = await useCommitInfo()
  version.value = info.version !== 'unknown' && info.version !== ''
    ? info.version
    : info.commit
})
</script>

<template>
  <div class="flex items-center gap-1">
    <span class="text-xs text-gray-500 opacity-60"> {{ t('app.version') + ': ' + version }}</span>
  </div>
</template>
