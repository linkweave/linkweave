# Offline Mode — Architecture Plan

**Status:** Draft
**Date:** 2026-04-27
**Requirements:** FR-052 through FR-061, NFR-011 through NFR-013
**Constraints:** C-009 (Offline Auth Trust Model), C-010 (Offline Requires Prior Login)
**Use Cases:** UC-047 (Manage Offline Cache), UC-048 (Browse Bookmarks Offline), UC-049 (Resume Online Session)

---

## Overview

Offline mode enables read-only access to cached bookmarks, folders, and tags when the Chainlink server is unreachable. The feature uses a service worker for app shell caching, IndexedDB for data persistence, and an API middleware layer for transparent cache fallback.

**Key design decisions:**
- **Always-on by default** — caching is automatic; users can opt out in Settings (FR-052)
- **Read-only offline** — no write queueing, no background sync, no conflict resolution (FR-056)
- **User-scoped cache keys** — all IndexedDB entries are prefixed with `{email}:` for data isolation (FR-061)
- **Cached identity as auth** — offline auth trusts the browser's IndexedDB; no password re-check (FR-057, C-009)

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Browser                                                     │
│                                                              │
│  ┌──────────────────────┐   ┌─────────────────────────────┐ │
│  │   Service Worker      │   │   IndexedDB                  │ │
│  │   (vite-plugin-pwa)   │   │   chainlink-offline          │ │
│  │                       │   │                              │ │
│  │  • App shell cache    │   │  {email}:user-info           │ │
│  │  • Static assets      │   │  {email}:collections         │ │
│  │  • navigateFallback   │   │  {email}:collection-info:id  │ │
│  └──────────────────────┘   └─────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Vue App                                                │ │
│  │                                                         │ │
│  │  Pinia Stores              API Client                   │ │
│  │  ┌─────────────┐          ┌────────────────┐           │ │
│  │  │ auth        │◄─────────│ Configuration   │           │ │
│  │  │ collection  │          │ + Middleware[]  │           │ │
│  │  │ bookmark    │          │                │           │ │
│  │  │ folder      │          │ offline-mw ────────►IDB    │ │
│  │  │ tag         │          │ (onError hook) │           │ │
│  │  │ offline ←───┼──────────│                │           │ │
│  │  │ ui          │          └────────────────┘           │ │
│  │  └─────────────┘                                        │ │
│  │                                                         │ │
│  │  offline-cache.ts (read/write IDB)                      │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

#### Online (normal operation, cache population)

```
  User action
      │
      ▼
  Pinia store action (e.g., fetchCollectionInfo)
      │
      ▼
  Generated API client → fetch('/api/collections/123')
      │
      ▼
  Server responds with CollectionInfoJson
      │
      ├──► Pinia store updates (existing behavior, unchanged)
      │
      └──► offline-cache.saveCollectionInfo(email, data)  ← NEW: fire-and-forget
               │
               ▼
          IndexedDB: {email}:collection-info:123
```

#### Offline (server unreachable, cache fallback)

```
  User opens/refreshes the app
      │
      ▼
  Service Worker serves app shell from Cache Storage
      │
      ▼
  Vue boots → router guard → initializeSession()
      │
      ▼
  auth.fetchCurrentUser() → fetch('/api/auth/me') → TypeError (network error)
      │
      ▼
  offline-middleware.onError intercepts
      │
      ▼
  offline-cache.loadUserInfo(email=null) → reads ALL {email}:user-info entries
      │
      ▼  (picks the first — identifies the cached user)
      │
      ▼
  Auth store populated with cached UserInfoJson
      │
      ▼
  Router proceeds → collection store fetches → network fails → middleware intercepts
      │
      ▼
  offline-cache.loadCollectionInfo(email, collectionId)
      │
      ▼
  Collection store populated from IndexedDB
      │
      ▼
  useOffline.isOffline = true → Offline Banner appears → write buttons disabled
```

#### Reconnect (back online)

```
  Browser fires 'online' event
      │
      ▼
  useOffline detects → debounce 2s → isOffline = false
      │
      ▼
  Dismiss banner → re-enable write buttons
      │
      ▼
  Background: fetchCollectionInfo() → server responds → update stores + IDB cache
```

