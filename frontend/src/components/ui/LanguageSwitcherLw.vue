<script setup lang="ts">
import { Languages } from '@lucide/vue'
import ButtonLw from './ButtonLw.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-1">
    <ButtonLw variant="ghost" size="sm" class="gap-2">
      <Languages class="h-4 w-4" />
      <span class="hidden sm:inline">{{ t('language.en') }}</span>
    </ButtonLw>
  </div>
</template>
