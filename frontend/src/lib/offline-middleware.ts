import * as offlineCache from '@/lib/offline-cache'
import { markServerReachable, markServerUnreachable } from '@/lib/network-status'
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

const OFFLINE_CACHE_HEADER = 'X-Offline-Cache'

function toResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      [OFFLINE_CACHE_HEADER]: '1',
    },
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
    return data ? toResponse({ collections: data }) : undefined
  }

  const match = pathname.match(CACHEABLE_PATHS.COLLECTION_BY_ID)
  if (match && match[1]) {
    const data = await offlineCache.loadCollectionInfo(email, match[1])
    return data ? toResponse(data) : undefined
  }

  return undefined
}

function isSameOrigin(url: string): boolean {
  try {
    return new URL(url, window.location.href).origin === window.location.origin
  } catch {
    return false
  }
}

const UPSTREAM_DOWN_STATUSES = new Set([404, 502, 503, 504])

export function createOfflineMiddleware(): Middleware {
  return {
    post: async (context) => {
      if (!isSameOrigin(context.url)) return undefined
      if (context.response.headers.get(OFFLINE_CACHE_HEADER)) return undefined

      const status = context.response.status
      const isGet = context.init.method === 'GET'

      if (isGet && UPSTREAM_DOWN_STATUSES.has(status)) {
        try {
          const url = new URL(context.url, window.location.href)
          if (isCacheablePath(url.pathname)) {
            const cached = await tryServeFromCache(url.pathname)
            if (cached) {
              if (navigator.onLine) markServerUnreachable()
              return cached
            }
          }
        } catch {
          // fall through to original response
        }
        if (status !== 404 && navigator.onLine) markServerUnreachable()
        return undefined
      }

      if (status < 500) markServerReachable()
      return undefined
    },
    onError: async (context) => {
      if (!(context.error instanceof TypeError)) return undefined
      if (context.init.method !== 'GET') return undefined

      if (navigator.onLine && isSameOrigin(context.url)) {
        markServerUnreachable()
      }

      try {
        const url = new URL(context.url, window.location.href)
        if (!isCacheablePath(url.pathname)) return undefined

        return await tryServeFromCache(url.pathname)
      } catch {
        return undefined
      }
    },
  }
}