---

## Component Design

### 1. Service Worker — `vite-plugin-pwa`

**File:** `vite.config.ts` (plugin configuration)
**Dependency:** `vite-plugin-pwa`

Caches the app shell (HTML, JS, CSS, fonts, images) so the application loads offline. Does NOT handle API caching — that's handled by the offline middleware.

**Configuration:**
```typescript
VitePWA({
  registerType: 'autoUpdate',
  workbox: {
    navigateFallback: 'index.html',
    navigateFallbackDenylist: [/^\/api/],
    runtimeCaching: [
      // Static assets: cache-first
      {
        urlPattern: /^https:\/\/.*\.(js|css|png|svg|ico|woff2)$/,
        handler: 'CacheFirst',
        options: { cacheName: 'chainlink-assets' },
      },
    ],
  },
  manifest: {
    name: 'Chainlink - Bookmark Manager',
    short_name: 'Chainlink',
    icons: [/* ... */],
    display: 'standalone',
  },
})
```

### 2. Offline Cache — `src/lib/offline-cache.ts`

**Responsibility:** Read/write data to IndexedDB with user-scoped keys.

**Database:** `chainlink-offline` (version 1)

**Object Stores and Key Format:**

| Object Store | Key Format | Value |
|---|---|---|
| `user-info` | `{email}:user-info` | `{ data: UserInfoJson, cachedAt: number }` |
| `collections` | `{email}:collections` | `{ data: CollectionSummaryJson[], cachedAt: number }` |
| `collection-info` | `{email}:collection-info:{collectionId}` | `{ data: CollectionInfoJson, cachedAt: number }` |

**Public API:**
```typescript
// Write operations (called after successful API responses)
function saveUserInfo(email: string, user: UserInfoJson): Promise<void>
function saveCollections(email: string, collections: CollectionSummaryJson[]): Promise<void>
function saveCollectionInfo(email: string, info: CollectionInfoJson): Promise<void>

// Read operations (called by offline middleware on network failure)
function loadUserInfo(): Promise<{ email: string; data: UserInfoJson; cachedAt: number } | null>
function loadCollections(email: string): Promise<CollectionSummaryJson[] | null>
function loadCollectionInfo(email: string, collectionId: string): Promise<CollectionInfoJson | null>

// Purge operations
function purgeForUser(email: string): Promise<void>   // On logout: delete {email}:*
function purgeAll(): Promise<void>                      // Nuclear: delete entire database

// Metadata
function getLastSyncedAt(email: string): Promise<number | null>
```

**Key design note:** `loadUserInfo()` does not take an email parameter — it scans for the first `{email}:user-info` entry to establish identity. This is the "who am I?" bootstrap read on offline boot.

### 3. Offline Middleware — `src/lib/offline-middleware.ts`

**Responsibility:** Intercept failed API fetches and serve cached responses from IndexedDB.

Plugs into the existing `Middleware.onError` hook in the generated API client runtime (`src/api/generated/runtime.ts`).

```typescript
import type { Middleware } from '@/api/generated/runtime'

export function createOfflineMiddleware(): Middleware {
  return {
    onError: async (context) => {
      // Only intercept network errors (TypeError) on GET requests
      if (!(context.error instanceof TypeError)) return undefined
      if (context.init.method !== 'GET') return undefined

      const url = new URL(context.url)
      return tryServeFromCache(url)
    }
  }
}

async function tryServeFromCache(url: URL): Promise<Response | undefined> {
  // 1. Identify the user from IndexedDB
  const cachedUser = await loadUserInfo()
  if (!cachedUser) return undefined

  const email = cachedUser.email

  // 2. Route to the correct cache loader based on URL pattern
  if (url.pathname === '/api/auth/me') {
    return toResponse(cachedUser.data)
  }

  if (url.pathname === '/api/collections') {
    const data = await loadCollections(email)
    return data ? toResponse(data) : undefined
  }

  const collectionMatch = url.pathname.match(/^\/api\/collections\/([^/]+)$/)
  if (collectionMatch) {
    const data = await loadCollectionInfo(email, collectionMatch[1])
    return data ? toResponse(data) : undefined
  }

  return undefined
}

function toResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
```

