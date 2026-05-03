import { suggestTagNames } from './builtin-tag-rules'

describe('builtin-tag-rules', () => {
  describe('suggestTagNames', () => {
    it('matches dev for label-equals host', () => {
      expect(suggestTagNames('https://dev.acme.com')).toEqual(['dev'])
    })

    it('matches dev for dashed prefix', () => {
      expect(suggestTagNames('https://dev-api.acme.com')).toEqual(['dev'])
    })

    it('does not match dev for unrelated word starting with dev', () => {
      expect(suggestTagNames('https://developer.mozilla.org')).toEqual([])
    })

    it('matches uat for label-equals and dashed prefix', () => {
      expect(suggestTagNames('https://uat.app.com')).toEqual(['uat'])
      expect(suggestTagNames('https://uat-api.app.com')).toEqual(['uat'])
    })

    it('matches local for various local host forms', () => {
      expect(suggestTagNames('http://local.app.test')).toEqual(['local'])
      expect(suggestTagNames('http://local-app.test')).toEqual(['local'])
      expect(suggestTagNames('http://localhost')).toEqual(['local'])
      expect(suggestTagNames('http://localhost:8080/foo')).toEqual(['local'])
      expect(suggestTagNames('http://127.0.0.1')).toEqual(['local'])
    })

    it('matches staging and stg', () => {
      expect(suggestTagNames('https://staging.app.com')).toEqual(['staging'])
      expect(suggestTagNames('https://stg-api.app.com')).toEqual(['staging'])
    })

    it('matches multiple rules in stable order', () => {
      expect(suggestTagNames('https://dev.uat.acme.com')).toEqual(['dev', 'uat'])
    })

    it('returns empty for invalid url', () => {
      expect(suggestTagNames('not a url')).toEqual([])
    })

    it('returns empty when no rule matches', () => {
      expect(suggestTagNames('https://www.acme.com')).toEqual([])
    })

    it('is case-insensitive', () => {
      expect(suggestTagNames('https://DEV.acme.com')).toEqual(['dev'])
    })
  })
})
