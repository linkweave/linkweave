<script setup lang="ts">
// Pill-style toggle. 34×19 px track with a 13 px thumb that slides 15 px when
// checked, matching the UC-067 prototype spec. Intentionally not a wrapper
// around `<input type="checkbox">` for two reasons: (1) we control the visual
// completely, (2) keyboard activation via Space/Enter is handled natively by
// the underlying button. Use this for true on/off preferences — checkboxes
// remain appropriate for "select N of many" lists.

defineProps<{
  modelValue: boolean
  ariaLabel?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function toggle(current: boolean, disabled: boolean | undefined) {
  if (disabled) return
  emit('update:modelValue', !current)
}
</script>

<template>
  <button
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :aria-label="ariaLabel"
    :disabled="disabled"
    class="relative inline-flex h-[19px] w-[34px] shrink-0 cursor-pointer items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background"
    :class="modelValue ? 'bg-primary' : 'bg-border'"
    @click="toggle(modelValue, disabled)"
  >
    <span
      class="inline-block h-[13px] w-[13px] rounded-full bg-white shadow-sm transition-transform"
      :class="modelValue ? 'translate-x-[18px]' : 'translate-x-[3px]'"
    />
  </button>
</template>
