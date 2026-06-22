<script setup lang="ts">
import ButtonLw from './ButtonLw.vue'
import DialogLw from './DialogLw.vue'
import DialogFooterLw from './DialogFooterLw.vue'
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
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ title || t('common.confirm') }}</template>

    <p class="text-sm text-muted-foreground">{{ message }}</p>

    <template #footer>
      <DialogFooterLw>
        <ButtonLw type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonLw>
        <ButtonLw
          type="button"
          data-testid="confirm-dialog-submit"
          :variant="destructive ? 'destructive' : 'default'"
          @click="handleConfirm"
        >
          {{ confirmLabel || t('common.delete') }}
        </ButtonLw>
      </DialogFooterLw>
    </template>
  </DialogLw>
</template>