### 4. Offline Store — `src/composables/useOffline.ts`

**Responsibility:** Reactively track online/offline state. Expose to all components.

```typescript
export const useOffline = defineStore('offline', () => {
  const isOffline = ref(!navigator.onLine)
  const lastSyncedAt = ref<number | null>(null)
  const cachedUserEmail = ref<string | null>(null)

  // Debounced online/offline listeners
  // Load lastSyncedAt and cachedUserEmail from IndexedDB on init

  return { isOffline, lastSyncedAt, cachedUserEmail }
})
```

### 5. Offline Banner — `src/components/ui/OfflineBanner.vue`

**Responsibility:** Display a dismissible banner when offline.

Shows:
- "You're offline. Showing cached data." (when cache is available)
- "You're offline. No cached data available." (when cache is empty)
- "Last synced X minutes ago" (freshness indicator)

---

## Files to Create

| # | File | Description |
|---|------|-------------|
| 1 | `src/lib/offline-cache.ts` | IndexedDB wrapper with user-scoped keys |
| 2 | `src/lib/offline-middleware.ts` | Middleware: intercept failed fetches → serve from cache |
| 3 | `src/composables/useOffline.ts` | Pinia store: reactive offline state |
| 4 | `src/components/ui/OfflineBanner.vue` | Visual offline indicator banner |
| 5 | `src/lib/offline-cache.spec.ts` | Unit tests for IndexedDB layer |

## Files to Modify

| # | File | Changes |
|---|------|---------|
| 1 | `frontend/vite.config.ts` | Add `VitePWA` plugin with Workbox config |
| 2 | `frontend/package.json` | Add `vite-plugin-pwa` dependency |
| 3 | `frontend/index.html` | Add `<link rel="manifest">` and theme-color meta |
| 4 | `frontend/public/manifest.json` | Web app manifest for PWA |
| 5 | `src/api/client.ts` | Register offline middleware in `Configuration.middleware` |
| 6 | `src/stores/collection.ts` | Add `saveCollections()` / `saveCollectionInfo()` calls after successful fetches |
| 7 | `src/stores/auth.ts` | Add `saveUserInfo()` call after successful fetch; handle offline in `fetchCurrentUser`; purge cache on logout |
| 8 | `src/router/index.ts` | Enhance guard: if `fetchCurrentUser()` fails and offline cache has user info, treat as authenticated |
| 9 | `src/views/CollectionView.vue` | Hide "Add Bookmark" button when `isOffline` |
| 10 | `src/components/layout/MainLayout.vue` | Add `<OfflineBanner />` to layout |
| 11 | `src/components/layout/SidebarCl.vue` | Disable "New Folder" button when `isOffline` |
| 12 | `src/components/layout/HeaderCl.vue` | Conditionally disable write actions when `isOffline` |
| 13 | `src/i18n/locales/en.json` | Add `offline.*` i18n keys |
| 14 | `src/i18n/locales/de.json` | Add German translations for offline keys |

---

## IndexedDB Schema

**Database:** `chainlink-offline` (version 1)

```
Object Store: user-info
  Key (string): "{email}:user-info"
  Value: { data: UserInfoJson, cachedAt: number }

Object Store: collections
  Key (string): "{email}:collections"
  Value: { data: CollectionSummaryJson[], cachedAt: number }

Object Store: collection-info
  Key (string): "{email}:collection-info:{collectionId}"
  Value: { data: CollectionInfoJson, cachedAt: number }
```

No indexes needed — all lookups are by exact key.

---

## Offline Auth Model

Offline authentication is based on trusting the browser's local IndexedDB cache as evidence of a prior successful login. There is **no password re-check, no session cookie validation, and no server contact**.

### How It Works

1. User logs in online → `GET /api/auth/me` succeeds → `UserInfoJson` cached to `{email}:user-info`
2. Server goes down → user refreshes page → `GET /api/auth/me` fails → middleware reads `{email}:user-info` from IndexedDB → auth store populated → router guard treats user as authenticated
3. The `email` from the cached `UserInfoJson` becomes the identity for all subsequent cache reads

### Scenarios

