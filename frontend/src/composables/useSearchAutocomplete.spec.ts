// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCollectionStore } from '@/stores/collection'
import { useSearchAutocomplete } from './useSearchAutocomplete'

/**
 * Characterization tests for the autocomplete dispatcher. They pin the
 * observable behaviour of every mode (tag / folder / property / operator) so
 * the parser can be refactored without changing what the dropdown shows.
 *
 * The tag / folder / property / bookmark stores all derive from a single
 * `collectionStore.collectionInfo`, so seeding that one object is enough.
 */
function seedStores() {
  useCollectionStore().collectionInfo = {
    tags: [
      { data: { name: 'work', color: '#fff' } },
      { data: { name: 'home', color: null } },
    ],
    folders: [{ data: { name: 'Projects' } }, { data: { name: 'Personal' } }],
    propertyDefinitions: [{ id: 'd1', data: { name: 'Status', allowedValues: 'open,closed' } }],
    bookmarks: [{ propertyValues: [{ definitionId: 'd1', valueText: 'inprogress' }] }],
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any
}

describe('useSearchAutocomplete – parseQueryForAutoCompl', () => {
  let parse: ReturnType<typeof useSearchAutocomplete>['parseQueryForAutoCompl']

  beforeEach(() => {
    setActivePinia(createPinia())
    seedStores()
    parse = useSearchAutocomplete().parseQueryForAutoCompl
  })

  const at = (q: string) => parse(q, q.length)

  it('suggests tags for a # token, filtered by what was typed', () => {
    const r = at('#wo')
    expect(r?.mode).toBe('tag')
    expect(r?.items.map((i) => i.insert)).toEqual(['#work'])
    expect(r?.items[0]?.color).toBe('#fff')
  })

  it('suggests all tags for a bare tag: operator', () => {
    const r = at('tag:')
    expect(r?.mode).toBe('tag')
    expect(r?.items.map((i) => i.insert)).toEqual(['tag:work', 'tag:home'])
  })

  it('suggests folders for folder: (flat) sorted alphabetically', () => {
    const r = at('folder:pro')
    expect(r?.mode).toBe('folder')
    expect(r?.items.map((i) => i.insert)).toEqual(['folder:Projects'])
  })

  it('suggests folders for the hierarchical under: operator', () => {
    const r = at('under:')
    expect(r?.mode).toBe('under')
    expect(r?.items.map((i) => i.label)).toEqual(['Personal', 'Projects'])
  })

  it('suggests property keys for property: with no value yet', () => {
    const r = at('property:st')
    expect(r?.mode).toBe('prop-key')
    expect(r?.items.map((i) => i.insert)).toEqual(['property:Status='])
  })

  it('suggests property values once a key and = are present', () => {
    const r = at('property:Status=op')
    expect(r?.mode).toBe('prop-val')
    expect(r?.label).toBe('Status')
    expect(r?.items.map((i) => i.label)).toEqual(['open'])
  })

  it('merges declared allowedValues with values seen on bookmarks', () => {
    const r = at('property:Status=')
    expect(r?.items.map((i) => i.label)).toEqual(['closed', 'inprogress', 'open'])
  })

  it('discovers operators from a 2+ char prefix', () => {
    const r = at('fol')
    expect(r?.mode).toBe('operator')
    expect(r?.items.map((i) => i.insert)).toContain('folder:')
  })

  it('returns null when nothing matches', () => {
    expect(at('xyz')).toBeNull()
  })
})
