// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { ApiKeyJson } from '@/api/generated'
import { MAX_ACTIVE_API_KEYS, useApiKeyStore } from '@/stores/apiKey'

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    apiAuthApiKeysGet: vi.fn(),
    apiAuthApiKeysPost: vi.fn(),
    apiAuthApiKeysApiKeyIdDelete: vi.fn(),
  },
}))

vi.mock('@/api', () => ({ config: {} }))
vi.mock('@/api/generated', () => ({
  ApiKeyResourceApi: class {
    constructor() {
      return apiMock
    }
  },
}))

function key(overrides: Partial<ApiKeyJson> = {}): ApiKeyJson {
  return {
    id: overrides.id ?? crypto.randomUUID(),
    name: 'key',
    prefix: 'abcd',
    createdAt: new Date('2026-01-01'),
    ...overrides,
  }
}

function seedGet(keys: ApiKeyJson[]) {
  apiMock.apiAuthApiKeysGet.mockResolvedValue({ apiKeys: keys })
}

describe('apiKey store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads keys and hides revoked ones from visibleKeys', async () => {
    seedGet([
      key({ id: '1' }),
      key({ id: '2', revokedAt: new Date('2026-02-01') }),
    ])
    const store = useApiKeyStore()
    await store.load()

    expect(store.keys).toHaveLength(2)
    expect(store.visibleKeys.map((k) => k.id)).toEqual(['1'])
  })

  it('treats keys with a past expiry as expired and excludes them from activeCount', async () => {
    seedGet([
      key({ id: 'active' }),
      key({ id: 'expired', expiresAt: new Date('2020-01-01') }),
    ])
    const store = useApiKeyStore()
    await store.load()

    const active = store.keys.find((k) => k.id === 'active')!
    const expired = store.keys.find((k) => k.id === 'expired')!
    expect(store.isExpired(active)).toBe(false)
    expect(store.isExpired(expired)).toBe(true)
    expect(store.activeCount).toBe(1)
  })

  it('flags maxReached only when active keys hit the limit', async () => {
    seedGet(Array.from({ length: MAX_ACTIVE_API_KEYS }, (_, i) => key({ id: String(i) })))
    const store = useApiKeyStore()
    await store.load()

    expect(store.maxReached).toBe(true)
  })

  it('create posts, reloads, and returns the raw key', async () => {
    apiMock.apiAuthApiKeysPost.mockResolvedValue({ key: 'cl_secret_raw' })
    seedGet([key({ id: 'new', name: 'CI' })])
    const store = useApiKeyStore()

    const raw = await store.create({ name: 'CI', expiresIn: '30d' })

    expect(raw).toBe('cl_secret_raw')
    expect(apiMock.apiAuthApiKeysPost).toHaveBeenCalledWith({
      apiKeyCreateJson: { name: 'CI', expiresIn: '30d' },
    })
    expect(store.visibleKeys.map((k) => k.id)).toEqual(['new'])
  })

  it('revoke optimistically drops the key from visibleKeys', async () => {
    seedGet([key({ id: '1' }), key({ id: '2' })])
    apiMock.apiAuthApiKeysApiKeyIdDelete.mockResolvedValue(undefined)
    const store = useApiKeyStore()
    await store.load()

    await store.revoke('1')

    expect(apiMock.apiAuthApiKeysApiKeyIdDelete).toHaveBeenCalledWith({ apiKeyId: '1' })
    expect(store.visibleKeys.map((k) => k.id)).toEqual(['2'])
  })
})
