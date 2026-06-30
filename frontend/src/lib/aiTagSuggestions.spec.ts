import { describe, expect, it } from 'vitest'
import type { TagJson } from '@/api/generated'
import { buildSuggestionGroups, type RuleSuggestion } from './aiTagSuggestions'

function tag(id: string, name: string, color = '#123456'): TagJson {
  return {
    id,
    entityInfo: {} as TagJson['entityInfo'],
    data: { collectionId: 'c1', name, color },
  }
}

describe('buildSuggestionGroups', () => {
  it('keeps rule and AI groups separate and preserves order', () => {
    const rules: RuleSuggestion[] = [{ name: 'docs', existingTagId: 't-docs' }]
    const ai = [tag('t-rust', 'rust'), tag('t-async', 'async')]
    const vocab = [tag('t-docs', 'docs'), ...ai]

    const { rules: r, ai: a } = buildSuggestionGroups(rules, ai, new Set(), vocab)

    expect(r.map((c) => c.name)).toEqual(['docs'])
    expect(r[0]?.color).toBe('#123456')
    expect(a.map((c) => c.name)).toEqual(['rust', 'async'])
  })

  it('drops an AI tag that a rule already covers (rule attribution wins)', () => {
    const rules: RuleSuggestion[] = [{ name: 'rust', existingTagId: 't-rust' }]
    const ai = [tag('t-rust', 'rust'), tag('t-async', 'async')]
    const vocab = ai

    const { rules: r, ai: a } = buildSuggestionGroups(rules, ai, new Set(), vocab)

    expect(r.map((c) => c.name)).toEqual(['rust'])
    expect(a.map((c) => c.name)).toEqual(['async'])
  })

  it('never suggests a tag already applied to the bookmark (by id or name)', () => {
    const rules: RuleSuggestion[] = [{ name: 'docs', existingTagId: 't-docs' }]
    const ai = [tag('t-rust', 'rust'), tag('t-async', 'async')]
    const vocab = [tag('t-docs', 'docs'), ...ai]
    const applied = new Set(['t-docs', 't-rust'])

    const { rules: r, ai: a } = buildSuggestionGroups(rules, ai, applied, vocab)

    expect(r).toHaveLength(0)
    expect(a.map((c) => c.name)).toEqual(['async'])
  })

  it('dedupes rule names case-insensitively', () => {
    const rules: RuleSuggestion[] = [
      { name: 'Docs', existingTagId: null },
      { name: 'docs', existingTagId: null },
    ]
    const { rules: r } = buildSuggestionGroups(rules, [], new Set(), [])
    expect(r.map((c) => c.name)).toEqual(['Docs'])
  })
})
