<script setup lang="ts">
import { Check } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

// Round selection checkbox (UC-074), built around a native
// <input type="checkbox"> for assistive-tech support across devices.
// `overlay` sits on imagery (white border + dark backdrop + drop shadow);
// `muted` sits on card background (muted border, transparent) for the
// previews-off favicon swap.
const props = defineProps<{
  checked: boolean
  visible: boolean
  size: number
  checkSize: number
  variant: 'overlay' | 'muted'
}>()

const emit = defineEmits<{
  toggle: [event: MouseEvent]
}>()

const { t } = useI18n()

function onClick(event: MouseEvent) {
  // The parent owns the state (incl. shift-range semantics): suppress the
  // native toggle and re-render from the `checked` prop.
  event.preventDefault()
  event.stopPropagation()
  emit('toggle', event)
}
</script>

<template>
  <span
    class="sel-check"
    :class="[`sel-check--${props.variant}`, { 'is-visible': props.visible, 'is-checked': props.checked }]"
    :style="{ width: `${props.size}px`, height: `${props.size}px` }"
  >
    <input
      type="checkbox"
      class="sel-check-input"
      :checked="props.checked"
      :aria-label="t('batch.toggleSelection')"
      tabindex="-1"
      data-testid="selection-checkbox"
      @click="onClick"
    />
    <Check
      v-if="props.checked"
      :size="props.checkSize"
      :stroke-width="3"
      class="sel-check-mark text-white"
    />
  </span>
</template>

<style scoped>
/* No `position` for the root here on purpose: the consumer positions the
   checkbox (overlay: absolute over the capture; swap: absolute in the
   favicon box). Scoped styles are unlayered and would beat Tailwind's
   layered utilities, so a base `position` would silently override the
   parent's placement. Both consumers position the root, which also anchors
   the input's expanded hit area. */
.sel-check {
  display: grid;
  place-items: center;
  border-radius: 50%;
  opacity: 0;
  transform: scale(0.7);
  pointer-events: none;
  transition: opacity 0.13s, transform 0.13s, background-color 0.13s, border-color 0.13s;
}

/* The input is the interactive element; it draws nothing itself and is
   padded out to a ≥32px hit target around the visible circle. */
.sel-check-input {
  position: absolute;
  inset: -7px;
  margin: 0;
  appearance: none;
  border-radius: 50%;
  cursor: pointer;
}

.sel-check-mark {
  grid-area: 1 / 1;
  pointer-events: none;
}

.sel-check--overlay {
  border: 1.5px solid rgba(255, 255, 255, 0.85);
  background: rgba(14, 16, 20, 0.45);
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.45);
}

.sel-check--muted {
  border: 1.5px solid #6e7480;
  background: transparent;
  box-shadow: none;
}

.sel-check.is-visible {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

.sel-check.is-checked {
  background: var(--color-primary);
  border: 1px solid color-mix(in oklab, var(--color-primary) 92%, white);
}

@media (prefers-reduced-motion: reduce) {
  .sel-check {
    transition: opacity 0.13s;
    transform: none;
  }
}
</style>
