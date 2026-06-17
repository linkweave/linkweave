# Use Case: Prevent Preview Popup from Obscuring Row Actions

## Overview

**Use Case ID:** UC-093
**Use Case Name:** Prevent Preview Popup from Obscuring Row Actions
**Primary Actor:** User
**Goal:** Ensure the bookmark's row actions (Edit / Move / Refresh Preview / Delete) remain reachable while the screenshot preview popup is open in the list layout — even when the popup covers the row's own "⋯" menu.
**Status:** Implemented

> **Note on approach:** This use case was originally framed as "keep the row's ⋯ menu visible *beside* the popup". The shipped solution takes the opposite tack (BR-093-4): the popup deliberately covers the row's ⋯ and **hosts the same actions in its own footer**, so the covered control never needs to be reached. The business rules and scenarios below reflect that shipped design.

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

The original trade-off in `measure()` treated the menu/stats cells as the "least important" cells to sacrifice to the overlap. The menu is in fact the **only** entry point to destructive and editing operations, so it must not be sacrificed — which is why the shipped fix moves those actions into the popup itself rather than leaving them stranded under it.

---

## Main Success Scenario

1. User hovers a bookmark row in list layout (previews enabled).
2. The preview popup appears, with its own action footer (favicon · host link · "⋯" menu).
3. The bookmark's actions remain **reachable**: even where the popup covers the row's own "⋯", the same Edit / Move / Refresh Preview / Delete actions are present in the popup's footer.
4. User moves the pointer down onto the footer without the popup disappearing or switching to an adjacent row (no flicker / no intent-cancellation — the solid overlay captures the pointer).
5. User opens the footer menu and selects Edit (or Move / Delete / Refresh Preview).
6. The chosen action completes normally; the popup closes when the pointer leaves it.

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

**Trigger:** `@media(hover: none)` — previews are on but the hover popup never opens (see `BookmarkCard.vue`, the `hoverPreviewActive` gate).
**Flow:**

1. No popup is shown; the menu button is always visible on touch (no `opacity-0` gate).
2. Use case ends. The fix must not affect this path.

---

## Postconditions

### Success Postconditions

- The previewed bookmark's actions (Edit / Move / Refresh Preview / Delete) are always reachable while the popup is open — via the popup's footer menu when the row's own "⋯" is covered.
- The popup continues to anchor against the right edge of the content pane and never flips onto the fixed sidebar (left edge invariant preserved).
- Wide-viewport behaviour (popup in the gutter, no overlap) is unchanged.
- Touch/no-hover behaviour is unchanged.

### Failure Postconditions

- The bookmark's actions are not reachable while its preview is open without moving the pointer away (which dismisses the preview).

---

## Business Rules

### BR-093-1: Row Actions Are Always Reachable

While the preview popup is open for a hovered row, the bookmark's actions (Edit / Move / Refresh Preview / Delete) MUST remain reachable without dismissing the preview. The popup MAY cover the row's own "⋯" button, but only if it surfaces the same actions itself — in the shipped design, via the popup's footer menu. Stranding the actions under the popup (covered and with no in-popup substitute) is not acceptable.

### BR-093-2: Left-Edge Invariant Preserved

The popup MUST NOT flip onto the fixed sidebar. The current constraint `minLeft = max(VIEWPORT_PAD, cb.left + VIEWPORT_PAD)` is retained; any repositioning happens within the content pane.

### BR-093-3: No Flicker on Pointer Transit

Moving the pointer from the row body onto the (now visible) menu button MUST NOT cancel the row's hover intent and MUST NOT close the popup prematurely. Hover-intent enter/leave timing must tolerate the pointer crossing from the row onto a row-child control.

### BR-093-4: Resolution Approach Is Implementation-Open

The fix may be implemented by any of (non-exhaustive): repositioning the popup so it leaves a clear corridor over the menu button; shrinking/reshaping the popup's overlap region; adding a non-overlapping hover-safe zone; deferring popup show until the pointer dwells outside the menu button's corridor; or making the popup dismissible on menu focus. The acceptance test (see below) is the source of truth, not the chosen technique.

### BR-093-5: Click-to-Open on the Capture

The popup is a solid `pointer-events: auto` overlay, so it owns the click on its capture surface: clicking anywhere on the screenshot opens the hovered bookmark (`window.open` + `trackClick`). This is consistent across the whole capture — unlike click-through, which only opened where the popup happened to overlap the row. Because the overlay captures the pointer, the rows beneath it never fire `mouseenter` while it is up, so the preview cannot be hijacked by an adjacent row as the user moves the pointer down to the footer.

The footer host is additionally rendered as a real `<a target="_blank" rel="noopener noreferrer">` (`data-testid="bookmark-preview-popup-link"`) pointing at the bookmark URL, so it is keyboard-focusable and supports middle-click / right-click → copy link — the affordances a JS `window.open` can't offer — while still recording the open via `trackClick`. The clickable capture (mouse) and the footer link (keyboard / aux-click) are twin entry points to the same page.

### BR-093-6: No Block on the Sticky Toolbar

