# Use Case: View Preview on Compact Grouped View

## Overview

**Use Case ID:** UC-105
**Use Case Name:** View Preview on Compact Grouped View
**Primary Actor:** User
**Goal:** See a screenshot preview popup when hovering a compact bookmark row in the grouped layout, so that bookmarks can be identified visually without leaving the dense, multi-folder grouped view.
**Status:** Open

## Traceability

**Refines:** UC-054 (View Bookmark Screenshot Previews) — extends the list-layout hover popup to the grouped layout.
**Maps to:** FR-101, NFR-015
**Related:** UC-093 (Prevent Preview Popup Obscuring Row Actions) — the popup's action footer and toolbar clamp are reused unchanged.

---

## Context

The grouped layout (`BookmarkGroupedLayout.vue`) renders a collection's folder tree as a responsive grid of **group cards** — one card per root folder — each containing compact text rows (`GroupedBookmarkRow.vue`: favicon + title + ⋯ menu). It is the only layout that shows the full folder hierarchy at a glance, but unlike the **list** and **grid/tiles** layouts it currently shows **no screenshot previews at all**. This use case brings the existing hover preview popup (`BookmarkPreviewPopup.vue`) to those compact rows, reusing the same capture endpoint, cache, SSRF guards, and action footer already built for the list layout.

---

## Preconditions

- The user is authenticated and has access to a collection.
- The collection has `screenshotEnabled = true` (UC-054, BR-115).
- The user has enabled previews in the toolbar (`ui.previewsEnabled = true`).
- The layout is **grouped**.
- The device supports real hover (`@media(hover: hover)`).

## Main Success Scenario

1. The user browses a collection in the grouped layout with previews enabled.
2. The user hovers a compact bookmark row inside a group card.
3. After the hover-intent dwell, the frontend opens the `BookmarkPreviewPopup` for that bookmark — the same component used by the list layout.
4. The popup requests the screenshot via the existing `GET /api/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot` endpoint (cache hit or sidecar capture as in UC-054).
5. The popup displays the screenshot capture with its action footer (favicon · host link · ⋯ menu offering Edit / Move / Refresh Preview / Delete), identical to the list layout.
6. The user moves the pointer onto the popup's footer and opens an action (e.g. Edit) without the popup disappearing.
7. The user moves the pointer off the row and popup; the popup dismisses after the hide delay.

## Alternative Flows

### A1: No Screenshot Available

**Trigger:** The bookmark has no cached screenshot (sidecar unreachable, SSRF guard, page-load failure, or capture still pending) — step 4.

**Flow:**

1. The backend returns HTTP 204 (negative cache, UC-054 BR-119).
2. The popup renders the styled placeholder (gradient + favicon + title) instead of a capture.
3. The action footer remains fully usable.
4. Use case ends.

### A2: Touch / No-Hover Device

**Trigger:** `@media(hover: none)` — previews are on but the device has no hover (step 2).

**Flow:**

1. No popup is shown; the compact row's ⋯ menu is always visible (no `opacity-0` gate).
2. Use case ends. This path is identical to the list layout (UC-093 A3) and must not change.

### A3: Previews Disabled

**Trigger:** The collection has `screenshotEnabled = false`, or the user has not toggled previews on in the toolbar.

**Flow:**

1. No popup wiring is active on the grouped rows.
2. Use case ends.

### A4: Row Belongs to a Scrollable Group Card

**Trigger:** A group card contains more bookmarks than fit its `max-h-96` scroll viewport, so the hovered row is deep inside the card's scroll region.

**Flow:**

1. The popup anchors against the **content pane** (not the narrow group card column) so the 340 px popup is not clipped by the card boundary.
2. The popup's vertical clamp keeps it below the sticky toolbar (BR-093-6) and inside the viewport.
3. Scrolling the group card dismisses the popup (the hover target scrolls away).
4. Use case ends.

### A5: Batch Selection Active

**Trigger:** The user has entered selection mode (UC-074) while in a layout that supports it.

