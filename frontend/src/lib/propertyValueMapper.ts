// Property-value codec: converts between the wire-shape
// `BookmarkPropertyValueJson` (which has separate text/number/boolean slots)
// and a form-friendly primitive per property type.
//
// Form-side representation per type:
//   TEXT          → string         (uses valueText)
//   DATE          → string (ISO)   (uses valueText, format YYYY-MM-DD)
//   SELECT        → string         (uses valueText, one of allowedValues)
//   MULTI_SELECT  → string[]       (uses valueText as a comma-separated list)
//   NUMBER        → number         (uses valueNumber)
//   BOOLEAN       → boolean        (uses valueBoolean)
//
// An "unset" value is encoded as `undefined` on the form side and as the
// absence of an entry on the wire side (no `BookmarkPropertyValueJson` is
// emitted for a definition the user has not assigned).
//
// Boolean `false` is a legitimate value, not "unset". The caller decides
// whether they want a Clear button that removes the entry entirely.

import type { BookmarkPropertyValueJson } from '@/api/generated'
import { PropertyType } from '@/api/generated'

export type PropertyFormValue = string | number | boolean | string[] | undefined

const MULTI_SELECT_SEPARATOR = ','

export function decodePropertyValue(
  type: PropertyType,
  value: BookmarkPropertyValueJson | undefined,
): PropertyFormValue {
  if (!value) return undefined
  switch (type) {
    case PropertyType.Number:
      return value.valueNumber
    case PropertyType.Boolean:
      return value.valueBoolean
    case PropertyType.MultiSelect:
      return (value.valueText ?? '')
        .split(MULTI_SELECT_SEPARATOR)
        .map(s => s.trim())
        .filter(Boolean)
    case PropertyType.Text:
    case PropertyType.Select:
    case PropertyType.Date:
      return value.valueText
  }
}

/**
 * Build the wire payload for one property. Returns `null` to signal "do not
 * persist this property" — used when the form value is empty/unset.
 */
export function encodePropertyValue(
  definitionId: string,
  type: PropertyType,
  value: PropertyFormValue,
): BookmarkPropertyValueJson | null {
  if (value === undefined || value === null) return null
  switch (type) {
    case PropertyType.Text:
    case PropertyType.Date:
    case PropertyType.Select:
      if (typeof value !== 'string' || value === '') return null
      return { definitionId, valueText: value }
    case PropertyType.MultiSelect:
      if (!Array.isArray(value) || value.length === 0) return null
      return { definitionId, valueText: value.join(MULTI_SELECT_SEPARATOR) }
    case PropertyType.Number:
      if (typeof value !== 'number' || Number.isNaN(value)) return null
      return { definitionId, valueNumber: value }
    case PropertyType.Boolean:
      // false is a real value, not "cleared". Caller removes via undefined.
      if (typeof value !== 'boolean') return null
      return { definitionId, valueBoolean: value }
  }
}

/**
 * Convenience: encode an entire `definitionId → formValue` map into the list
 * payload, dropping entries that encode to null.
 */
export function encodePropertyValueMap(
  values: Map<string, PropertyFormValue>,
  typesByDefinitionId: Map<string, PropertyType>,
): BookmarkPropertyValueJson[] {
  const out: BookmarkPropertyValueJson[] = []
  for (const [definitionId, raw] of values) {
    const type = typesByDefinitionId.get(definitionId)
    if (!type) continue
    const encoded = encodePropertyValue(definitionId, type, raw)
    if (encoded) out.push(encoded)
  }
  return out
}
