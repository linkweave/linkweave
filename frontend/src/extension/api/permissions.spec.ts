import { describe, it, expect, vi, beforeEach } from 'vitest'
import { checkApiUrl, hasApiPermission, requestApiPermission } from './permissions'

describe('checkApiUrl', () => {
  it('strips the port — a host grant covers all ports', () => {
    expect(checkApiUrl('https://my.host:8443/api')).toEqual({ ok: true, pattern: 'https://my.host/*' })
  })

  it('handles a bare host on the default port', () => {
    expect(checkApiUrl('https://linkweave.dev')).toEqual({ ok: true, pattern: 'https://linkweave.dev/*' })
  })

  it('ignores trailing slashes and paths', () => {
    expect(checkApiUrl('https://example.com/')).toEqual({ ok: true, pattern: 'https://example.com/*' })
    expect(checkApiUrl('https://example.com/some/path')).toEqual({ ok: true, pattern: 'https://example.com/*' })
  })

  it('rejects non-HTTPS origins as not-https', () => {
    expect(checkApiUrl('http://localhost:8080')).toEqual({ ok: false, reason: 'not-https' })
  })

  it('rejects malformed input as invalid-url', () => {
    expect(checkApiUrl('not a url')).toEqual({ ok: false, reason: 'invalid-url' })
    expect(checkApiUrl('')).toEqual({ ok: false, reason: 'invalid-url' })
    expect(checkApiUrl('   ')).toEqual({ ok: false, reason: 'invalid-url' })
  })
})

describe('hasApiPermission / requestApiPermission', () => {
  const contains = vi.fn()
  const request = vi.fn()

  beforeEach(() => {
    contains.mockReset().mockResolvedValue(true)
    request.mockReset().mockResolvedValue(true)
    vi.stubGlobal('chrome', { permissions: { contains, request } })
  })

  it('queries chrome with the port-stripped pattern', async () => {
    await expect(hasApiPermission('https://my.host:8443')).resolves.toBe(true)
    expect(contains).toHaveBeenCalledWith({ origins: ['https://my.host/*'] })
  })

  it('requests the port-stripped pattern', async () => {
    await expect(requestApiPermission('https://my.host:8443/api')).resolves.toBe(true)
    expect(request).toHaveBeenCalledWith({ origins: ['https://my.host/*'] })
  })

  it('short-circuits to false for an invalid URL without calling chrome', async () => {
    await expect(requestApiPermission('garbage')).resolves.toBe(false)
    expect(request).not.toHaveBeenCalled()
  })

  it('short-circuits to false for a non-HTTPS URL without calling chrome', async () => {
    await expect(hasApiPermission('http://localhost:8080')).resolves.toBe(false)
    expect(contains).not.toHaveBeenCalled()
  })

  it('returns false when chrome rejects (e.g. permission denied)', async () => {
    request.mockRejectedValueOnce(new Error('denied'))
    await expect(requestApiPermission('https://my.host')).resolves.toBe(false)
  })
})
