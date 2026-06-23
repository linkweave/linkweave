# Use Case: Save Bookmark from Mobile Share Sheet

## Overview

**Use Case ID:** UC-094
**Use Case Name:** Save Bookmark from Mobile Share Sheet
**Primary Actor:** User (on an Android device, reading a page in any app that exposes the system Share sheet)
**Goal:** Save the page currently being read as a LinkWeave bookmark via the OS share sheet, without copy-pasting the URL
**Status:** Open

**Notes:**
- The feature is implemented via the [Web Share Target API](https://developer.chrome.com/docs/web-platform/web-share-target): the LinkWeave PWA declares a `share_target` entry in its Web App Manifest, so Android lists it in the system Share sheet alongside other apps.
- Share is `method: GET` with `url` and `title` params only. No files, no selected text — bookmarks are URL + title.
- **Android Chrome only surfaces the PWA in the share sheet if the PWA is installed.** Installation requires real 192/512/maskable icons, screenshots, a valid manifest, and a registered service worker. This makes the "installable PWA" work a prerequisite for this use case, not a separate goal.
- **iOS is not supported.** iOS Safari does not implement Web Share Target. iOS users are no worse off than today (they copy/paste the URL manually). This is a platform limitation, not a defect.
- The save reuses the existing `POST /api/bookmarks` endpoint. There are no backend changes.

**Traces to:** FR-092 (Save Bookmark from Mobile Share Sheet)

**Implementation Plan:** See [`../mobile-share.md`](../mobile-share.md) for the architectural approach, the manifest/route/dialog wiring, file-by-file changes, and the build sequence.

## Preconditions

- The user is on an **Android** device using Chrome. (Desktop Chrome supports share_target technically but offers no benefit over the browser extension, FR-043; iOS is out of scope.)
- The user has **installed** the LinkWeave PWA (added to home screen) at least once. Without installation, LinkWeave does not appear in the system share sheet.
- The LinkWeave instance is reachable over HTTPS (a hard PWA requirement; the service worker will not register otherwise).
- The user has at least one collection (the auto-provisioned default satisfies this — FR-001).

## Main Success Scenario

1. User is viewing a web page in any Android app (typically Chrome) and invokes the system Share action.
2. Android renders the share sheet. LinkWeave appears as a target because the PWA is installed and its manifest declares `share_target`.
3. User taps LinkWeave.
4. Android Chrome opens the installed PWA and navigates it to the manifest's `share_target.action` (`/share`) with query params `?url=<encoded>&title=<encoded>`.
5. The Vue router matches `/share`; `router.beforeEach` runs `initializeSession()` → `fetchCurrentUser()` resolves successfully (the user is authenticated).
6. `ShareTargetView` reads `route.query.url` and `route.query.title` and validates `url` against the existing bookmark zod URL schema.
7. `ShareTargetView` opens `BookmarkDialog` in create mode, pre-filled with `url` and `title`, the user's default collection pre-selected (FR-004), and no folder/tags.
8. User optionally adjusts the collection, folder, and tags, then confirms Save.
9. `useBookmarkStore.createBookmark()` issues `POST /api/bookmarks` with a `BookmarkSaveJson`. The server enforces collection access via `AuthorizationService` and returns `BookmarkJson`.
10. The app navigates to the bookmark's collection (`name: 'collection'`) so the user lands on a useful view rather than a bare `/share` route.

## Alternative Flows

### A1: PWA Not Installed

**Trigger:** The user has never installed the LinkWeave PWA (step 2)
**Flow:**

1. Android does not list LinkWeave in the share sheet — only installed PWAs with `share_target` appear.
2. Nothing happens in LinkWeave. The user is unaware the feature exists.
3. Mitigation (optional, out of scope for v1): an in-app install prompt when the `beforeinstallprompt` event fires, explaining that installation enables share-to-save.

### A2: Not Authenticated at Share Time

**Trigger:** `fetchCurrentUser()` rejects with 401 (session expired or never established on this PWA) at step 5
**Flow:**

1. `router.beforeEach` stashes `{ url, title }` into `sessionStorage` under `linkweave:pending-share`.
2. The router redirects to `/login`.
3. The user completes login (form-based, or Google OIDC round-trip).
4. On successful login, a post-login hook reads `linkweave:pending-share`, removes it, and redirects to `/share?url=…&title=…`.
5. The use case continues at step 6.

> `sessionStorage` (not `localStorage`) is used deliberately: the share intent is a one-shot, in-flight action that should not survive a tab close. See BR-094-4.

### A3: Invalid or Missing `url` Param

**Trigger:** `url` is absent, empty, or fails the zod URL schema (e.g. the sharing app sent only `text`, or a non-`http(s)` scheme) at step 6
**Flow:**

1. `ShareTargetView` does not open the dialog blindly.
2. It renders a small manual-paste fallback form: "Couldn't read the shared link — paste a URL to continue."
3. The user pastes a valid URL and confirms.
4. The use case continues at step 7 with the pasted URL.

### A4: Save Fails (Network or Server Error)

**Trigger:** `POST /api/bookmarks` returns an error, or the request fails (step 9)
**Flow:**

1. The existing error-handling path applies (FR-040): a toast distinguishes auth/session errors (401/403) from backend/network problems (5xx, network failure).
2. The dialog stays open with the entered values preserved so the user can retry without re-entering.
3. No bookmark is written.

### A5: Offline at Share Time

**Trigger:** `navigator.onLine === false` when the user confirms Save (step 9)
**Flow:**

1. Offline read-only mode applies (FR-056): write operations are disabled.
2. The offline banner (FR-055) is shown.
3. The save is **not queued** — offline write queues are explicitly deferred (see `mobile-share.md` §13 and `offlinemode.md` FR-056).
4. The user must retry once back online. The pre-filled dialog values are retained in-memory while the tab is open.

### A6: iOS User

**Trigger:** The user is on iOS Safari
**Flow:**

1. iOS does not implement Web Share Target. LinkWeave never appears in the iOS share sheet regardless of installation or manifest configuration.
2. This is a documented platform limitation (BR-094-5), not a failure.
3. The iOS user falls back to the pre-existing manual flow: copy the URL from the source app, open LinkWeave, paste into the bookmark dialog. No regression versus today.

### A7: User Cancels

**Trigger:** The user dismisses the pre-filled dialog without saving (any time after step 7)
**Flow:**

1. The dialog closes.
2. The app navigates to home (`name: 'home'`), which in turn auto-navigates to the user's default collection.
3. No bookmark is written. The share params are discarded.

## Postconditions

### Success Postconditions

- A new bookmark is persisted in the selected collection via the existing `POST /api/bookmarks` endpoint, identical to a bookmark created through the web UI or the browser extension.
- The user is viewing that bookmark's collection.
- No new backend surface, auth mechanism, or data format has been exercised — the share path is a thin frontend entry point onto the existing create flow.

### Failure Postconditions

- No bookmark is written.
- If authentication was required (A2), the pending share was held in `sessionStorage` for the duration of the login attempt and cleared on success or tab close.
- The user is informed of any error via the standard toast mechanism (FR-040).

## Business Rules

### BR-094-1: Share Target Is GET, URL + Title Only

The manifest `share_target` MUST use `method: GET` with params `{ title, url }`. Files, selected text, and `multipart/form-data` POST are out of scope — bookmarks are URL + title. If a sharing source sends `text` instead of `url`, `ShareTargetView` MAY fall back to `text`, but the v1 cut targets `url` only.

### BR-094-2: Installation Is a Prerequisite

LinkWeave appears in the Android share sheet ONLY if the PWA is installed, and installation requires real 192/512/maskable icons, screenshots, a valid manifest, and a registered service worker. The "installable PWA" work is therefore part of this feature's scope, not an optional add-on.

### BR-094-3: No Backend Changes

The save MUST reuse the existing `POST /api/bookmarks` endpoint with `BookmarkSaveJson`. No new API endpoint, auth mechanism, or DTO is introduced for the share path. `AuthorizationService` enforces collection access exactly as it does for the web UI and extension.

### BR-094-4: Pending Share Lives in `sessionStorage`

When a share arrives while unauthenticated, the intent MUST be stashed in `sessionStorage` (key `linkweave:pending-share`), not `localStorage` or an in-memory store. Rationale: the intent is a one-shot, in-flight action that must survive the OIDC redirect round-trip but must NOT persist beyond the tab. The entry is removed on first read.

### BR-094-5: iOS Is Unsupported by Design

iOS Safari does not implement Web Share Target. The feature MUST NOT attempt a workaround (e.g. URL-scheme hacks). iOS users continue to save via copy/paste. This limitation MUST be documented in user-facing help text. No regression versus the pre-feature state is acceptable.

### BR-094-6: The `url` Param Is Data, Not a Navigation Target

`ShareTargetView` MUST treat the `url` query param as bookmark data to be validated and saved, never as a destination for `window.location` or `window.open`. This rules out open-redirect attacks via crafted share payloads.
