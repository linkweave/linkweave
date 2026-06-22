# Use Case: Manage Offline Cache

## Overview

**Use Case ID:** UC-047
**Use Case Name:** Manage Offline Cache
**Primary Actor:** User
**Goal:** Control whether the app caches collection data locally for offline access (enabled by default, can be disabled)
**Status:** Implemented

**Implementation Notes:**
- Always-on caching is implemented — no user opt-out toggle yet (backend `User` entity change deferred due to `@AllArgsConstructor` cascading build issue; will use a separate `UserSettings` entity when revisited)
- A1/A2 (disable/re-enable) are NOT implemented — caching is always active
- A3/A4 (SW not supported / IndexedDB not available) are handled gracefully — cache writes fail with `console.error`
- Main success scenario is fully implemented in `src/lib/offline-cache.ts` + store hooks in `auth.ts` and `collection.ts`

**Traces to:** FR-052, FR-053, FR-061

## Preconditions

- User is authenticated and has at least one collection
- The service worker is registered and active in the browser
- User is currently online
- Offline caching is active by default — the system already persists API responses to IndexedDB on every successful fetch

## Main Success Scenario

Automatic caching (default behavior — no user action required):

1. User browses the app normally (loads collections, views bookmarks).
2. After every successful API response for `GET /api/auth/me`, `GET /api/collections`, and `GET /api/collections/{id}`, the system automatically persists the response data to IndexedDB (`linkweave-offline` database).
3. All data is stored under user-scoped keys using the user's email as a namespace prefix (e.g., `alice@example.com:collections`, `alice@example.com:collection-info:uuid-123`).
4. The cached data is available for offline browsing (see UC-048).

## Alternative Flows

### A1: User Disables Offline Caching

**Trigger:** User toggles "Offline Caching" to OFF in Settings
**Flow:**

1. User opens the Settings dialog from the user menu.
2. System displays the Settings dialog with a "Data Management" section containing an "Offline Caching" toggle (currently ON).
3. User toggles "Offline Caching" to OFF.
4. System updates the preference in `localStorage` to OFF.
5. System asks the user to confirm: "Clear cached offline data?"
6. If user confirms, system purges all user-scoped data from IndexedDB (all keys prefixed with the current user's email).
7. System displays a toast: "Offline caching disabled. Your data will not be stored locally."
8. System stops automatically persisting API responses to IndexedDB.
9. If the server becomes unreachable, the offline middleware will not serve cached data for this user.

### A2: User Re-Enables Offline Caching

**Trigger:** User toggles "Offline Caching" back to ON in Settings
**Flow:**

1. User opens Settings and toggles "Offline Caching" to ON.
2. System registers the preference in `localStorage`.
3. System immediately triggers a cache population by fetching the current user info, collections list, and the active collection info from the server.
4. System persists the fetched data to IndexedDB under user-scoped keys.
5. System displays a toast: "Offline caching enabled."
6. Automatic caching resumes on all subsequent successful API responses.

### A3: Service Worker Not Supported

**Trigger:** Browser does not support service workers
**Flow:**

1. System detects that service workers are not available.
2. System displays the "Offline Caching" toggle in a disabled state with a tooltip: "Offline caching is not supported in this browser."
3. Automatic caching still operates (IndexedDB writes work without a service worker), but the app shell will not load without network.

### A4: IndexedDB Not Available

**Trigger:** IndexedDB is not accessible (private browsing, storage quota exceeded)
**Flow:**

1. System catches the IndexedDB write error silently on the first cache attempt.
2. System displays a warning toast: "Could not cache data locally. Offline mode may not work correctly."
3. The system retries on subsequent API responses.

### A5: Cache Population Fails (Network Error During Re-Enable)

**Trigger:** Network fails during initial fetch when re-enabling (A2, step 3)
**Flow:**

1. System catches the network error.
2. System keeps the preference as ON.
3. System displays a toast: "Offline caching enabled. Data will be cached on your next browsing session."
4. Automatic caching proceeds on subsequent successful API responses.

## Postconditions

### Success Postconditions

- IndexedDB `linkweave-offline` database contains the user's collection data under user-scoped keys
- All future successful GET responses for auth, collections, and collection info are automatically persisted to IndexedDB
- The cached data is namespaced by the user's email, ensuring isolation from other users

### Failure Postconditions

- If IndexedDB is unavailable, no data is cached and offline mode will not work
- The system will retry on the next successful API response

## Business Rules

### BR-047-1: Enabled by Default

Offline caching is enabled by default for all authenticated users. No explicit user action is required to start caching. This ensures the cache is populated before an unexpected outage occurs.

### BR-047-2: User-Scoped Cache Keys

All IndexedDB entries are prefixed with the authenticated user's email address. This ensures that even if multiple users share the same browser and the purge-on-logout fails, no user can access another user's cached data. The key format is:
- `{email}:user-info` — cached `UserInfoJson`
- `{email}:collections` — cached `CollectionSummaryJson[]`
- `{email}:collection-info:{collectionId}` — cached `CollectionInfoJson`

### BR-047-3: Automatic Cache Updates

Caching is transparent and automatic. The user does not need to trigger manual syncs — every successful API GET response updates the cache.

### BR-047-4: Per-Browser Setting

The offline caching preference is stored in `localStorage`, meaning it is per-browser and per-origin. Disabling it in Chrome does not affect Firefox.

### BR-047-5: Cache Scope

Only the following data is cached:
- `UserInfoJson` (email, name, defaultCollectionId — no passwords or auth tokens)
- `CollectionSummaryJson[]` (collection list)
- `CollectionInfoJson` (bookmarks, folders, tags for each visited collection)

### BR-047-6: Identity Validation on Cache Read

When serving cached data (UC-048), the offline middleware must verify that the email embedded in the cache key matches the email of the last-authenticated user (stored as `{email}:user-info`). If the emails do not match, the cache is treated as empty and the user is redirected to login.
