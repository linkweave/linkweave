import type { TagJson } from '@/api/generated'

/**
 * A rule-sourced suggestion (computed client-side from the URL). `existingTagId`
 * is null when the rule names a tag that doesn't exist yet — it'd be created on
 * accept.
 */
export interface RuleSuggestion {
  name: string
  existingTagId: string | null
}

/** A chip rendered in the "From your rules" group. */
export interface RuleChip {
  name: string
  existingTagId: string | null
  /** Color of the existing tag, or null when the tag would be created. */
  color: string | null
}

/** A chip rendered in the "AI suggestions" group — always an existing tag. */
export interface AiChip {
  id: string
  name: string
  color: string
}

const NEUTRAL_DOT = 'var(--color-muted-foreground)'

/**
 * Builds the two display groups for the Suggested tags section, applying the
 * handoff's de-duplication rules:
 *
 * - A tag already applied to the bookmark is never suggested (either source).
 * - If a tag is suggested by both a rule and the model, it shows once under
 *   "From your rules" (rule attribution wins); the AI group drops it.
 *
 * Pure and order-preserving so it can be unit-tested without a component.
 */
export function buildSuggestionGroups(
  ruleSuggestions: RuleSuggestion[],
  aiTags: TagJson[],
  appliedTagIds: ReadonlySet<string>,
  vocab: TagJson[],
): { rules: RuleChip[]; ai: AiChip[] } {
  const appliedNamesLower = new Set(
    vocab
      .filter((t) => appliedTagIds.has(t.id))
      .map((t) => t.data.name.toLowerCase()),
  )
  const colorByNameLower = new Map(
    vocab.map((t) => [t.data.name.toLowerCase(), t.data.color ?? null] as const),
  )

  const rules: RuleChip[] = []
  const ruleNamesLower = new Set<string>()
  for (const s of ruleSuggestions) {
    const lower = s.name.toLowerCase()
    // Skip a rule tag the bookmark already carries (by id or by name).
    if (s.existingTagId && appliedTagIds.has(s.existingTagId)) continue
    if (appliedNamesLower.has(lower)) continue
    if (ruleNamesLower.has(lower)) continue
    ruleNamesLower.add(lower)
    rules.push({
      name: s.name,
      existingTagId: s.existingTagId,
      color: colorByNameLower.get(lower) ?? null,
    })
  }

  const ai: AiChip[] = []
  const seenAi = new Set<string>()
  for (const tag of aiTags) {
    const lower = tag.data.name.toLowerCase()
    if (appliedTagIds.has(tag.id)) continue
    if (appliedNamesLower.has(lower)) continue
    if (ruleNamesLower.has(lower)) continue // rule attribution wins
    if (seenAi.has(lower)) continue
    seenAi.add(lower)
    ai.push({ id: tag.id, name: tag.data.name, color: tag.data.color ?? NEUTRAL_DOT })
  }

  return { rules, ai }
}
