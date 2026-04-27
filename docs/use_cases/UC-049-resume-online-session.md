# Use Case: Resume Online Session

## Overview

**Use Case ID:** UC-049
**Use Case Name:** Resume Online Session
**Primary Actor:** User
**Goal:** Automatically detect restored network connectivity, refresh cached data from the server, and transition out of offline mode seamlessly
**Status:** Draft

**Traces to:** FR-059, FR-060, FR-061

## Preconditions

- User is currently in offline mode (UC-048 active, offline banner visible)
- The system has been automatically caching data (UC-047 default behavior)
- The user has been browsing cached data

## Main Success Scenario

1. The user's network connectivity is restored (browser fires `online` event).
2. The `useOffline` store detects the `online` event and sets `isOffline = false`.
3. System dismisses the offline banner with a brief "Back online" success toast.
4. System re-enables all write-operation buttons (Add Bookmark, New Folder, edit/delete/move actions).
5. System triggers a background refresh of the current collection data by calling `fetchCollectionInfo()` with the active `currentCollectionId`.
6. Server responds with fresh `CollectionInfoJson`.
7. Collection store updates with the latest bookmarks, folders, and tags from the server.
8. System persists the fresh data to IndexedDB under user-scoped keys (replacing the stale cache for this user only, without affecting any other user's cached data).
9. System updates the "Last synced" timestamp to the current time.
10. User continues browsing with live data and full read-write capabilities.

## Alternative Flows

### A1: Server Still Unreachable (DNS Issues, Server Down)

**Trigger:** The network is available but the server is still unreachable (step 5 or 6)
**Flow:**

1. The `fetchCollectionInfo()` call fails (network error or HTTP 5xx).
2. System keeps `isOffline = true` and the banner remains visible.
3. System displays a toast: "You appear to be back online, but the server could not be reached."
4. The user continues in offline mode with cached data.
5. The system retries the fetch on the next user action (e.g., collection switch) or on a periodic interval (every 30 seconds for up to 5 minutes).

### A2: Session Expired While Offline

**Trigger:** Server responds with HTTP 401 to the refresh request (step 6)
**Flow:**

1. Auth store receives the 401 response.
2. System clears all Pinia stores and purges only the current user's data from IndexedDB (all keys prefixed with `{email}:`), leaving other users' cached data untouched.
3. Router redirects to the login page.
4. System displays a toast: "Your session has expired. Please sign in again."

### A3: Data Changed on Server While Offline

**Trigger:** The fresh data from the server differs from the cached data (step 7)
**Flow:**

1. Collection store updates with the new server data, replacing the stale cache.
2. The UI updates reactively to show the current state.
3. No explicit "data has changed" notification is needed — the update is seamless.
4. If the user had selected a folder or tag filter that no longer exists (e.g., a folder was deleted by another user on a shared collection), the filter resets to "All Bookmarks."

### A4: User Was on Login Page When Connectivity Restored

**Trigger:** Connectivity restores while user is on the login page (step 1)
**Flow:**

1. The offline banner is dismissed.
2. The login form becomes functional again.
3. No data refresh is needed since no collection was loaded.
4. User proceeds with normal login.

### A5: Multiple Rapid Connectivity Changes

**Trigger:** Network flaps (goes online → offline → online rapidly) (step 1)
**Flow:**

1. The `useOffline` store debounces `online`/`offline` events with a 2-second delay.
2. Only the final stable state is acted upon.
3. This prevents flickering the banner and triggering unnecessary data refreshes.

## Postconditions

### Success Postconditions

- Offline banner is dismissed
- Collection data is refreshed from the server
- IndexedDB cache is updated with fresh data
- All read-write capabilities are restored
- "Last synced" timestamp reflects the current time

### Failure Postconditions

- If the server is unreachable, the user remains in offline mode
- If the session expired, the user is redirected to login with cached data purged

## Business Rules

### BR-049-1: Seamless Transition

The transition from offline to online should be as seamless as possible. The user should not need to manually refresh the page or take any action to get live data.

### BR-049-2: No Data Merge

There is no attempt to merge offline changes with server data. Since all write operations are disabled in offline mode, there can be no conflicts. The server data always wins.

### BR-049-3: Debounced Connectivity Detection

Network status changes are debounced by 2 seconds to avoid reacting to transient connectivity fluctuations.

### BR-049-4: User-Scoped Cache Purge on Logout

When the user logs out (either manually or due to session expiry), only the current user's cached data is purged from IndexedDB — specifically all entries with the `{email}:` key prefix. Other users' cached data (from different `{email}:` prefixes) is left untouched. This ensures data isolation even on shared browsers. (See also FR-060, FR-061, UC-047 A1.)

### BR-049-5: Retry Strategy

If the server is unreachable after the network comes back, the system retries up to 10 times at 30-second intervals, then stops retrying and waits for the next user action.
