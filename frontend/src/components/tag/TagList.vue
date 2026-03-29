<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface Props {
  className?: string
}

defineProps<Props>()
</script>

<template>
  <div :class="className">
    <div class="text-sm text-muted-foreground p-2">{{ t('tag.none') }}</div>
  </div>
</template>
