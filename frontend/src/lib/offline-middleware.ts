import * as offlineCache from '@/lib/offline-cache'
import type { Middleware } from '@/api/generated/runtime'
import { UserInfoJsonToJSON } from '@/api/generated'

const CACHEABLE_PATHS = {
  AUTH_ME: '/api/auth/me',
  COLLECTIONS: '/api/collections',
  COLLECTION_BY_ID: /^\/api\/collections\/([^/]+)$/,
} as const

function isCacheablePath(pathname: string): boolean {
  if (pathname === CACHEABLE_PATHS.AUTH_ME) return true
  if (pathname === CACHEABLE_PATHS.COLLECTIONS) return true
  if (CACHEABLE_PATHS.COLLECTION_BY_ID.test(pathname)) return true
  return false
}

function toResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function tryServeFromCache(pathname: string): Promise<Response | undefined> {
  const cachedUser = await offlineCache.loadUserInfo()
  if (!cachedUser) return undefined

  const { email, data: userData } = cachedUser

  if (pathname === CACHEABLE_PATHS.AUTH_ME) {
    return toResponse(UserInfoJsonToJSON(userData))
  }

  if (pathname === CACHEABLE_PATHS.COLLECTIONS) {
    const data = await offlineCache.loadCollections(email)
    return data ? toResponse(data) : undefined
  }

  const match = pathname.match(CACHEABLE_PATHS.COLLECTION_BY_ID)
  if (match && match[1]) {
    const data = await offlineCache.loadCollectionInfo(email, match[1])
    return data ? toResponse(data) : undefined
  }

  return undefined
}

export function createOfflineMiddleware(): Middleware {
  return {
    onError: async (context) => {
      if (!(context.error instanceof TypeError)) return undefined
      if (context.init.method !== 'GET') return undefined
      if (navigator.onLine) return undefined

      try {
        const url = new URL(context.url)
        if (!isCacheablePath(url.pathname)) return undefined

        return await tryServeFromCache(url.pathname)
      } catch {
        return undefined
      }
    },
  }
}
