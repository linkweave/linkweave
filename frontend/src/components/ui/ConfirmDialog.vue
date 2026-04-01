<script setup lang="ts">
import { DialogCl, ButtonCl } from '@/components/ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface Props {
  open?: boolean
  title?: string
  message: string
  confirmLabel?: string
  destructive?: boolean
}

withDefaults(defineProps<Props>(), {
  title: '',
  confirmLabel: '',
  destructive: true,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirmed: []
}>()

function handleConfirm() {
  emit('confirmed')
  emit('update:open', false)
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ title || t('common.confirm') }}</template>

    <p class="text-sm text-muted-foreground">{{ message }}</p>

    <div class="flex justify-end gap-2 mt-4">
      <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
        {{ t('common.cancel') }}
      </ButtonCl>
      <ButtonCl
        type="button"
        :variant="destructive ? 'destructive' : 'default'"
        @click="handleConfirm"
      >
        {{ confirmLabel || t('common.delete') }}
      </ButtonCl>
    </div>
  </DialogCl>
</template>
