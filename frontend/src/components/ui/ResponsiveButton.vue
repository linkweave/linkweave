<script setup lang="ts">
import { computed, useAttrs } from 'vue'
import ButtonLw from './ButtonLw.vue'

type Variant = 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'

interface Props {
  label: string
  variant?: Variant
  disabled?: boolean
}

defineOptions({ inheritAttrs: false })

withDefaults(defineProps<Props>(), { variant: 'default' })

const attrs = useAttrs()

// data-testid goes only on the desktop button so strict-mode selectors don't match hidden elements
const mobileAttrs = computed(() => {
  const { 'data-testid': _omit, ...rest } = attrs as Record<string, unknown>
  return rest
})
</script>

<template>
  <ButtonLw v-bind="mobileAttrs" size="icon" :variant="variant" :disabled="disabled" :aria-label="label" class="sm:hidden">
    <slot />
  </ButtonLw>
  <ButtonLw v-bind="attrs" size="sm" :variant="variant" :disabled="disabled" class="hidden sm:inline-flex">
    <slot />
    {{ label }}
  </ButtonLw>
</template>
