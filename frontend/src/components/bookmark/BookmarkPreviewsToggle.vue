<script setup lang="ts">
import ToolbarToggleButton from '@/components/bookmark/ToolbarToggleButton.vue'
import { useUiStore } from '@/stores/ui'
import { Eye, EyeOff } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ui = useUiStore()
</script>

<template>
  <ToolbarToggleButton
    :active="ui.previewsEnabled"
    :aria-label="ui.previewsEnabled ? t('toolbar.previewsOn') : t('toolbar.previewsOff')"
    :title="ui.previewsEnabled ? t('toolbar.previewsOn') : t('toolbar.previewsOff')"
    data-testid="bookmark-previews-toggle"
    @click="ui.togglePreviewsEnabled()"
  >
    <component :is="ui.previewsEnabled ? Eye : EyeOff" class="h-3.5 w-3.5" />
    <span class="hidden sm:inline">{{ t('toolbar.previews') }}</span>
  </ToolbarToggleButton>
</template>
