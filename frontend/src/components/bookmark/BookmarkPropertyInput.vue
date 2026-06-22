<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { InputLw, SelectLw, SwitchLw } from '@/components/ui'
import { PropertyType } from '@/api/generated'
import type { PropertyDefinitionJson } from '@/api/generated'
import type { PropertyFormValue } from '@/lib/propertyValueMapper'

// One row that renders the correct form control based on `propDef.data.type`
// and emits the new value through `update:modelValue`. The parent (the edit
// dialog) holds the canonical map of values keyed by definition id; this
// component only owns the input bindings.
//
// `clear` is a separate emit so the parent can distinguish "user wants this
// removed entirely" from "user set this to false / empty string" — important
// for booleans where false is a real value.

const props = defineProps<{
  propDef: PropertyDefinitionJson
  modelValue: PropertyFormValue
}>()

const emit = defineEmits<{
  'update:modelValue': [value: PropertyFormValue]
  clear: []
}>()

const { t } = useI18n()

// `allowedValues` is a CSV string on the wire — split it lazily for renders.
const options = computed<string[]>(() =>
  (props.propDef.data.allowedValues ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
)

const hasValue = computed(() => {
  const v = props.modelValue
  if (v === undefined || v === null) return false
  if (typeof v === 'string') return v !== ''
  if (Array.isArray(v)) return v.length > 0
  return true // numbers and booleans always count as "set" once assigned
})

// --- per-type binding helpers ---------------------------------------------

function asString(): string {
  return typeof props.modelValue === 'string' ? props.modelValue : ''
}
function asNumber(): number | undefined {
  return typeof props.modelValue === 'number' ? props.modelValue : undefined
}
function asBoolean(): boolean {
  return typeof props.modelValue === 'boolean' ? props.modelValue : false
}
function asArray(): string[] {
  return Array.isArray(props.modelValue) ? props.modelValue : []
}

function onText(value: string) {
  emit('update:modelValue', value === '' ? undefined : value)
}
function onNumber(raw: string) {
  if (raw === '') {
    emit('update:modelValue', undefined)
    return
  }
  const n = Number(raw)
  emit('update:modelValue', Number.isNaN(n) ? undefined : n)
}
function onBoolean(value: boolean) {
  emit('update:modelValue', value)
}
function onSelect(value: string) {
  emit('update:modelValue', value === '' ? undefined : value)
}
function toggleMulti(option: string) {
  const current = asArray()
  const next = current.includes(option) ? current.filter((o) => o !== option) : [...current, option]
  emit('update:modelValue', next.length > 0 ? next : undefined)
}
</script>

<template>
  <div class="space-y-1.5" :data-testid="`property-input-${propDef.data.name}`">
    <div class="flex items-center justify-between gap-2">
      <label
        class="block text-xs font-medium leading-none font-mono"
        :for="`property-input-${propDef.id}`"
      >
        {{ propDef.data.name }}
      </label>
      <button
        v-if="hasValue"
        type="button"
        class="text-[10px] text-muted-foreground hover:text-foreground transition-colors"
        :data-testid="`property-clear-${propDef.data.name}`"
        @click="emit('clear')"
      >
        {{ t('property.clearValue') }}
      </button>
    </div>

    <!-- TEXT -->
    <InputLw
      v-if="propDef.data.type === PropertyType.Text"
      :id="`property-input-${propDef.id}`"
      type="text"
      :model-value="asString()"
      @update:model-value="(v: string) => onText(v)"
    />

    <!-- NUMBER -->
    <InputLw
      v-else-if="propDef.data.type === PropertyType.Number"
      :id="`property-input-${propDef.id}`"
      type="number"
      :model-value="asNumber() ?? ''"
      @update:model-value="(v: string) => onNumber(v)"
    />

    <!-- BOOLEAN -->
    <div v-else-if="propDef.data.type === PropertyType.Boolean" class="flex items-center gap-2">
      <SwitchLw
        :model-value="asBoolean()"
        :aria-label="propDef.data.name"
        @update:model-value="onBoolean"
      />
      <span class="text-xs text-muted-foreground">
        {{ asBoolean() ? t('common.yes') : t('common.no') }}
      </span>
    </div>

    <!-- SELECT -->
    <SelectLw
      v-else-if="propDef.data.type === PropertyType.Select"
      :id="`property-input-${propDef.id}`"
      :model-value="asString()"
      @update:model-value="(v: any) => onSelect(String(v))"
    >
      <option value="">{{ t('property.notSet') }}</option>
      <option v-for="opt in options" :key="opt" :value="opt">{{ opt }}</option>
    </SelectLw>

    <!-- MULTI_SELECT -->
    <div v-else-if="propDef.data.type === PropertyType.MultiSelect" class="flex flex-wrap gap-1.5">
      <button
        v-for="opt in options"
        :key="opt"
        type="button"
        class="px-2.5 py-1 text-[11.5px] rounded border transition-colors"
        :class="
          asArray().includes(opt)
            ? 'bg-primary/10 border-primary text-primary'
            : 'bg-secondary border-border text-muted-foreground hover:border-primary/40 hover:text-foreground'
        "
        @click="toggleMulti(opt)"
      >
        {{ opt }}
      </button>
    </div>

    <!-- DATE -->
    <InputLw
      v-else-if="propDef.data.type === PropertyType.Date"
      :id="`property-input-${propDef.id}`"
      type="date"
      :model-value="asString()"
      @update:model-value="(v: string) => onText(v as string)"
    />
  </div>
</template>
