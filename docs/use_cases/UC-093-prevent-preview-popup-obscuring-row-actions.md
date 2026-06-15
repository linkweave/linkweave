# Use Case: Prevent Preview Popup from Obscuring Row Actions

## Overview

**Use Case ID:** UC-093
**Use Case Name:** Prevent Preview Popup from Obscuring Row Actions
**Primary Actor:** User
**Goal:** Ensure the bookmark row's hover actions (the "⋯" menu that hosts Edit / Move / Refresh Preview / Delete) remain visible and reachable while the screenshot preview popup is open in the list layout.
**Status:** Open

## Traceability

**Refines:** UC-054 (View Bookmark Screenshot Previews) — fixes a regression introduced by the list-view hover popup.
**Maps to:** NFR-015 (Usability)

---

## Preconditions

- The user is authenticated and has access to a collection.
- The collection has `screenshotEnabled = true` (UC-054, BR-115).
- The user has enabled previews in the toolbar (`ui.previewsEnabled = true`).
- The layout is **list**.
- The device supports real hover (`@media(hover: hover)`).
- The viewport is narrow enough that the 340 px preview popup cannot sit fully in the right gutter beside the row (i.e. the popup must overlap the row's right edge).

---

## Problem Statement

`BookmarkPreviewPopup.vue` (`measure()`) pins the popup to the right edge of the content pane. By design it overlaps the **right** side of the row, which is where the "⋯" menu button lives (`BookmarkCard.vue`, `ml-auto` title-row button). The popup is `pointer-events: none`, so clicks pass through to the row, but the menu button is:

1. **Visually obscured** by the 340 px popup, and
2. **Only rendered on hover** (`opacity-0` → `group-hover:opacity-100`),

so the user has no visible target and cannot discover the Edit / Move / Delete / Refresh Preview actions for the bookmark they are currently inspecting. The popup and the menu are mutually triggered by the same row hover, which is the root of the conflict.

The chosen trade-off (documented in `BookmarkPreviewPopup.vue` lines 52–57) currently treats the menu/stats cells as the "least important" cells to sacrifice. The menu is in fact the **only** entry point to destructive and editing operations, so it must not be sacrificed.

---

## Main Success Scenario

1. User hovers a bookmark row in list layout (previews enabled).
2. The preview popup appears.
3. The row's "⋯" menu button remains **fully visible** and **clickable** — it is not covered, clipped, or pushed off-screen by the popup.
4. User moves the pointer onto the menu button without the popup disappearing (no flicker / no intent-cancellation).
5. User opens the menu and selects Edit (or Move / Delete / Refresh Preview).
6. The chosen action completes normally; the popup closes when the row is left.

## Alternative Flows

### A1: Wide viewport with a real right gutter

**Trigger:** The content pane is wide enough that the popup fits beside the row without overlapping it.
**Flow:**

1. The popup is placed in the right gutter (current right-aligned behaviour).
2. No overlap occurs; the menu button is visible.
3. Use case ends. This is the no-op path; the fix must not regress it.

### A2: User hovers a row whose menu has already been activated (radix dropdown open)

**Trigger:** The user has clicked the "⋯" button and the dropdown is open, then the pointer lingers on the row.
**Flow:**

1. The dropdown stays open and interactive; the popup must not cover the dropdown items (the dropdown is `z-50`).
2. User selects an item or presses Escape.
3. Use case ends.

### A3: Touch / no-hover device

**Trigger:** `@media(hover: none)` — previews are on but the hover popup never opens (see `BookmarkCard.vue` hover-intent gate, lines 293–315).
**Flow:**

1. No popup is shown; the menu button is always visible on touch (no `opacity-0` gate).
2. Use case ends. The fix must not affect this path.

---

## Postconditions

### Success Postconditions

- The "⋯" menu button on a hovered list row is never visually obscured by the preview popup.
- The popup continues to anchor against the right edge of the content pane and never flips onto the fixed sidebar (left edge invariant preserved).
- Wide-viewport behaviour (popup in the gutter, no overlap) is unchanged.
- Touch/no-hover behaviour is unchanged.

### Failure Postconditions

- The menu button remains hidden on hovered rows in cramped layouts; editing/deleting a bookmark whose preview is open is not discoverable without moving the pointer away.

---

## Business Rules

### BR-093-1: Row Actions Are Never Obscured

While the preview popup is open for a hovered row, every interactive control that belongs to that row — specifically the "⋯" menu button (Edit / Move / Refresh Preview / Delete) — MUST remain visually unobstructed and within its normal hit target. Obscuring the menu in exchange for showing the popup is not an acceptable trade-off.

### BR-093-2: Left-Edge Invariant Preserved

The popup MUST NOT flip onto the fixed sidebar. The current constraint `minLeft = max(VIEWPORT_PAD, cb.left + VIEWPORT_PAD)` is retained; any repositioning happens within the content pane.

### BR-093-3: No Flicker on Pointer Transit

Moving the pointer from the row body onto the (now visible) menu button MUST NOT cancel the row's hover intent and MUST NOT close the popup prematurely. Hover-intent enter/leave timing must tolerate the pointer crossing from the row onto a row-child control.

### BR-093-4: Resolution Approach Is Implementation-Open

The fix may be implemented by any of (non-exhaustive): repositioning the popup so it leaves a clear corridor over the menu button; shrinking/reshaping the popup's overlap region; adding a non-overlapping hover-safe zone; deferring popup show until the pointer dwells outside the menu button's corridor; or making the popup dismissible on menu focus. The acceptance test (see below) is the source of truth, not the chosen technique.

---

## Acceptance Test

A Playwright test in the **chromium** project (hover-capable) shall, with previews enabled and a narrow list layout where the popup overlaps the row:

1. Hover a bookmark row.
2. Assert the preview popup is visible (`[data-testid="bookmark-preview-popup"]`).
3. Assert the row's menu button (`[data-testid="bookmark-menu-button"]`) is visible and its bounding box is **not** fully covered by the popup's bounding box (i.e. at least one corner/edge of the button lies outside the popup rect).
4. Click the menu button and assert the dropdown with the "Edit" item appears.

The test must also cover A1 (wide viewport): the popup sits in the gutter and no overlap assertion is needed beyond "popup visible, menu visible".

---

## Implementation Pointers

- `frontend/src/components/bookmark/BookmarkPreviewPopup.vue` — `measure()` and the `POPUP_W` / overlap trade-off comment (lines 52–61).
- `frontend/src/components/bookmark/BookmarkCard.vue` — the menu button (lines 462–497) and the hover-intent gate (lines 286–315).
- `frontend/src/composables/useBookmarkPreviewHover.ts` — enter/leave intent timing relevant to BR-093-3.
