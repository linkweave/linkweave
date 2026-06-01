<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
import { Eye, EyeOff } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ui = useUiStore()
</script>

<template>
  <button
    type="button"
    class="preview-toggle"
    :class="{ 'is-on': ui.previewsEnabled }"
    :aria-pressed="ui.previewsEnabled"
    :aria-label="ui.previewsEnabled ? t('toolbar.previewsOn') : t('toolbar.previewsOff')"
    :title="ui.previewsEnabled ? t('toolbar.previewsOn') : t('toolbar.previewsOff')"
    data-testid="bookmark-previews-toggle"
    @click="ui.togglePreviewsEnabled()"
  >
    <component :is="ui.previewsEnabled ? Eye : EyeOff" class="h-3.5 w-3.5" />
    <span class="hidden sm:inline">{{ t('toolbar.previews') }}</span>
  </button>
</template>

<style scoped>
.preview-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-muted-foreground);
  transition: color 0.15s, background-color 0.15s, border-color 0.15s;
}

.preview-toggle:hover {
  color: var(--color-foreground);
}

.preview-toggle.is-on {
  border-color: color-mix(in oklab, var(--color-primary) 90%, black);
  background: color-mix(in oklab, var(--color-primary) 13%, transparent);
  color: color-mix(in oklab, var(--color-primary) 65%, white);
}
</style>
