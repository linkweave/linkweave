<script setup lang="ts">
import type { BookmarkPropertyValueJson, PropertyDefinitionJson } from '@/api/generated'
import { PropertyType } from '@/api/generated'
import { decodePropertyValue } from '@/lib/propertyValueMapper'
import { computed } from 'vue'

// Inline `key=value` chip rendered on a bookmark card. The display value is
// derived from the definition's type:
//   BOOLEAN → "true" / "false"
//   DATE    → localized short date (day + month)
//   others  → string form of the decoded value
//
// `active` is a parent-driven prop — the card already knows whether the
// matching `property:key=value` token sits in the search query, so we don't
// re-derive it here.

const props = defineProps<{
  propDef: PropertyDefinitionJson
  value: BookmarkPropertyValueJson | undefined
  active?: boolean
}>()

defineEmits<{
  click: []
}>()

const decoded = computed(() => decodePropertyValue(props.propDef.data.type, props.value))

const displayValue = computed<string>(() => {
  const v = decoded.value
  if (v === undefined) return ''
  if (props.propDef.data.type === PropertyType.Boolean) {
    return v ? 'true' : 'false'
  }
  if (props.propDef.data.type === PropertyType.Date && typeof v === 'string') {
    const parsed = new Date(v)
    if (Number.isNaN(parsed.getTime())) return v
    return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
  }
  if (Array.isArray(v)) return v.join(', ')
  return String(v)
})

const isBooleanTrue = computed(
  () => props.propDef.data.type === PropertyType.Boolean && decoded.value === true,
)
</script>

<template>
  <button
    type="button"
    class="inline-flex items-center rounded-[5px] text-[11px] px-2 py-0.5 border bg-secondary transition-colors"
    :class="[
      // Active filter takes precedence over the boolean-true variant —
      // saturated purple border + tinted bg matches the design's
      // `.prop-chip.active`. Hover state on non-active chips tints purple
      // at lower intensity so the affordance flows into the active look.
      active
        ? 'border-[var(--color-property)] bg-[color-mix(in_oklab,var(--color-property)_12%,var(--color-secondary))] text-foreground'
        : isBooleanTrue
          ? 'border-[color-mix(in_oklab,#22c55e_35%,var(--color-border))] bg-[color-mix(in_oklab,#22c55e_7%,var(--color-secondary))]'
          : 'border-border hover:border-[color-mix(in_oklab,var(--color-property)_55%,var(--color-border))] hover:bg-[color-mix(in_oklab,var(--color-property)_9%,var(--color-secondary))]',
    ]"
    :data-testid="`card-property-badge-${propDef.data.name}`"
    :data-active="active ? 'true' : 'false'"
    :title="`Filter by ${propDef.data.name}=${displayValue}`"
    @click.stop="$emit('click')"
  >
    <span class="font-mono text-muted-foreground mr-[3px]">{{ propDef.data.name }}</span>
    <span class="text-muted-foreground/70 mx-[2px]">=</span>
    <span class="font-mono text-foreground font-medium">{{ displayValue }}</span>
  </button>
</template>
