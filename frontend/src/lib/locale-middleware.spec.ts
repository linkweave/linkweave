import { describe, expect, it, vi } from 'vitest'
import type { RequestContext } from '@/api/generated/runtime'

// Mock the i18n instance so the middleware reads a controllable active locale.
// vi.hoisted lets the factory (hoisted to top) share the ref with the tests.
const { localeRef } = vi.hoisted(() => ({ localeRef: { value: 'en' } }))
vi.mock('@/i18n', () => ({
  default: { global: { locale: localeRef } },
}))

import { createLocaleMiddleware } from './locale-middleware'

function ctx(init: RequestInit = {}): RequestContext {
  return { fetch: globalThis.fetch, url: '/api/collections', init }
}

describe('createLocaleMiddleware', () => {
  it('sets Accept-Language from the active locale', async () => {
    localeRef.value = 'fr'
    const result = await createLocaleMiddleware().pre!(ctx())
    expect(result).toBeDefined()
    const headers = (result as { init: RequestInit }).init.headers as Record<string, string>
    expect(headers['Accept-Language']).toBe('fr')
  })

  it('reflects a live locale switch on each request', async () => {
    const mw = createLocaleMiddleware()
    localeRef.value = 'de'
    const de = await mw.pre!(ctx())
    localeRef.value = 'en'
    const en = await mw.pre!(ctx())
    expect(((de as { init: RequestInit }).init.headers as Record<string, string>)['Accept-Language']).toBe('de')
    expect(((en as { init: RequestInit }).init.headers as Record<string, string>)['Accept-Language']).toBe('en')
  })

  it('preserves existing headers', async () => {
    localeRef.value = 'en'
    const result = await createLocaleMiddleware().pre!(
      ctx({ headers: { 'X-Requested-With': 'XMLHttpRequest' } }),
    )
    const headers = (result as { init: RequestInit }).init.headers as Record<string, string>
    expect(headers['X-Requested-With']).toBe('XMLHttpRequest')
    expect(headers['Accept-Language']).toBe('en')
  })
})
