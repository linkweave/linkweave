# Use Case: View Bookmark Screenshot Previews

## Overview

**Use Case ID:** UC-054
**Use Case Name:** View Bookmark Screenshot Previews
**Primary Actor:** User
**Secondary Actors:** Collection Owner (enables/disables feature per collection), Playwright Screenshot Service (sidecar)
**Goal:** Display a visual screenshot preview of each bookmarked page so that the user can identify bookmarks at a glance without reading titles or URLs.
**Status:** Open

## Traceability

**Maps to:** FR-065, NFR-015, C-012, C-013
**Design document:** [plans/screenshot-previews.md](../plans/screenshot-previews.md)

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection containing bookmarks with valid HTTP(S) URLs.
- The Playwright screenshot sidecar service is running and reachable from the Quarkus API (configurable; the feature degrades gracefully if the service is unavailable).

## Main Success Scenario

1. The Collection Owner enables the "Screenshot Previews" toggle in the collection settings.
2. The collection's `screenshotEnabled` flag is set to `true` and persisted.
3. The "tiles" layout option becomes available in the Settings dialog for all users who have access to this collection.
4. The user selects the "tiles" layout in the Settings dialog.
5. The frontend renders bookmarks as a responsive grid of tile cards. Each tile card shows:
   - A screenshot cover image (lazy-loaded) occupying the top portion of the card.
   - The bookmark title overlaid at the bottom.
   - The bookmark favicon and context menu (edit, move, delete) — same actions as the existing card layouts.
6. For each visible tile, the frontend requests the screenshot via `GET /api/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot` with `loading="lazy"`.
7. The backend checks the on-disk screenshot cache (keyed by SHA-256 of the canonical URL):
   - **Cache hit (fresh):** Return the cached JPEG bytes with `Cache-Control: private, max-age=86400`.
   - **Cache miss:** Forward the request to the Playwright sidecar (`POST /screenshot {url, width, height, format}`), store the result in the cache, and return the bytes.
8. Screenshots for bookmarks that are created or updated are captured **asynchronously** (outside the main CRUD path) so that the user's create/edit operation completes without delay.
9. When a bookmark's URL is changed, the old screenshot cache entry is invalidated and a new capture is triggered asynchronously.

## Alternative Flows

### A1: Screenshot Sidecar Unreachable

**Trigger:** The Playwright screenshot service is down or unreachable when the backend tries to fetch a screenshot (step 7, cache miss).
**Flow:**

1. The backend writes a "no screenshot" negative cache entry with a short TTL (default 12 hours) to suppress retry storms.
2. The backend returns HTTP 204 No Content.
3. The frontend renders a styled placeholder in the tile card (gradient background + favicon + title).
4. Use case ends. The user can still open, edit, and delete the bookmark normally.

### A2: Feature Disabled per Collection

**Trigger:** The user views a collection where `screenshotEnabled` is `false`.
**Flow:**

1. The "tiles" layout option is hidden from the Settings dialog.
2. If the user previously selected "tiles" for another collection and switches to this one, the layout falls back to "grid".
3. Use case ends.

### A3: SSRF Guard Triggered

**Trigger:** A bookmark URL resolves to a disallowed IP range (private, loopback, link-local, cloud-metadata).
**Flow:**

1. The backend refuses to request a screenshot from the sidecar.
2. A negative cache entry is written.
3. HTTP 204 No Content is returned.
4. The frontend renders the placeholder.
5. Use case ends.

### A4: Screenshot Capture Fails (Page Load Error)

**Trigger:** The Playwright sidecar reports that the target page could not be loaded (DNS failure, timeout, HTTP error, etc.).
**Flow:**

1. The sidecar returns an error response.
2. The backend writes a negative cache entry.
3. HTTP 204 is returned to the frontend.
4. The frontend renders the placeholder.
5. Use case ends.

### A5: Owner Disables Screenshots on a Collection

**Trigger:** The Collection Owner toggles "Screenshot Previews" off.
**Flow:**

1. The collection's `screenshotEnabled` flag is set to `false`.
2. Existing cached screenshots remain on disk (not purged immediately — they expire naturally via the cleanup job or manual cache wipe).
3. All users viewing this collection lose the "tiles" layout option.
4. The layout falls back to "grid" for any user who had "tiles" selected.
5. Use case ends.

### A6: Non-Owner Attempts to Toggle Screenshots

**Trigger:** A user with shared (non-owner) access tries to change the screenshot toggle.
**Flow:**

1. The system denies the change (owner-only operation, enforced by `AuthorizationService.requireOwnerAccess`).
2. Use case ends.

## Postconditions

### Success Postconditions

- Tile cards display screenshot previews for bookmarks where a screenshot could be captured.
- Bookmarks without screenshots display a styled placeholder; all bookmark operations remain functional.
- The backend has not been induced to make requests to disallowed network ranges.
- Screenshots for newly created or updated bookmarks are being captured asynchronously in the background.

### Failure Postconditions

- The placeholder is shown; bookmark functionality (open, edit, delete, move) is unaffected.
- The tiles layout continues to work with placeholders for any missing screenshots.

## Business Rules

### BR-114: Owner-Only Screenshot Toggle

Only the Collection Owner may enable or disable screenshot previews for a collection.

### BR-115: Default Off

The `screenshotEnabled` flag defaults to `false` on new and existing collections. The owner must explicitly opt in.

### BR-116: Screenshot Capture Is Asynchronous

Screenshots must never be captured synchronously during bookmark creation or editing. The CRUD response must return immediately; the screenshot is captured via an asynchronous background process.

### BR-117: SSRF Protection on Screenshot Fetches

The same SSRF guards that apply to the favicon proxy (BR-100) apply to screenshot fetches: reject private, loopback, link-local, and cloud-metadata IPs; allow only `http` and `https` schemes.

### BR-118: Screenshot Cache Key

Cache entries are keyed by `SHA-256(canonicalOrigin + normalizedPath)` of the bookmark URL. Multiple bookmarks pointing to the same URL share a single cache entry.

### BR-119: Negative Cache Entries

When a screenshot cannot be captured (sidecar unreachable, SSRF guard, page load failure), a negative cache entry with a short TTL (default 12 hours) prevents retry storms. The entry is transparent to the frontend (HTTP 204).

### BR-120: Screenshot Format and Quality

Screenshots are captured as JPEG at quality 60, viewport 1280×800, device scale factor 1. This yields approximately 50–150 KB per screenshot, balancing visual quality with storage efficiency.

### BR-121: Tile Layout Availability

The "tiles" layout option is only available when the current collection has `screenshotEnabled=true`. When the user switches to a collection where screenshots are disabled, the layout falls back to "grid".

### BR-122: Screenshot Invalidation on URL Change

When a bookmark's URL is updated, the old screenshot cache entry is deleted before the new screenshot capture is triggered. This ensures stale screenshots are not served.
