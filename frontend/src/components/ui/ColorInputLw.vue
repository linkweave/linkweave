<script setup lang="ts">
import type { InputHTMLAttributes } from 'vue'
import { computed, ref } from 'vue'
import { TwitterPicker } from 'vue-color'

interface Props {
  modelValue?: string
  inputId?: string
  placeholder?: string
  attrs?: InputHTMLAttributes & Record<string, unknown>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

const presets = [
  '#ef4444', '#f97316', '#eab308', '#22c55e', '#06b6d4',
  '#3b82f6', '#8b5cf6', '#ec4899', '#64748b', '#14b8a6',
  '#f43f5e', '#a855f7', '#84cc16', '#0ea5e9', '#d946ef',
  '#f59e0b',
]

const pickerColor = computed({
  get: () => props.modelValue ?? '#ffffff',
  set: (val: string) => emit('update:modelValue', val || undefined),
})

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value || undefined)
  if (typeof props.attrs?.onInput === 'function') {
    ;(props.attrs.onInput as (e: Event) => void)(event)
  }
}

const pickerOpen = ref(false)
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-2">
      <input
        :id="inputId"
        :value="modelValue"
        type="text"
        maxlength="7"
        :placeholder="placeholder ?? 'e.g. #ef1111'"
        class="flex h-9 w-full rounded-md border border-input bg-input px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        v-bind="attrs"
        @input="onInput($event)"
      />
      <span
        class="h-9 w-9 shrink-0 rounded-md border border-input cursor-pointer hover:ring-2 hover:ring-ring transition-shadow"
        :class="modelValue ? '' : 'bg-muted-foreground/20'"
        :style="modelValue ? { backgroundColor: modelValue } : undefined"
        @click="pickerOpen = !pickerOpen"
      />
    </div>
    <template v-if="pickerOpen">
      <TwitterPicker
      v-model="pickerColor"
      :preset-colors="presets"
      :width="'100%'"
      triangle="hide"
    />
    <button
      v-if="modelValue"
      type="button"
      class="text-xs text-muted-foreground hover:text-foreground transition-colors"
      @click="emit('update:modelValue', undefined)"
    >
      {{ $t('common.reset') }}
    </button>
    </template>
  </div>
</template>

<style scoped>
:deep(.vc-twitter-picker) {
  --vc-body-bg: var(--color-card);
  --vc-twitter-input-bg: var(--color-input);
  --vc-twitter-input-border: var(--color-input);
  --vc-twitter-input-color: var(--color-foreground);
  --vc-twitter-hash-bg: var(--color-muted);
  --vc-twitter-hash-color: var(--color-muted-foreground);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.1);
}
</style>
