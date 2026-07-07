import * as offlineCache from '@/lib/offline-cache'
import { markServerReachable, markServerUnreachable } from '@/lib/network-status'
import type { Middleware } from '@/api/generated/runtime'

const AUTH_ME_PATH = '/api/auth/me'

const CACHEABLE_PATHS = {
  COLLECTIONS: '/api/collections',
  COLLECTION_BY_ID: /^\/api\/collections\/([^/]+)$/,
  SAVED_SEARCHES: '/api/saved-searches',
} as const

function isCacheablePath(pathname: string): boolean {
  if (pathname === CACHEABLE_PATHS.COLLECTIONS) return true
  if (pathname === CACHEABLE_PATHS.SAVED_SEARCHES) return true
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

async function tryServeFromCache(
  pathname: string,
  searchParams: URLSearchParams,
): Promise<Response | undefined> {
  const cachedUser = await offlineCache.loadUserInfo()
  if (!cachedUser) return undefined

  const { email } = cachedUser

  if (pathname === CACHEABLE_PATHS.COLLECTIONS) {
    const data = await offlineCache.loadCollections(email)
    return data ? toResponse({ collections: data }) : undefined
  }

  if (pathname === CACHEABLE_PATHS.SAVED_SEARCHES) {
    const collectionId = searchParams.get('collectionId')
    if (!collectionId) return undefined
    const data = await offlineCache.loadSavedSearches(email, collectionId)
    return data ? toResponse({ savedSearchList: data }) : undefined
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

let onSessionExpired: (() => void) | null = null
export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler
}

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
            const cached = await tryServeFromCache(url.pathname, url.searchParams)
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

      // 499 is Quarkus OIDC's "unauthenticated AJAX request" response (see
      // quarkus.oidc.authentication.java-script-auto-redirect=false) — semantically
      // identical to 401 for our purposes. Checked for every method: mutations
      // (create tag/bookmark, …) hit this too when the session expires, and the
      // user must be sent back to the login page rather than see a dead button.
      // Authorization failures are 403, so 401/499 is unambiguous here.
      if (status === 401 || status === 499) {
        try {
          const url = new URL(context.url, window.location.href)
          if (!url.pathname.startsWith('/api/auth/')) {
            onSessionExpired?.()
          }
        } catch {
          // ignore — malformed URL, no action
        }
      }
      return undefined
    },
    onError: async (context) => {
      if (!(context.error instanceof TypeError)) return undefined
      if (context.init.method !== 'GET') return undefined
      if (!isSameOrigin(context.url)) return undefined

      let pathname: string
      let searchParams: URLSearchParams
      try {
        const parsed = new URL(context.url, window.location.href)
        pathname = parsed.pathname
        searchParams = parsed.searchParams
      } catch {
        return undefined
      }

      // Errors on /api/auth/me are typically auth redirects (e.g. OIDC 302
      // to the IDP that fails CORS), not real network failures. They must
      // not flip the app into "offline" mode or serve cached user info —
      // server reachability is the dedicated probe's job.
      if (pathname === AUTH_ME_PATH) return undefined

      if (navigator.onLine) markServerUnreachable()

      if (!isCacheablePath(pathname)) return undefined
      try {
        return await tryServeFromCache(pathname, searchParams)
      } catch {
        return undefined
      }
    },
  }
}
