// `property:` operator parsing + matching for the UC-070 / UC-067 search
// integration. The tokenizer doesn't get a dedicated branch — instead a
// `property:` token uses the generic operator shape with a structured
// payload in the value, e.g. `property:status=draft`, `property:priority>3`.
// We parse that payload at match time (and at render time in FilterPill).

import type { BookmarkPropertyValueJson } from '@/api/generated'
import { PropertyType } from '@/api/generated'
import { decodePropertyValue } from './propertyValueMapper'

export type PropertyOp = '=' | '>' | '<' | '>=' | '<='

export interface ParsedProperty {
  /** Lowercased property name as written in the token. */
  key: string
  op: PropertyOp
  /** Raw value as typed — caller compares to the bookmark's decoded value. */
  value: string
}

export interface PropertyDef {
  id: string
  type: PropertyType
}

// `>=` and `<=` MUST appear before the single-char variants so the regex
// engine prefers them when both could match.
const PROPERTY_VALUE_RE = /^([\w-]+)(>=|<=|=|>|<)(.*)$/

export function parsePropertyValue(raw: string): ParsedProperty | null {
  const m = PROPERTY_VALUE_RE.exec(raw)
  if (!m) return null
  const valueRaw = m[3]
  if (valueRaw === undefined) return null
  return {
    key: m[1]!.toLowerCase(),
    op: m[2] as PropertyOp,
    value: valueRaw,
  }
}

/**
 * Match a parsed `property:` operator against a bookmark's property values.
 *
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
