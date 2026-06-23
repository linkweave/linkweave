import { describe, expect, it } from 'vitest'
import {
  ariaCheckedFor,
  baseState,
  buildToastMessage,
  changeSummary,
  computeChanges,
  markFor,
  nextIntent,
  type ChangeItem,
  type Intent,
  type TagState,
} from './batchTagModel'

describe('baseState', () => {
  it('classifies all / some / none', () => {
    expect(baseState(0, 3)).toBe('none')
    expect(baseState(3, 3)).toBe('all')
    expect(baseState(1, 3)).toBe('some')
  })

  it('treats a single selection as only all or none', () => {
    expect(baseState(1, 1)).toBe('all')
    expect(baseState(0, 1)).toBe('none')
  })
})

describe('nextIntent (click cycle)', () => {
  it('cycles an on-all tag leave → remove → leave', () => {
    expect(nextIntent('all', undefined)).toBe('remove')
    expect(nextIntent('all', 'remove')).toBeUndefined()
  })

  it('cycles an on-none tag leave → add → leave', () => {
    expect(nextIntent('none', undefined)).toBe('add')
    expect(nextIntent('none', 'add')).toBeUndefined()
  })

  it('cycles a partial tag leave → add → remove → leave', () => {
    expect(nextIntent('some', undefined)).toBe('add')
    expect(nextIntent('some', 'add')).toBe('remove')
    expect(nextIntent('some', 'remove')).toBeUndefined()
  })
})

describe('markFor', () => {
  it('lets a staged intent override the base', () => {
    expect(markFor('none', 'add')).toBe('check')
    expect(markFor('all', 'remove')).toBe('empty-x')
  })

  it('reflects the base when nothing is staged', () => {
    expect(markFor('all', undefined)).toBe('check')
    expect(markFor('some', undefined)).toBe('dash')
    expect(markFor('none', undefined)).toBe('empty')
  })
})

describe('ariaCheckedFor', () => {
  it('maps the tri-state to aria-checked', () => {
    expect(ariaCheckedFor('all', undefined)).toBe('true')
    expect(ariaCheckedFor('none', undefined)).toBe('false')
    expect(ariaCheckedFor('some', undefined)).toBe('mixed')
    expect(ariaCheckedFor('none', 'add')).toBe('true')
    expect(ariaCheckedFor('all', 'remove')).toBe('false')
  })
})

describe('computeChanges', () => {
  const tags: TagState[] = [
    { id: 'all', name: 'java', isNew: false, count: 3 },
    { id: 'some', name: 'ops', isNew: false, count: 1 },
    { id: 'none', name: 'design', isNew: false, count: 0 },
  ]

  it('computes affected counts (k) for adds and removes', () => {
    const draft: Record<string, Intent> = { none: 'add', some: 'remove' }
    const { adds, removes } = computeChanges(draft, tags, 3)
    expect(adds).toEqual([{ id: 'none', name: 'design', isNew: false, k: 3 }])
    expect(removes).toEqual([{ id: 'some', name: 'ops', isNew: false, k: 1 }])
  })

  it('drops zero-effect drafts (add already-on-all / remove on-none)', () => {
    const draft: Record<string, Intent> = { all: 'add', none: 'remove' }
    const { adds, removes } = computeChanges(draft, tags, 3)
    expect(adds).toEqual([])
    expect(removes).toEqual([])
  })

  it('ignores drafts for tags not in the universe', () => {
    const { adds } = computeChanges({ ghost: 'add' }, tags, 3)
    expect(adds).toEqual([])
  })
})

describe('changeSummary', () => {
  it('joins adds then removes with +/− signs', () => {
    const adds: ChangeItem[] = [{ id: 'a', name: 'java', isNew: false, k: 2 }]
    const removes: ChangeItem[] = [{ id: 'b', name: 'red', isNew: false, k: 1 }]
    expect(changeSummary(adds, removes)).toBe('+java  −red')
  })

  it('is empty when there are no changes', () => {
    expect(changeSummary([], [])).toBe('')
  })
})

describe('buildToastMessage', () => {
  // Mirrors the en.json clauses so the assembled sentence is realistic.
  const translate = (key: 'toastAdded' | 'toastRemoved', p: { names: string; k: number }) =>
    key === 'toastAdded' ? `Added ${p.names} to ${p.k}` : `removed ${p.names} from ${p.k}`

  it('formats an adds-only message with the max affected count', () => {
    const adds: ChangeItem[] = [
      { id: '1', name: 'java', isNew: false, k: 2 },
      { id: '2', name: 'design', isNew: false, k: 3 },
    ]
    expect(buildToastMessage(adds, [], translate)).toBe('Added "java", "design" to 3.')
  })

  it('sentence-cases a removes-only message', () => {
    const removes: ChangeItem[] = [{ id: '1', name: 'red', isNew: false, k: 2 }]
    expect(buildToastMessage([], removes, translate)).toBe('Removed "red" from 2.')
  })

  it('joins both clauses, capitalizing only the first', () => {
    const adds: ChangeItem[] = [{ id: '1', name: 'ops', isNew: false, k: 5 }]
    const removes: ChangeItem[] = [{ id: '2', name: 'red', isNew: false, k: 2 }]
    expect(buildToastMessage(adds, removes, translate)).toBe(
      'Added "ops" to 5, removed "red" from 2.',
    )
  })
})
