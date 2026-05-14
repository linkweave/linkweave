# Use Case: Sort Bookmarks

## Overview

**Use Case ID:** UC-076   
**Use Case Name:** Sort Bookmarks   
**Primary Actor:** User   
**Goal:** Choose how bookmarks are sorted in the bookmark list and have the preference remembered per collection.
**Status:** Done

## Traceability

**Maps to:** FR-082

> **Note on shared collections:** "Last clicked" and "Click count" reflect aggregate click activity across all members of a shared collection — they are *not* per-user. Sorting by these criteria will therefore reflect what everyone in the collection clicks, not only the current user.

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

## Main Success Scenario

1. User clicks the sort dropdown in the bookmark list toolbar. The trigger button shows the current field and direction (e.g. *Sort: Date added ↓*).
2. System opens the dropdown listing four sort fields: Title, Date added, Last clicked, Click count.
3. User picks a field. The dropdown stays open and an inline ASC/DESC direction toggle appears on the active row.
4. User optionally flips the direction via the inline toggle. The dropdown remains open across direction changes.
5. System re-sorts the bookmark list immediately (optimistic UI).
6. System persists the per-(user, collection) preference to the backend after a short debounce, reusing the same `PUT /collections/{id}/settings` endpoint that already serves the `layout` setting.
7. Next time the user opens this collection — same browser or another device — the bookmark list uses the saved sort order.

## Alternative Flows

### A1: Default Sort

**Trigger:** User has never set a sort preference for the collection (step 7).
**Flow:**

1. System uses the system default — `DATE_ADDED` descending ("Newest first") — without writing anything to the backend. A collection without a stored preference looks identical to one whose preference matches the default.

### A2: Sort with Active Filters

**Trigger:** User has active tag, folder, or search filters when sort is applied (step 5).
**Flow:**

1. System sorts the *filtered* result set, not the entire collection. The sort pass runs after the existing folder/tag/search filter chain.

### A3: Click-Based Sort with Never-Opened Bookmarks

**Trigger:** User picks "Last clicked" or "Click count" (step 3) and at least one bookmark in the (filtered) list has no click activity.
**Flow:**

1. Bookmarks with no click activity are grouped at the end of the list, separated from the rest by a "Never opened · N" divider (where N is the count).
2. The "primary" group is sorted by the chosen field; the "never opened" group is ordered by creation date descending, independent of the chosen direction (BR-110).
3. The sort menu shows a small note inline: *"Last clicked" and "Click count" reflect everyone in this collection.*

### A4: Reset Per-Collection Preference

**Trigger:** A per-collection preference exists, and the user clicks "Reset" inside the sort dropdown header (step 3).
**Flow:**

1. System issues `DELETE /collections/{id}/settings/sort`, which clears the sort fields from the user's `CollectionAccess.settings` while preserving any other settings (e.g. `layout`).
2. The effective sort falls back to the system default (BR-108).
3. The "Reset" affordance disappears, since there is no longer a per-collection override to reset.

## Postconditions

### Success Postconditions

- The bookmark list is sorted by the selected criterion.
- The preference is persisted.

### Failure Postconditions

- The bookmark list remains sorted by the previous criterion.

## Business Rules

### BR-106: Sort Options

Sort is defined by a (field, direction) pair. Available fields: `TITLE`, `DATE_ADDED`, `LAST_CLICKED`, `CLICK_COUNT`. Available directions: `ASC`, `DESC`. Both are user-selectable independently — the direction toggle applies to whichever field is currently active.

### BR-107: Per-Collection Persistence

Sort preference is stored per `(user, collection)` inside `CollectionAccess.settings`. Changing sort in one collection does not affect other collections, and one user's preference does not affect other members of a shared collection.

### BR-108: Default Sort

When no per-collection preference is set, the system default is `DATE_ADDED` descending (newest first). No row is written to persist the default — it is purely a fallback applied client-side.

### BR-109: Stable Tie-Break

When two bookmarks share the same value for the sort criterion, they are tie-broken by creation date descending — *regardless of the requested direction*. This keeps the order deterministic across reloads and avoids "swapping siblings" when the primary key is coarse (e.g. equal click counts).

### BR-110: Never-Opened Group for Click-Based Sorts

When sorting by `LAST_CLICKED`, any bookmark with no `lastClickedAt` is placed in a "never opened" group at the end of the list. When sorting by `CLICK_COUNT`, any bookmark with `clickCount == 0` is treated the same way. The never-opened group is internally ordered by creation date descending, independent of the requested direction, so it always reads as "newest first that you've never opened".

### BR-111: Shared Click Data Disclosure

When the active sort is `LAST_CLICKED` or `CLICK_COUNT`, the sort menu must surface a short note explaining that those values reflect activity across all members of the collection, not just the current user. The note disappears for non-click-based sorts.
