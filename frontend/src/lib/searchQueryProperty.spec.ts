import { PropertyType } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import { matchesPropertyToken, parsePropertyValue } from './searchQueryProperty'

describe('parsePropertyValue', () => {
  it('parses key=value', () => {
    expect(parsePropertyValue('status=draft')).toEqual({
      key: 'status',
      op: '=',
      value: 'draft',
    })
  })

  it('parses numeric comparators, preferring longer ones', () => {
    expect(parsePropertyValue('priority>3')?.op).toBe('>')
    expect(parsePropertyValue('priority<3')?.op).toBe('<')
    expect(parsePropertyValue('priority>=3')?.op).toBe('>=')
    expect(parsePropertyValue('priority<=3')?.op).toBe('<=')
  })

  it('lowercases the key', () => {
    expect(parsePropertyValue('Status=Draft')?.key).toBe('status')
    expect(parsePropertyValue('Status=Draft')?.value).toBe('Draft')
  })

  it('accepts hyphen and underscore in the key', () => {
    expect(parsePropertyValue('needs-review=true')?.key).toBe('needs-review')
    expect(parsePropertyValue('my_field=x')?.key).toBe('my_field')
  })

  it('parses a bare key as an existence check', () => {
    expect(parsePropertyValue('status')).toEqual({ key: 'status', op: 'exists', value: '' })
    expect(parsePropertyValue('My-Prop_X')).toEqual({
      key: 'my-prop_x',
      op: 'exists',
      value: '',
    })
  })

  it('returns null when the key side is missing entirely', () => {
    expect(parsePropertyValue('=value')).toBeNull()
    expect(parsePropertyValue('')).toBeNull()
  })

  it('treats empty value as a valid empty-string match (parses; matcher decides)', () => {
    expect(parsePropertyValue('status=')).toEqual({ key: 'status', op: '=', value: '' })
  })
})

describe('matchesPropertyToken', () => {
  const defsByName = new Map([
    ['status', { id: 'd-status', type: PropertyType.Select }],
    ['priority', { id: 'd-priority', type: PropertyType.Number }],
    ['done', { id: 'd-done', type: PropertyType.Boolean }],
    ['tags', { id: 'd-tags', type: PropertyType.MultiSelect }],
  ])

  it('returns false for a property not defined on the collection', () => {
    expect(matchesPropertyToken([], defsByName, { key: 'unknown', op: '=', value: 'x' })).toBe(
      false,
    )
  })

  it('returns false for a bookmark that has no value for the property', () => {
    expect(matchesPropertyToken([], defsByName, { key: 'status', op: '=', value: 'draft' })).toBe(
      false,
    )
  })

  it('matches case-insensitive equality on SELECT values', () => {
    const values = [{ definitionId: 'd-status', valueText: 'Draft' }]
    expect(
      matchesPropertyToken(values, defsByName, { key: 'status', op: '=', value: 'draft' }),
    ).toBe(true)
    expect(
      matchesPropertyToken(values, defsByName, { key: 'status', op: '=', value: 'review' }),
    ).toBe(false)
  })

  it('matches BOOLEAN as the literal "true" / "false"', () => {
    const values = [{ definitionId: 'd-done', valueBoolean: true }]
    expect(matchesPropertyToken(values, defsByName, { key: 'done', op: '=', value: 'true' })).toBe(
      true,
    )
    expect(matchesPropertyToken(values, defsByName, { key: 'done', op: '=', value: 'false' })).toBe(
      false,
    )
  })

  it('matches MULTI_SELECT against the canonical comma-joined form', () => {
    const values = [{ definitionId: 'd-tags', valueText: 'red,blue' }]
    expect(
      matchesPropertyToken(values, defsByName, { key: 'tags', op: '=', value: 'red,blue' }),
    ).toBe(true)
  })

  it('compares numerically for >, <, >=, <=', () => {
    const values = [{ definitionId: 'd-priority', valueNumber: 5 }]
    expect(matchesPropertyToken(values, defsByName, { key: 'priority', op: '>', value: '3' })).toBe(
      true,
    )
    expect(matchesPropertyToken(values, defsByName, { key: 'priority', op: '>', value: '5' })).toBe(
      false,
    )
    expect(
      matchesPropertyToken(values, defsByName, { key: 'priority', op: '>=', value: '5' }),
    ).toBe(true)
    expect(matchesPropertyToken(values, defsByName, { key: 'priority', op: '<', value: '5' })).toBe(
      false,
    )
    expect(
      matchesPropertyToken(values, defsByName, { key: 'priority', op: '<=', value: '5' }),
    ).toBe(true)
  })

  it('returns false when a numeric comparator gets non-numeric operands', () => {
    const values = [{ definitionId: 'd-status', valueText: 'draft' }]
    expect(matchesPropertyToken(values, defsByName, { key: 'status', op: '>', value: '3' })).toBe(
      false,
    )
  })

  describe('exists', () => {
    it('matches when the bookmark has a non-empty value of any type', () => {
      expect(
        matchesPropertyToken(
          [{ definitionId: 'd-status', valueText: 'draft' }],
          defsByName,
          { key: 'status', op: 'exists', value: '' },
        ),
      ).toBe(true)
      expect(
        matchesPropertyToken(
          [{ definitionId: 'd-priority', valueNumber: 0 }],
          defsByName,
          { key: 'priority', op: 'exists', value: '' },
        ),
      ).toBe(true)
      expect(
        matchesPropertyToken(
          [{ definitionId: 'd-done', valueBoolean: false }],
          defsByName,
          { key: 'done', op: 'exists', value: '' },
        ),
      ).toBe(true)
    })

    it('does not match when the property entry is absent', () => {
      expect(
        matchesPropertyToken([], defsByName, { key: 'status', op: 'exists', value: '' }),
      ).toBe(false)
    })

    it('does not match when the stored value is empty (string or multi-select)', () => {
      expect(
        matchesPropertyToken(
          [{ definitionId: 'd-status', valueText: '' }],
          defsByName,
          { key: 'status', op: 'exists', value: '' },
        ),
      ).toBe(false)
      expect(
        matchesPropertyToken(
          [{ definitionId: 'd-tags', valueText: '' }],
          defsByName,
          { key: 'tags', op: 'exists', value: '' },
        ),
      ).toBe(false)
    })
  })
})
