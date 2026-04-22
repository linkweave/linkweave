<script setup lang="ts">
import type { InputHTMLAttributes } from 'vue'

interface Props {
  modelValue?: string
  inputId?: string
  placeholder?: string
  attrs?: InputHTMLAttributes & Record<string, unknown>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value)
  if (typeof props.attrs?.onInput === 'function') {
    ;(props.attrs.onInput as (e: Event) => void)(event)
  }
}
</script>

<template>
  <div class="flex items-center gap-2">
    <input
      :id="inputId"
      :value="modelValue"
      type="text"
      maxlength="7"
      :placeholder="placeholder ?? 'e.g. #ef1111'"
      class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      v-bind="attrs"
      @input="onInput($event)"
    />
    <span
      v-if="modelValue"
      class="h-9 w-9 shrink-0 rounded-md border border-input"
      :style="{ backgroundColor: modelValue }"
    />
  </div>
</template>
