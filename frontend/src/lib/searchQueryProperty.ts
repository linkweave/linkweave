// `property:` operator parsing + matching for the UC-070 / UC-067 search
// integration. The tokenizer doesn't get a dedicated branch — instead a
// `property:` token uses the generic operator shape with a structured
// payload in the value, e.g. `property:status=draft`, `property:priority>3`.
// We parse that payload at match time (and at render time in FilterPill).

import type { BookmarkPropertyValueJson } from '@/api/generated'
import { PropertyType } from '@/api/generated'
import { decodePropertyValue } from './propertyValueMapper'

export type PropertyOp = '=' | '>' | '<' | '>=' | '<=' | 'exists'

export interface ParsedProperty {
  /** Lowercased property name as written in the token. */
  key: string
  op: PropertyOp
  /** Raw value as typed — caller compares to the bookmark's decoded value.
      Always empty when op === 'exists'. */
  value: string
}

export interface PropertyDef {
  id: string
  type: PropertyType
}

// Two token shapes:
//   `property:status`            → existence check (op === 'exists')
//   `property:status=draft`,     → comparison forms (op === '=' / '>' / ...)
//   `property:priority>3`, ...
// `>=` and `<=` MUST appear before the single-char variants so the regex
// engine prefers them when both could match. The trailing `?` on the
// operator+value group makes the bare-key shape legal.
const PROPERTY_VALUE_RE = /^([\w-]+)(?:(>=|<=|=|>|<)(.*))?$/

export function parsePropertyValue(raw: string): ParsedProperty | null {
  const m = PROPERTY_VALUE_RE.exec(raw)
  if (!m) return null
  const key = m[1]!.toLowerCase()
  const op = m[2]
  if (op === undefined) {
    return { key, op: 'exists', value: '' }
  }
  return {
    key,
    op: op as Exclude<PropertyOp, 'exists'>,
    value: m[3] ?? '',
  }
}

/**
 * Match a parsed `property:` operator against a bookmark's property values.
 *
 *   - `exists`          → bookmark has any non-empty value for this property
 *   - `=`              → case-insensitive string equality on the decoded value
 *                        (multi-select compares the canonical CSV form)
 *   - `>` `<` `>=` `<=` → numeric comparison; non-numeric operands → no match
 *
 * Unknown property names → no match (consistent with "you typed a property that
 * isn't defined on this collection"). The matcher conventionally returns true
 * for unknown *operators* but we treat property names more strictly because
 * silently matching everything would be more confusing than a transparent miss.
 */
export function matchesPropertyToken(
  values: BookmarkPropertyValueJson[] | undefined,
  defsByName: Map<string, PropertyDef>,
  parsed: ParsedProperty,
): boolean {
  const def = defsByName.get(parsed.key)
  if (!def) return false
  const entry = (values ?? []).find(v => v.definitionId === def.id)
  if (!entry) return false

  const decoded = decodePropertyValue(def.type, entry)
  if (decoded === undefined) return false

  if (parsed.op === 'exists') {
    // "any value set". Treat empty strings and empty arrays as not-set so
    // existence semantics line up with the badge-visibility rules on cards.
    if (typeof decoded === 'string') return decoded !== ''
    if (Array.isArray(decoded)) return decoded.length > 0
    return true
  }

  // Equality: stringify both sides (the wire only has primitives + CSV) and
  // case-insensitive compare. Numbers compare numerically when both sides
  // parse cleanly.
  if (parsed.op === '=') {
    const left = stringifyDecodedValue(decoded)
    return left.toLowerCase() === parsed.value.toLowerCase()
  }

  const left = Number(stringifyDecodedValue(decoded))
  const right = Number(parsed.value)
  if (Number.isNaN(left) || Number.isNaN(right)) return false
  if (parsed.op === '>') return left > right
  if (parsed.op === '<') return left < right
  if (parsed.op === '>=') return left >= right
  if (parsed.op === '<=') return left <= right
  return false
}

function stringifyDecodedValue(v: string | number | boolean | string[]): string {
  if (Array.isArray(v)) return v.join(',')
  return String(v)
}
