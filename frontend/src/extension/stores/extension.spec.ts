import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock the host-permission layer and the config/client layer so the store's
// permission gating can be exercised without chrome.* or a real backend.
vi.mock('../api/permissions', () => ({
  hasApiPermission: vi.fn(),
  requestApiPermission: vi.fn(),
}))
vi.mock('../api/client', () => ({
  loadExtensionConfig: vi.fn().mockResolvedValue({
    apiUrl: 'https://my.dev',
    webAppUrl: 'https://my.dev',
  }),
  createApiConfig: vi.fn(() => ({})),
}))

import { useExtensionStore } from './extension'
import { hasApiPermission, requestApiPermission } from '../api/permissions'

describe('extension store — permission gating', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(hasApiPermission).mockReset()
    vi.mocked(requestApiPermission).mockReset()
  })

  it('initialize() gates on host permission — sets needsPermission, stays unauthenticated, no API call', async () => {
    vi.mocked(hasApiPermission).mockResolvedValue(false)
    const store = useExtensionStore()

    await store.initialize()

    expect(store.needsPermission).toBe(true)
    expect(store.isAuthenticated).toBe(false)
    expect(requestApiPermission).not.toHaveBeenCalled()
  })

  it('grantPermission() guards double-clicks — only one permission request in flight', async () => {
    vi.mocked(hasApiPermission).mockResolvedValue(false)
    let resolveReq!: (granted: boolean) => void
    vi.mocked(requestApiPermission).mockReturnValue(
      new Promise<boolean>((r) => { resolveReq = r }),
    )
    const store = useExtensionStore()
    await store.initialize() // populates apiUrl + needsPermission

    const p1 = store.grantPermission()
    const p2 = store.grantPermission() // re-entrant call — must be ignored

    expect(requestApiPermission).toHaveBeenCalledTimes(1)
    expect(store.granting).toBe(true)

    resolveReq(false) // user denies
    await Promise.all([p1, p2])

    expect(store.granting).toBe(false)
    expect(store.error).toMatch(/denied/i)
    expect(store.needsPermission).toBe(true) // still gated after a denial
  })

  it('grantPermission() clears a stale error before requesting', async () => {
    vi.mocked(hasApiPermission).mockResolvedValue(false)
    vi.mocked(requestApiPermission).mockResolvedValue(false)
    const store = useExtensionStore()
    await store.initialize()

    store.error = 'previous failure'
    await store.grantPermission()

    // error was cleared at the top, then re-set to the denial message (not the stale one)
    expect(store.error).not.toBe('previous failure')
    expect(store.error).toMatch(/denied/i)
  })
})
