<script setup lang="ts">
import ToolbarToggleButton from '@/components/bookmark/ToolbarToggleButton.vue'
import { useSelectionStore } from '@/stores/selection'
import { Check, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const selection = useSelectionStore()

function onClick() {
  if (selection.selecting) {
    selection.clearAndExit()
  } else {
    selection.enterMode()
  }
}
</script>

<template>
  <ToolbarToggleButton
    :active="selection.selecting"
    data-testid="bookmark-select-toggle"
    @click="onClick"
  >
    <component :is="selection.selecting ? X : Check" class="h-[13px] w-[13px]" />
    <span class="hidden sm:inline">{{
      selection.selecting ? t('batch.cancel') : t('batch.select')
    }}</span>
  </ToolbarToggleButton>
</template>
