<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ButtonLw from './ButtonLw.vue'

type ButtonVariant = 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'

// `DialogFooterLw` does two jobs:
//   1. Renders the bg-card / border-t footer chrome.
//   2. Optionally renders a default Cancel + Submit button pair for form
//      dialogs that use the convention.
//
// Dialogs that want a different button arrangement (e.g. a single "Done"
// button on a CRUD-immediate modal) pass their own content into the default
// slot — the chrome stays consistent across every dialog. The submit-/cancel-
// related props are only consulted when no slot content is provided.
//
// Since DialogLw now renders the footer in its own grid row (outside the
// scrollable body), this component is no longer sticky — its position is
// determined by the parent layout.

const props = withDefaults(
  defineProps<{
    /** Label on the primary submit button — e.g. t('common.create'). */
    submitLabel?: string
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
    /**
     * ID of the form this footer's submit button should submit. Required
     * when the footer lives in DialogLw's `#footer` slot — the form is
     * inside the scrollable body slot and the submit button needs the
     * HTML5 `form` attribute to associate with it.
     */
    submitForm?: string
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
  props.submitting
    ? (props.loadingLabel ?? t('common.loading'))
    : (props.submitLabel ?? t('common.save')),
)
const cancelText = computed(() => props.cancelLabel ?? t('common.cancel'))
const submitDisabledFinal = computed(() => props.submitting || props.submitDisabled)
</script>

<template>
  <!-- Footer chrome: top border to separate from the body, `bg-card` to
       carry the dialog's elevated tone, `py-3` for breathing room. Lives in
       DialogLw's `#footer` grid row, which is the row below the scrollable
       body — so no sticky / negative-margin gymnastics needed. -->
  <div class="px-4 sm:px-6 py-3 bg-card border-t border-border flex justify-end gap-2">
    <slot>
      <ButtonLw type="button" variant="outline" @click="emit('cancel')">
        {{ cancelText }}
      </ButtonLw>
      <ButtonLw
        type="submit"
        :variant="submitVariant"
        :disabled="submitDisabledFinal"
        :data-testid="submitTestid"
        :form="submitForm"
      >
        {{ submitText }}
      </ButtonLw>
    </slot>
  </div>
</template>