**Flow:**

1. The grouped layout does not participate in batch selection (`selectAvailable = false` for grouped, see `BookmarkListToolbar.vue`), so this flow is unreachable today.
2. If batch selection is ever extended to the grouped layout, the popup must be suppressed while selecting (the batch action bar takes precedence), matching the list-layout rule in UC-093.

## Postconditions

### Success Postconditions

- The hovered compact row shows a screenshot preview popup identical in behavior to the list layout.
- All bookmark actions (Edit / Move / Refresh Preview / Delete) remain reachable via the popup's footer.
- The popup never overlaps the sticky toolbar and never flips onto the fixed sidebar.

### Failure Postconditions

- The placeholder is shown (A1); bookmark functionality is unaffected.

## Business Rules

### BR-211: Reuse, Do Not Fork, the Preview Popup

The grouped layout MUST use the existing `BookmarkPreviewPopup.vue` component and `useBookmarkPreviewHover` composable. The capture endpoint, cache, SSRF guards, negative-cache, and action footer are shared with the list layout — no parallel preview pipeline is introduced.

### BR-212: Preview Gating

The popup appears only when ALL of the following hold: `screenshotEnabled = true` for the collection, `ui.previewsEnabled = true`, the layout is `grouped`, and the device supports real hover. If any condition is false, no popup wiring is active on the compact rows.

### BR-213: Content-Pane Anchoring

Because group cards are narrow (up to three per row), the popup MUST anchor against the content pane using the same right-edge clamping strategy as the list layout (UC-093), not against the card column. The popup must never be clipped by a card boundary or overflow:hidden container.

### BR-214: Sticky-Toolbar Exclusion Preserved

The popup MUST NOT overlap the sticky `bookmark-list-toolbar` (BR-093-6). The existing toolbar clamp (`useStickyToolbar`) applies unchanged. The group-card scroll region is an additional vertical bound only insofar as scrolling dismisses the popup by moving the hover target.

### BR-215: Action Footer Consistency

The popup footer MUST surface the same actions (favicon · host link · ⋯ menu) as the list layout (BR-093-1). When the popup covers the row's own ⋯ button, the footer menu is the reachable substitute.

## Acceptance Test

A Playwright test in the **chromium** project (hover-capable) shall, with previews enabled and the grouped layout active:

1. Hover a compact bookmark row (`[data-testid^="grouped-row-"]`).
2. Assert the preview popup is visible (`[data-testid="bookmark-preview-popup"]`).
3. Assert a sticky-toolbar control remains the hit-test target (`document.elementFromPoint`) — the popup is clamped below the toolbar.
4. Open the popup's footer menu (`[data-testid="bookmark-menu-button"]`) and assert the "Edit" item appears.
5. Assert that on a touch/no-hover emulation the popup does not appear (A2).

## Implementation Pointers

The grouped layout is the only layout that does not render `BookmarkCard.vue`; it renders `GroupedBookmarkRow.vue`, which has no preview wiring today. The change is almost entirely in the frontend:

1. **`GroupedBookmarkRow.vue`** — wire the hover-intent composable (`useBookmarkPreviewHover`) and emit the bookmark to a single shared popup, gated by `screenshotEnabled && previewsEnabled && !isTouch`. Mirror the `hoverPreviewActive` gate that `BookmarkCard.vue` uses for the list layout.
2. **`BookmarkGroupedLayout.vue`** — host a single `BookmarkPreviewPopup` instance (as `BookmarkList.vue` does for the list) and bind its `edit`/`move`/`delete`/`refreshPreview` events to the existing handlers bubbled via `emit`.
3. **`BookmarkPreviewPopup.vue`** — confirm the `measure()` clamp anchors against the content pane (not the card column) so BR-213 holds. The toolbar clamp (`useStickyToolbar`) already bounds the top edge; no sidebar/left-edge change is needed.
4. No backend changes: the screenshot endpoint, cache, and SSRF guards are reused verbatim.
