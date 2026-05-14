<script setup lang="ts">
import { Clock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

defineProps<{ count: number }>()
const { t } = useI18n()
</script>

<template>
  <div
    class="col-span-full my-4 flex items-center gap-3 text-xs text-muted-foreground"
    role="separator"
    data-testid="never-opened-divider"
  >
    <div class="h-px flex-1 bg-border" />
    <div class="inline-flex items-center gap-1.5 rounded-full border border-dashed border-border bg-card px-3 py-1">
      <Clock class="h-3 w-3" />
      <span>{{ t('sort.neverOpened', { n: count }) }}</span>
    </div>
    <div class="h-px flex-1 bg-border" />
  </div>
</template>
