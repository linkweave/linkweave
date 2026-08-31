import { describe, it, expect } from 'vitest'
import {
  tokenize,
  matchesTokens,
  type MatchableBookmark,
  type MatchContext,
} from './searchQuery'
import { normalizeUrl } from './url'

describe('search query', () => {
  describe('url operator', () => {
    const bookmark: MatchableBookmark = {
      data: {
        url: 'https://example.com/a',
        title: 'Example',
        description: 'An example site',
      },
    }

    const ctx: MatchContext = {
      tagNamesById: new Map(),
      folderName: null,
      ancestorFolderNames: new Set(),
      ancestorFolderIds: new Set(),
    }

    it('should match exact URL with normalization', () => {
      const tokens = tokenize('url:https://Example.com/a/')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://Example.com/a/', neg: false })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(true)
    })

    it('should not match different URLs', () => {
      const tokens = tokenize('url:https://example.com/a/b')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/a/b', neg: false })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(false)
    })

    it('should match with query parameter reordering', () => {
      const tokens = tokenize('url:https://example.com/a?a=1&b=2')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/a?a=1&b=2', neg: false })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(false) // because bookmark has no query params
    })

    it('should match with same query parameters in different order', () => {
      const bookmarkWithQuery: MatchableBookmark = {
        data: {
          url: 'https://example.com/a?a=1&b=2',
          title: 'Example',
          description: 'An example site',
        },
      }
      
      const tokens = tokenize('url:https://example.com/a?b=2&a=1')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/a?b=2&a=1', neg: false })
      
      const result = matchesTokens(bookmarkWithQuery, tokens, ctx)
      expect(result).toBe(true)
    })

    it('should handle URL with fragment', () => {
      const bookmarkWithFragment: MatchableBookmark = {
        data: {
          url: 'https://example.com/a#top',
          title: 'Example',
          description: 'An example site',
        },
      }
      
      const tokens = tokenize('url:https://example.com/a')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/a', neg: false })
      
      const result = matchesTokens(bookmarkWithFragment, tokens, ctx)
      expect(result).toBe(true) // fragment should be ignored in normalization
    })

    it('should not match invalid URL', () => {
      const tokens = tokenize('url:???')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: '???', neg: false })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(false) // should not match invalid URLs
    })

    it('should treat bare URLs as text, not operators', () => {
      const tokens = tokenize('https://example.com/a')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'text', value: 'https://example.com/a', neg: false })
    })

    it('should handle exclusion with url operator', () => {
      const tokens = tokenize('-url:https://example.com/a')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/a', neg: true })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(false) // negated should not match
    })

    it('should handle exclusion with non-matching URL', () => {
      const tokens = tokenize('-url:https://example.com/b')
      expect(tokens).toHaveLength(1)
      expect(tokens[0]).toEqual({ kind: 'operator', key: 'url', value: 'https://example.com/b', neg: true })
      
      const result = matchesTokens(bookmark, tokens, ctx)
      expect(result).toBe(true) // negated non-match should match
    })
  })
})