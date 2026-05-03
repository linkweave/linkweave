import { compileCustomRules, suggestAllTagNames } from './tag-suggester'

describe('tag-suggester', () => {
  describe('compileCustomRules', () => {
    it('drops disabled rules', () => {
      const compiled = compileCustomRules([
        { pattern: '^https://github\\.com/', tagNames: 'gh', enabled: false },
      ])
      expect(compiled).toEqual([])
    })

    it('drops rules with invalid regex', () => {
      const compiled = compileCustomRules([
        { pattern: '[unclosed', tagNames: 'foo', enabled: true },
      ])
      expect(compiled).toEqual([])
    })

    it('drops rules with empty tag list', () => {
      const compiled = compileCustomRules([
        { pattern: '.*', tagNames: '  , ', enabled: true },
      ])
      expect(compiled).toEqual([])
    })

    it('normalises tag names (trim, lowercase, drop empty)', () => {
      const compiled = compileCustomRules([
        { pattern: '.*', tagNames: 'Foo, , BAR', enabled: true },
      ])
      expect(compiled[0]?.tagNames).toEqual(['foo', 'bar'])
    })
  })

  describe('suggestAllTagNames', () => {
    it('combines built-in and custom in order, deduped', () => {
      const custom = compileCustomRules([
        { pattern: '^https://github\\.com/.+/pull/\\d+', tagNames: 'pr,github', enabled: true },
      ])
      expect(suggestAllTagNames('https://github.com/foo/bar/pull/42', custom)).toEqual([
        'pr',
        'github',
      ])
    })

    it('built-in wins ordering when overlapping', () => {
      const custom = compileCustomRules([
        { pattern: '\\.com', tagNames: 'dev', enabled: true },
      ])
      expect(suggestAllTagNames('https://dev.acme.com', custom)).toEqual(['dev'])
    })

    it('custom rule applies even when no built-in matches', () => {
      const custom = compileCustomRules([
        { pattern: '^https://www\\.acme\\.com/', tagNames: 'acme', enabled: true },
      ])
      expect(suggestAllTagNames('https://www.acme.com/foo', custom)).toEqual(['acme'])
    })

    it('returns built-ins only when no custom rule matches', () => {
      const custom = compileCustomRules([
        { pattern: '^https://other\\.com', tagNames: 'other', enabled: true },
      ])
      expect(suggestAllTagNames('https://dev.acme.com', custom)).toEqual(['dev'])
    })

    it('returns empty for invalid url with no matching custom rule', () => {
      expect(suggestAllTagNames('not a url', [])).toEqual([])
    })
  })
})