Because the popup is `pointer-events: auto`, it MUST NOT overlap the sticky `bookmark-list-toolbar` — otherwise it would block the toolbar's links (the original regression). The popup's vertical clamp uses the toolbar's bottom edge as its top bound, so the two never share screen space. (The batch action bar only appears during selection, which disables the popup, so it is not an additional bound.)

---

## Acceptance Test

A Playwright test in the **chromium** project (hover-capable) shall, with previews enabled and a narrow list layout where the popup overlaps the row:

1. Hover a bookmark row.
2. Assert the preview popup is visible (`[data-testid="bookmark-preview-popup"]`).
3. Move the pointer across the capture toward the footer (past the row's bottom edge) and assert the popup stays visible — the solid overlay's own `mouseenter` holds it open while the pointer is anywhere over it, and prevents an adjacent row from switching the preview mid-travel.
4. Assert a sticky-toolbar control (e.g. `collection-settings-open`) remains the hit-test target (`document.elementFromPoint`) even while the popup is up — the popup is clamped below the toolbar, so it never covers it.
5. Open the popup's footer menu (`[data-testid="bookmark-menu-button"]`) and assert the dropdown with the "Edit" item appears.

The test must also cover A1 (wide viewport): the popup sits in the gutter and no overlap assertion is needed beyond "popup visible, footer menu visible".

---

## Implementation Pointers

Chosen approach (BR-093-4): **make the popup own the actions**. Rather than
repositioning the popup away from the row, the popup that already covers the
row's `⋯` now hosts the same actions in a footer, so the covered control is no
longer the one the user has to reach.

Decomposed change set:

1. **`frontend/src/components/bookmark/BookmarkRowMenu.vue`** (new) — the
   inline lazy-mount radix dropdown that was duplicated in `BookmarkCard` and
   `GroupedBookmarkRow` is extracted into a shared component. Props:
   `bookmark`, `showRefreshPreview`, `triggerClass`, `iconClass`; emits
   `edit`/`move`/`delete`/`refreshPreview`/`open-change`. Preserves the
   `data-testid="bookmark-menu-button"` trigger.
2. **`BookmarkCard.vue` / `GroupedBookmarkRow.vue`** — migrated to
   `BookmarkRowMenu` (callers pass their own positioning + hover-reveal
   `triggerClass`, which Tailwind still scans from each caller's source).
3. **`frontend/src/composables/useScreenshotRefresh.ts`** (new) — the refresh
   POST + error handling is shared by the card row menu and the popup footer.
4. **`frontend/src/composables/useBookmarkPreviewHover.ts`** — extended with
   popup keepalive: `onPopupEnter`/`onPopupLeave` (cancel/schedule the hide as
   the pointer enters/leaves the solid overlay) and `pin`/`unpin` (hold the
   popup open while the footer dropdown is open, UC-093 A2). `scheduleHide` is a
   no-op while pinned; `dismiss` resets pinned. Because the overlay captures the
   pointer, rows beneath it never fire `mouseenter` while it is up, so the
   preview can't be hijacked by an adjacent row mid-travel to the footer.
5. **`frontend/src/components/bookmark/BookmarkPreviewPopup.vue`** — renders the
   action footer (favicon · host · `BookmarkRowMenu`) as a sibling below the
   capture frame; removes the old full-URL caption (host replaces it) and
   `aria-hidden` (the popup is now interactive). The whole popup is a solid
   `pointer-events: auto` overlay (BR-093-5/6): the capture owns its click via
   `onOpenBookmark` (`window.open` + `trackClick`), and the popup is clamped to
   never overlap the sticky toolbar so it can't block toolbar links. Root
   `@mouseenter`/`@mouseleave` drive the keepalive; `@open-change` pins/unpins.
   Refresh is handled locally (bumps a popup-scoped nonce). Edit/Move/Delete are
   emitted up to `BookmarkList` (each also schedules a hide so the popup clears
   as the dialog opens).
6. **`BookmarkList.vue`** — binds the popup's `edit`/`move`/`delete` to the
   existing handlers that already serve the row menu.
7. **`frontend/src/composables/useStickyToolbar.ts`** (new) +
   **`CollectionView.vue`** / **`BookmarkListToolbar.vue`** — the toolbar shares
   its root element with the popup via provide/inject (anchored in
   CollectionView, which renders both the toolbar slot and the list) instead of
   a `document.querySelector` on a test id, so the clamp can't silently regress.

Invariants preserved: left-edge (sidebar) invariant (BR-093-2), wide-viewport
gutter placement (A1), touch/no-hover path (A3), and click-to-open on the
capture area (now an explicit handler — BR-093-5 — rather than click-through).

- `frontend/src/components/bookmark/BookmarkPreviewPopup.vue` — `measure()`
  right-alignment + toolbar clamp, and the `POPUP_W` / overlap trade-off (the
  overlap is acceptable because the row's actions live in the popup's footer).
- `frontend/src/components/bookmark/BookmarkCard.vue` — the `hoverPreviewActive`
  gate that decides when the zoom popup is allowed.
- `frontend/src/composables/useBookmarkPreviewHover.ts` — enter/leave/pin
  timing relevant to BR-093-3.