| Scenario | Outcome | Why |
|----------|---------|-----|
| Logged in yesterday, server down today | ✅ Access cached bookmarks | `alice@example.com:user-info` exists in IDB |
| Never logged in on this browser, server down | ❌ Redirected to login | No `{email}:user-info` in IDB |
| Explicitly logged out, server down | ❌ Redirected to login | Logout purged `alice@example.com:*` from IDB |
| Logged in on Chrome, open Firefox during outage | ❌ Redirected to login in Firefox | IDB is per-origin, per-browser |
| Tab closed without logout, different person opens browser offline | ⚠️ Sees previous user's cached bookmarks (read-only) | No way to distinguish users without server — accepted trade-off (C-009) |

### Mitigations for the Tab-Close Edge Case

- Data is **read-only** — no modifications, deletions, or shares can occur
- **User-scoped keys** prevent accessing any other user's data
- **Explicit logout** purges cache completely (FR-060)
- **Same-origin policy** prevents other websites from reading IndexedDB
- On reconnect, the server session is validated — if expired, user is redirected to login

---

## Build Sequence

### Phase 1: Foundation — IndexedDB Cache + Offline Middleware
- [ ] Install `vite-plugin-pwa`
- [ ] Create `src/lib/offline-cache.ts` (IndexedDB wrapper with user-scoped keys)
- [ ] Create `src/lib/offline-middleware.ts` (fetch error interceptor)
- [ ] Register middleware in `src/api/client.ts`
- [ ] Add cache-save calls to `collection.ts` (after `fetchCollections`, `fetchCollectionInfo`) and `auth.ts` (after `fetchCurrentUser`)
- [ ] Add cache-purge call to `auth.ts` (on `logout`)
- [ ] Write unit tests for `offline-cache.ts`
- [ ] **Verify:** `npm run type-check && npm run test`

### Phase 2: Service Worker + PWA Shell
- [ ] Configure `VitePWA` plugin in `vite.config.ts`
- [ ] Create `public/manifest.json` with app name, icons, theme colors
- [ ] Add `<link rel="manifest">` to `index.html`
- [ ] **Verify:** `npm run build` → test that SW registers in browser

### Phase 3: Offline UI + Read-Only Mode
- [ ] Create `src/composables/useOffline.ts` (Pinia store)
- [ ] Create `src/components/ui/OfflineBanner.vue`
- [ ] Integrate banner into `MainLayout.vue`
- [ ] Enhance router guard in `router/index.ts` for offline auth bypass
- [ ] Disable write-action buttons when offline in `CollectionView`, `SidebarCl`, `HeaderCl`
- [ ] Add i18n keys to `en.json` and `de.json`
- [ ] **Verify:** `npm run type-check`, test offline mode in Chrome DevTools

### Phase 4: Polish + Edge Cases
- [ ] Handle stale cache: show "Last synced X minutes ago" in banner
- [ ] Handle back-online: auto-refresh data, dismiss banner, debounced (2s)
- [ ] Add E2E test for offline scenario using Playwright (`context.setOffline(true)`)
- [ ] **Verify:** `npm run test` + `npm run test:e2e`

---

## i18n Keys

```json
{
  "offline": {
    "banner": "You're offline. Showing cached data.",
    "bannerNoCache": "You're offline. No cached data available.",
    "bannerNoCacheHint": "Connect to the internet to load your bookmarks.",
    "lastSynced": "Last synced {time}",
    "readOnly": "Changes are not available while offline.",
    "writeDisabled": "This action is not available while offline.",
    "backOnline": "Back online.",
    "serverUnreachable": "You appear to be back online, but the server could not be reached.",
    "loginRequired": "You are offline. Please connect to the internet to sign in.",
    "sessionExpired": "Your session has expired. Please sign in again."
  }
}
```

---

## Performance Targets

| Metric | Target | NFR |
|--------|--------|-----|
| App shell load from SW cache (cold, no network) | < 500 ms | NFR-011 |
| Cached data served from IndexedDB to Pinia store | < 200 ms | NFR-012 |
| Total IndexedDB storage per user (10 collections × 1,000 bookmarks) | < 50 MB | NFR-013 |
| Cache writes (after API response, fire-and-forget) | Non-blocking, no UX impact | — |
| Online/offline debounce | 2 seconds | BR-049-3 |
