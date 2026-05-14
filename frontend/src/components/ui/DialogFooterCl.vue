<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ButtonCl from './ButtonCl.vue'

type ButtonVariant = 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'

const props = withDefaults(
  defineProps<{
    /** Label on the primary submit button — e.g. t('common.create'). */
    submitLabel: string
    /** When true, the submit button is disabled and shows `loadingLabel` instead of `submitLabel`. */
    submitting?: boolean
    /** Extra disabled predicate, ORed with `submitting` (e.g. "name typed doesn't match"). */
    submitDisabled?: boolean
    /** Variant for the submit button. */
    submitVariant?: ButtonVariant
    /** Optional data-testid forwarded onto the submit button. */
    submitTestid?: string
    /** Label shown on the submit button while `submitting` is true. Defaults to t('common.loading'). */
    loadingLabel?: string
    /** Label on the cancel button. Defaults to t('common.cancel'). */
    cancelLabel?: string
  }>(),
  {
    submitting: false,
    submitDisabled: false,
    submitVariant: 'default',
  },
)

const emit = defineEmits<{ cancel: [] }>()

const { t } = useI18n()

const submitText = computed(() =>
  props.submitting ? (props.loadingLabel ?? t('common.loading')) : props.submitLabel,
)
const cancelText = computed(() => props.cancelLabel ?? t('common.cancel'))
const submitDisabledFinal = computed(() => props.submitting || props.submitDisabled)
</script>

<template>
  <div class="flex justify-end gap-2">
    <ButtonCl type="button" variant="outline" @click="emit('cancel')">
      {{ cancelText }}
    </ButtonCl>
    <ButtonCl
      type="submit"
      :variant="submitVariant"
      :disabled="submitDisabledFinal"
      :data-testid="submitTestid"
    >
      {{ submitText }}
    </ButtonCl>
  </div>
</template>
