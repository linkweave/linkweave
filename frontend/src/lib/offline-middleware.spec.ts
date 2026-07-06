// @vitest-environment happy-dom
import 'fake-indexeddb/auto'
import type { CollectionInfoJson, CollectionSummaryJson, UserInfoJson } from '@/api/generated'
import {
  CollectionInfoJsonFromJSON,
  CollectionSummaryListJsonFromJSON,
  Permission,
} from '@/api/generated'
import { purgeAll, saveCollectionInfo, saveCollections, saveUserInfo } from './offline-cache'
import { createOfflineMiddleware, setSessionExpiredHandler } from './offline-middleware'

const fakeUser: UserInfoJson = {
  id: 'user-1',
  email: 'alice@example.com',
  firstName: 'Alice',
  lastName: 'User',
  permissions: new Set([Permission.BookmarkRead, Permission.BookmarkWrite]),
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
  screenshotEnabled: false,
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

function makeResponseContext(url: string, method: string, status: number) {
  return {
    fetch: (() => Promise.reject(new Error('not used'))) as unknown as typeof fetch,
    url,
    init: { method } as RequestInit,
    response: new Response(null, { status }),
  }
}

describe('offline-middleware session expiry detection', () => {
  let expiredCalls: number

  beforeEach(() => {
    expiredCalls = 0
    setSessionExpiredHandler(() => {
      expiredCalls++
    })
  })

  afterEach(() => {
    setSessionExpiredHandler(null)
  })

  it('should fire session-expired handler on 499 for POST requests', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/tags', 'POST', 499))
    expect(expiredCalls).toBe(1)
  })

  it('should fire session-expired handler on 401 for DELETE requests', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/bookmarks/b-1', 'DELETE', 401))
    expect(expiredCalls).toBe(1)
  })

  it('should fire session-expired handler on 499 for GET requests', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/collections', 'GET', 499))
    expect(expiredCalls).toBe(1)
  })

  it('should not fire session-expired handler for /api/auth/ paths', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/auth/logout', 'POST', 499))
    expect(expiredCalls).toBe(0)
  })

  it('should not fire session-expired handler on successful responses', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/tags', 'POST', 200))
    expect(expiredCalls).toBe(0)
  })

  it('should not fire session-expired handler on 403 authorization failures', async () => {
    const middleware = createOfflineMiddleware()
    await middleware.post!(makeResponseContext('/api/tags', 'POST', 403))
    expect(expiredCalls).toBe(0)
  })
})
