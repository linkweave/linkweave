// @vitest-environment happy-dom
import 'fake-indexeddb/auto'
import {
  CollectionInfoJsonFromJSON,
  CollectionSummaryListJsonFromJSON,
} from '@/api/generated'
import type { CollectionInfoJson, CollectionSummaryJson, UserInfoJson } from '@/api/generated'
import {
  purgeAll,
  saveCollectionInfo,
  saveCollections,
  saveUserInfo,
} from './offline-cache'
import { createOfflineMiddleware } from './offline-middleware'

const fakeUser: UserInfoJson = {
  email: 'alice@example.com',
  firstName: 'Alice',
  lastName: 'User',
  roles: new Set(['USER']),
  defaultCollectionId: 'col-1',
  settings: { offlineCachingEnabled: true, savedSearchesEnabled: true },
}

const fakeCollections: CollectionSummaryJson[] = [
  {
    id: 'col-1',
    name: 'Default',
    isDefault: true,
    role: 'ADMIN' as CollectionSummaryJson['role'],
    shared: false,
  },
]

const fakeCollectionInfo: CollectionInfoJson = {
  id: 'col-1',
  name: 'Default',
  bookmarks: [],
  tags: [],
  folders: [],
  autoTagRules: [],
  propertyDefinitions: [],
}

beforeEach(async () => {
  await purgeAll()
  await saveUserInfo('alice@example.com', fakeUser)
  await saveCollections('alice@example.com', fakeCollections)
  await saveCollectionInfo('alice@example.com', fakeCollectionInfo)
})

function makeErrorContext(url: string) {
  return {
    fetch: (() => Promise.reject(new Error('not used'))) as unknown as typeof fetch,
    url,
    init: { method: 'GET' } as RequestInit,
    error: new TypeError('Failed to fetch'),
  }
}

describe('offline-middleware onError cache fallback', () => {
  it('does not serve /api/auth/me from cache — auth must hit the server', async () => {
    const middleware = createOfflineMiddleware()
    const res = await middleware.onError!(makeErrorContext('/api/auth/me'))
    expect(res).toBeUndefined()
  })

  it('serves /api/collections wrapped so CollectionSummaryListJsonFromJSON can parse', async () => {
    const middleware = createOfflineMiddleware()
    const res = await middleware.onError!(makeErrorContext('/api/collections'))
    expect(res).toBeInstanceOf(Response)

    // Regression guard: the parser expects { collections: [...] }, not the bare array.
    const parsed = CollectionSummaryListJsonFromJSON(await res!.json())
    expect(parsed.collections).toHaveLength(1)
    expect(parsed.collections[0]!.id).toBe('col-1')
  })

  it('serves /api/collections/:id in a shape CollectionInfoJsonFromJSON can parse', async () => {
    const middleware = createOfflineMiddleware()
    const res = await middleware.onError!(makeErrorContext('/api/collections/col-1'))
    expect(res).toBeInstanceOf(Response)

    const parsed = CollectionInfoJsonFromJSON(await res!.json())
    expect(parsed.id).toBe('col-1')
    expect(parsed.name).toBe('Default')
  })

  it('returns undefined when path is not cacheable', async () => {
    const middleware = createOfflineMiddleware()
    const res = await middleware.onError!(makeErrorContext('/api/bookmarks'))
    expect(res).toBeUndefined()
  })

  it('returns undefined for non-GET requests', async () => {
    const middleware = createOfflineMiddleware()
    const ctx = { ...makeErrorContext('/api/collections'), init: { method: 'POST' } as RequestInit }
    const res = await middleware.onError!(ctx)
    expect(res).toBeUndefined()
  })
})
