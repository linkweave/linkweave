# Use Case: Copy Bookmark URL to Clipboard

## Overview

**Use Case ID:** UC-106
**Use Case Name:** Copy Bookmark URL to Clipboard
**Primary Actor:** User
**Goal:** Copy a single bookmark's URL to the system clipboard directly from the bookmark's row-action menu, without opening the bookmark or its edit dialog, so it can be pasted into a chat, document, or another app.
**Status:** Implemented

## Traceability

**Related to:** UC-074 (Batch Move and Delete — its batch bar already offers "Copy URLs" for a *selection*; this use case is the single-bookmark counterpart available on every row).
**Maps to:** NFR-015 (Usability)

---

## Preconditions

- The user is authenticated and has access to a collection containing at least one bookmark.
- The user can reach a bookmark row's "⋯" menu — on a card (grid/list layout), a compact grouped row, or the screenshot preview popup's footer (UC-093).

---

## Main Success Scenario

1. User opens the "⋯" row-action menu of a bookmark.
2. User selects **Copy URL**.
3. The system writes the bookmark's URL (exactly as stored, no normalization) to the clipboard via `navigator.clipboard.writeText`.
4. The system shows a success toast ("URL copied to clipboard.") and closes the menu. Nothing else changes — no navigation, no dialog, the list state is untouched.

## Alternative Flows

### A1: Clipboard write fails

**Trigger:** The browser denies clipboard access (permissions policy, insecure context, or document not focused).

**Flow:**

1. The `writeText` promise rejects.
2. The system shows an error toast ("Failed to copy URL.").
3. No data is written; the menu closes. The user can still copy manually from the edit dialog or the visible URL line.

### A2: Copy from the preview popup footer

**Trigger:** The user opens the popup footer's "⋯" menu (UC-093) instead of the row's own.

**Flow:** identical — the item lives in the shared `BookmarkRowMenu`, so all three surfaces (card, grouped row, popup footer) offer the same action.

---

## Postconditions

### Success Postconditions

- The clipboard contains the bookmark's URL.
- A success toast confirmed the copy to the user.

### Failure Postconditions

- The clipboard is unchanged and an error toast explained the failure.

---

## Business Rules

### BR-106-1: One Action on the Shared Menu

The Copy URL item is added to the shared `BookmarkRowMenu` component — not wired per call site — so the card, grouped-row, and popup-footer menus MUST all expose it with identical behaviour. It sits between "Move to Folder" and "Refresh preview".

### BR-106-2: Exact Stored URL

The copied text MUST be the bookmark's stored URL verbatim; no tracking parameters added, no normalization applied.

### BR-106-3: Non-Mutating and Non-Navigating

Copy URL MUST NOT modify the bookmark, the selection, or the collection; it MUST NOT navigate away or open the URL (that is UC-006's "open in new tab", reachable by clicking the row itself). No click tracking fires for a copy.

### BR-106-4: Feedback Is Mandatory

Every copy attempt MUST end in exactly one toast — success or error. There is no silent failure path.

---

## Acceptance Test

A Playwright test in the **chromium** project (`frontend/e2e/copy-url-menu.spec.ts`), with clipboard permissions granted:

1. Seed a bookmark with a known URL via the API.
2. Open the collection, open the row's "⋯" menu (`bookmark-menu-button`).
3. Select the Copy URL item (`bookmark-menu-copy-url`).
4. Assert the success toast appears and `navigator.clipboard.readText()` equals the seeded URL.

---

## Implementation Pointers

- `frontend/src/components/bookmark/BookmarkRowMenu.vue` — the action is self-contained in the shared menu component (no event plumbing to callers): a `copyUrl()` handler calls `navigator.clipboard.writeText(props.bookmark.data.url)` and toasts via the notification store, mirroring the batch bar's copy-URLs error handling (`BatchActionBar.vue`).
- `frontend/src/i18n/locales/{en,de,fr}.json` — keys `bookmark.copyUrl`, `bookmark.copiedToast`, `bookmark.copyError`.
- Single-bookmark counterpart of the batch "Copy URLs" (UC-074) — intentionally reuses the same toast/copy pattern rather than extracting a shared util for two call sites.
