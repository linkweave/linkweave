import { describe, expect, it } from 'vitest'
import { tokenAtCursor } from './searchAutocompleteToken'

describe('tokenAtCursor', () => {
  it('returns the token at the end of the query', () => {
    const q = 'hello #quar'
    expect(tokenAtCursor(q, q.length)).toEqual({
      token: '#quar',
      colonToken: '#quar',
      range: [6, 11],
    })
  })

  it('covers the whole token when the cursor is in its middle', () => {
    // query: "folder:dev next", cursor inside "folder:dev" (after "folder:d")
    const q = 'folder:dev next'
    const cursor = 8 // between 'd' and 'e'
    expect(tokenAtCursor(q, cursor)).toEqual({
      token: 'folder:d',
      colonToken: 'folder:d',
      // range spans the full token, not just up to the cursor
      range: [0, 10],
    })
  })

  it('treats a bare operator name as its colon form', () => {
    expect(tokenAtCursor('folder', 6).colonToken).toBe('folder:')
    expect(tokenAtCursor('tag', 3).colonToken).toBe('tag:')
    expect(tokenAtCursor('under', 5).colonToken).toBe('under:')
    expect(tokenAtCursor('property', 8).colonToken).toBe('property:')
  })

  it('is case-insensitive when normalizing a bare operator name', () => {
    expect(tokenAtCursor('Folder', 6).colonToken).toBe('Folder:')
  })

  it('does not normalize once a colon is already present', () => {
    expect(tokenAtCursor('folder:', 7).colonToken).toBe('folder:')
    expect(tokenAtCursor('folder:dev', 10).colonToken).toBe('folder:dev')
  })

  it('does not normalize a non-operator word or a partial prefix', () => {
    expect(tokenAtCursor('folders', 7).colonToken).toBe('folders')
    expect(tokenAtCursor('fo', 2).colonToken).toBe('fo')
    expect(tokenAtCursor('quarkus', 7).colonToken).toBe('quarkus')
  })

  it('returns an empty token at a trailing space', () => {
    const q = '#quarkus '
    expect(tokenAtCursor(q, q.length)).toEqual({
      token: '',
      colonToken: '',
      range: [9, 9],
    })
  })

  it('splits tokens on tabs as well as spaces', () => {
    const q = 'a\tfolder:dev'
    expect(tokenAtCursor(q, q.length)).toEqual({
      token: 'folder:dev',
      colonToken: 'folder:dev',
      range: [2, 12],
    })
  })

  it('handles an empty query', () => {
    expect(tokenAtCursor('', 0)).toEqual({ token: '', colonToken: '', range: [0, 0] })
  })
})
