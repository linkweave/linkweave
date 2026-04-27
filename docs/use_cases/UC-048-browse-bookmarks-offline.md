# Use Case: Browse Bookmarks Offline

## Overview

**Use Case ID:** UC-048
**Use Case Name:** Browse Bookmarks Offline
**Primary Actor:** User
**Goal:** Access, browse, search, and filter cached bookmarks when the server is unreachable
**Status:** Draft

**Traces to:** FR-054, FR-055, FR-056, FR-057, FR-058, FR-061

## Preconditions

- User has previously authenticated in this browser (the system automatically caches data on every online session — see UC-047)
- The browser has cached collection data in IndexedDB from a previous online session, stored under user-scoped keys (`{email}:*`)
- The service worker has cached the app shell (HTML, JS, CSS, assets)

## Main Success Scenario

1. User opens or refreshes the Chainlink application in the browser while the server is unreachable.
2. Service worker serves the app shell from the Cache Storage.
3. Vue application boots; the router guard triggers `initializeSession()`.
4. System detects that the network request to `GET /api/auth/me` failed (TypeError: network error).
5. System loads the cached `UserInfoJson` from IndexedDB (key `{email}:user-info`) and populates the auth store. The email from this cached record becomes the user identity for all subsequent cache lookups.
6. Router guard recognizes the user as authenticated and proceeds to the default collection route.
7. System detects that the network request to `GET /api/collections/{id}` failed.
8. Offline middleware intercepts the failure, constructs the user-scoped key `{email}:collection-info:{collectionId}`, and loads `CollectionInfoJson` from IndexedDB. The middleware validates that the email in the cache key matches the cached user identity before serving data.
9. Collection store populates with cached bookmarks, folders, and tags.
10. System sets the offline state to `isOffline = true`.
11. System displays the Offline Banner at the top of the page: "You're offline. Showing cached data. Last synced X minutes ago."
12. System disables all write-operation buttons (Add Bookmark, New Folder, edit/delete/move actions).
13. User browses the bookmark list, selects folders in the sidebar, filters by tags, and searches using the search bar — all operating on the cached data in Pinia stores.
14. User clicks a bookmark URL to navigate to the external website (requires internet for the target site, but the click is handled by the browser's normal link behavior).

## Alternative Flows

### A1: No Cached Data Available

**Trigger:** IndexedDB has no cached collection data for the requested collection (step 8)
**Flow:**

1. Offline middleware returns no cached data.
2. Collection store remains empty.
3. Offline Banner shows: "You're offline. No cached data available. Connect to the internet to load your bookmarks."
4. Bookmark list shows the standard empty state message.
5. User can only navigate to the login page or try again.

### A2: No Cached User Info (Never Authenticated, or Logged Out Before Outage)

**Trigger:** IndexedDB has no `{email}:user-info` entry (step 5)
**Flow:**

1. Auth store cannot populate user data.
2. Router guard redirects to the login page.
3. Login page displays normally but login will fail without network.
4. User sees a message: "You are offline. Please connect to the internet to sign in."

This covers three sub-scenarios:
- **Never logged in on this browser**: No cache exists at all.
- **Explicitly logged out before the outage**: Logout purged the `{email}:*` keys from IndexedDB (FR-060).
- **Different browser**: Offline cache is per-browser (same-origin + IndexedDB scope). Logging in on Chrome does not create a cache in Firefox.

### A3: User Attempts a Write Operation

**Trigger:** User clicks a disabled write button (e.g., "Add Bookmark") (step 12)
**Flow:**

1. The button is visually disabled and non-interactive (grayed out, `pointer-events: none`).
2. If the user somehow triggers a write action (e.g., via keyboard shortcut), the system displays a toast: "This action is not available while offline."

### A4: User Switches Collections While Offline

**Trigger:** User selects a different collection from the collection switcher (between step 12 and 14)
**Flow:**

1. System updates `currentCollectionId` in the collection store.
2. Collection store watcher triggers `fetchCollectionInfo()` for the new collection ID.
3. Network request fails → offline middleware loads `CollectionInfoJson` from IndexedDB for the new collection ID.
4. If cached data exists for that collection, the view updates with the new collection's bookmarks, folders, and tags.
5. If no cached data exists, the collection view shows the empty state (see A1).

### A5: User Searches Bookmarks While Offline

**Trigger:** User types a search query into the search bar (between step 12 and 14)
**Flow:**

1. Search operates entirely client-side using the existing `filteredBookmarks` computed property in the bookmark store.
2. Search results update reactively as the user types, matching against cached bookmark titles, URLs, descriptions, and tag names.
3. No network request is needed — search is purely local.

### A6: Partial Data (Only Some Collections Cached)

**Trigger:** User navigates to a collection that was visited online but another collection was not (step 8)
**Flow:**

1. Offline middleware checks IndexedDB for the specific user-scoped key `{email}:collection-info:{collectionId}`.
2. If found, returns the cached data — use case proceeds normally.
3. If not found, the collection shows empty state — see A1.

### A7: Cross-User Cache Mismatch (Failed Purge)

**Trigger:** A different user previously logged in on the same browser and their data was not fully purged on logout (step 5)
**Flow:**

1. System loads `UserInfoJson` from IndexedDB — this identifies the cached user (e.g., `alice@example.com`).
2. Offline middleware uses this email to scope all subsequent cache reads (`alice@example.com:*`).
3. Even if another user's data (e.g., `bob@example.com:*`) exists in IndexedDB from a failed purge, it is never accessed because the keys are different.
4. Use case proceeds normally with the correct user's data.

### A8: Tab-Close Without Logout (Accepted Edge Case)

**Trigger:** User A closes the browser tab without logging out, and User B later opens the app on the same browser while the server is down (step 5)
**Flow:**

1. System loads `UserInfoJson` from IndexedDB — this is User A's data (e.g., `alice@example.com`).
2. The offline middleware uses Alice's email to scope all cache reads.
3. User B sees Alice's cached bookmarks (read-only) — the system has no way to distinguish User B from User A without contacting the server.
4. This is an **accepted security trade-off** (see C-009). The data shown is stale and read-only; no modifications are possible.
5. When connectivity is restored, the server session cookie will be validated: if User A's session expired, the system redirects to login; if User A's session is still valid, User B continues seeing User A's data (same behavior as a shared online session).

**Mitigations in place:**
- Data is read-only — no modifications, deletions, or shares can occur.
- User-scoped keys prevent accessing any other user's data.
- Explicit logout purges the cache completely (FR-060).
- Same-origin policy prevents other websites from reading IndexedDB.

## Postconditions

### Success Postconditions

- User sees their cached bookmark collection with all bookmarks, folders, and tags
- All read-only interactions work: browsing, folder navigation, tag filtering, search
- The offline banner is visible indicating the read-only, potentially stale state
- The "Last synced" timestamp is displayed

### Failure Postconditions

- If no cache exists, the user sees the login page or empty state with a clear offline message
- No data loss occurs — the cached data remains intact in IndexedDB

## Business Rules

### BR-048-1: Read-Only Offline

All write operations (create, update, delete, move, share) are disabled in offline mode. No write queueing or background sync is attempted. The user must be online to make changes.

### BR-048-2: Cache Serves Stale Data

The offline display may show stale data. The "Last synced" timestamp helps the user assess freshness. No warning is shown for individual stale items — the banner is sufficient.

### BR-048-3: Search Is Local

Search operates on the cached data in the Pinia store using the existing `parseSearchQuery` and `bookmarkMatchesTerms` utilities. No server-side search is needed or attempted while offline.

### BR-048-4: Only Previously Visited Collections Are Cached

Only collections that were loaded while the user was online (triggering `fetchCollectionInfo()`) have data in IndexedDB. Switching to an unvisited collection while offline shows the empty state.

### BR-048-5: Bookmark Links Navigate Normally

Clicking a bookmark URL while offline will open the target URL in a new browser tab. Whether the target page loads depends on the user's internet connectivity and the target server — this is outside Chainlink's control.

### BR-048-6: User-Scoped Data Isolation

All cache reads are scoped by the cached user's email address. The offline middleware constructs cache keys as `{email}:collection-info:{collectionId}` and `{email}:collections`. A cache key for one user will never match data belonging to another user, even if both users' data coexists in IndexedDB due to a failed logout purge. The user identity is established from the `{email}:user-info` entry, which is the first cache read on offline boot.

### BR-048-7: Offline Auth Trust Model

Offline authentication does not re-validate credentials or session cookies. The system trusts the browser's IndexedDB cache as evidence of a prior successful login. This means: (1) at least one online login must have occurred in this browser, (2) the user must not have explicitly logged out (which purges the cache), (3) there is no way to distinguish a different person opening the same browser after a tab-close without logout. This trade-off is accepted because the data is read-only and represents what the user already saw on screen during their last online session. See C-009.
