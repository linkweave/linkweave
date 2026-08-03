// @vitest-environment happy-dom
import { useOfflineStore } from '@/stores/offline'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('offline store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })),
      ),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should register the online/offline listeners only once', () => {
    // ARRANGE: MainLayout wraps five views and remounts on every navigation
    // between them, so setupListeners runs again and again.
    const addEventListener = vi.spyOn(window, 'addEventListener')
    const store = useOfflineStore()

    // ACT
    store.setupListeners()
    store.setupListeners()
    store.setupListeners()

    // ASSERT
    const networkListeners = addEventListener.mock.calls.filter(
      ([type]) => type === 'online' || type === 'offline',
    )
    expect(networkListeners).toHaveLength(2)
  })
})
