# Use Case: Reorder Bookmarks Within a Folder

## Overview

**Use Case ID:** UC-103   
**Use Case Name:** Reorder Bookmarks Within a Folder   
**Primary Actor:** User   
**Goal:** Arrange the bookmarks of a folder into a custom, persistent order by dragging them to a new position in the bookmark list.   
**Status:** Draft   

## Traceability

**Maps to:** FR-099
**Related:** UC-102 (Reorder Folders in Sidebar — same ordering model), UC-076 (Sort Bookmarks — Manual joins its sort modes), UC-013 (Move Bookmark to Folder — drop onto a sidebar folder), FR-082 (Sort Preferences per collection)

---

## Preconditions

- The user is authenticated.
- The user has edit access to the collection (viewers see the manual order but cannot change it).
- The user's sort preference for the collection is set to "Manual" (UC-076).
- The folder contains at least two bookmarks.
- The user is on a device with pointer-based drag and drop (drag is disabled on touch devices).

## Main Success Scenario

1. User selects "Manual" as the sort mode for the collection (or it is already active).
2. User selects a folder in the sidebar; system lists the folder's bookmarks in their manual order.
3. User starts dragging a bookmark row.
4. System highlights valid drop positions as the user moves the bookmark:
   - Hovering over the **upper or lower edge** of another bookmark row shows an insertion line before or after it.
   - Sidebar folders remain drop targets for moving the bookmark (existing behavior, UC-013).
5. User drops the bookmark on an insertion line between two bookmarks of the same folder.
6. System places the bookmark at that position within the folder.
7. System persists the new order.
8. System displays the updated list; the new order is visible to every member viewing the collection in Manual mode and preserved across sessions and devices.

## Alternative Flows

### A1: Drop Onto a Sidebar Folder (Move)

**Trigger:** User drops the bookmark on a folder in the sidebar (step 5).
**Flow:**

1. System moves the bookmark into the target folder (existing behavior, UC-013).
2. The bookmark keeps its previous order position number; the system slots it among the target folder's bookmarks according to that number.
3. Use case continues at step 7.

### A2: Drop at a Specific Position in Another Folder's Group

**Trigger:** The list spans several folders (e.g. a parent folder with subfolders is selected) and the user drops the bookmark on an insertion line inside a different folder's group (step 5).
**Flow:**

1. System moves the bookmark into that folder.
2. System assigns the bookmark the order position where it was dropped (overriding its previous position number).
3. Use case continues at step 7.

### A2b: Reorder in the Grouped (Compact) Layout

**Trigger:** The user views the collection in the grouped layout (folder cards with compact rows) and starts dragging a compact bookmark row (step 3).
**Flow:**

1. System shows insertion lines between the compact rows of a section while the sort mode is Manual (BR-199).
2. Dropping between two rows of the same section reorders the bookmark within its folder (continue at step 6).
3. Dropping between rows of a different folder's section moves the bookmark into that folder at the drop position (as in A2).
4. Dropping on a folder card's header moves the bookmark into that root folder without assigning a new position (as in A1).

### A3: Non-Manual Sort Mode Active

**Trigger:** User drags a bookmark while the collection's sort mode is Title, Creation Date, Last Clicked, or Click Count (step 3).
**Flow:**

1. System shows no insertion lines between bookmarks; dragging onto a sidebar folder (move, UC-013) remains available.
2. While the drag is in flight, the toolbar shows a hint that reordering requires the Manual sort mode. The same mechanism explains a filtered view (BR-197): with a search or tag filter active, the hint says filters must be cleared to reorder.
3. Use case ends.

### A4: Undo Reorder

**Trigger:** User chooses "Undo" from the confirmation notification after a reorder (step 8).
**Flow:**

1. System restores the bookmark's previous position (and previous folder, if the drop also changed it).
2. System persists and displays the restored order.
3. Use case ends.

### A5: Persistence Fails

**Trigger:** System cannot save the new order, e.g. network error (step 7).
**Flow:**

1. System reverts the list to the previous order.
2. System displays an error message.
3. Use case ends.

### A6: Viewer Without Edit Rights

**Trigger:** A collection member with view-only access attempts to drag a bookmark (step 3).
**Flow:**

1. System does not initiate the drag; the list remains read-only. The viewer can still select Manual mode to see the shared order.
2. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark occupies its new position among the bookmarks of its folder.
- The order is stored on the bookmarks themselves and is identical for every member viewing the collection in Manual mode.
- The order survives reloads, new sessions, and other devices.
- Other sort modes (UC-076) are unaffected.

### Failure Postconditions

- The bookmark order remains unchanged.
- System displays an error message.

## Business Rules

### BR-192: Manual Is an Additional Sort Mode

"Manual" joins the existing sort modes (FR-082: title, creation date, last clicked, click count). Each user's sort preference remains a personal, per-collection setting; the manual position itself is stored on the bookmark regardless of which mode a user is viewing in. Before anyone reorders, the manual order equals creation order.

### BR-193: Order Is Scoped to Folder Groups and Shared

A manual position is meaningful among bookmarks of the same folder (bookmarks without a folder form their own group). Like the folder order (UC-102), it is shared collection data: all members see the same manual order; only members with edit rights may change it.

### BR-194: Reordering Requires Manual Mode

Insertion targets between bookmarks appear only while the viewing user's sort mode is Manual. Dragging a bookmark onto a sidebar folder (move, UC-013) works in every sort mode.

### BR-195: Moved Bookmarks Keep Their Position Number

When a bookmark is moved to another folder by dropping it onto the folder (A1) or by any non-positional means (edit dialog, batch move, CLI), it keeps its previous position number and is ranked among its new folder's bookmarks by that number. Only an explicit drop between two bookmarks assigns a new position.

### BR-196: New Bookmarks Append at the End

A newly created or imported bookmark receives the last position within its folder group.

### BR-197: Views Spanning Multiple Folders Group by Folder

When a Manual-mode list spans several folders (a folder selected together with its subfolders), bookmarks are grouped by folder — folders appearing in their own manual order (UC-102), parent before descendants — and manually ordered within each group. Views that are not folder-scoped (e.g. search results) fall back to a deterministic order and offer no reorder targets.

### BR-198: Deterministic Tie-Breaking

If two bookmarks in the same folder ever hold the same position number (e.g. after a move per BR-195), the system orders them deterministically (older bookmark first).

### BR-199: Layout Support

Reorder insertion targets are offered in the **list** layout and in the **grouped (compact)** layout (between the compact rows of a section). The **grid** layout displays the manual order but offers no insertion targets — dragging a grid card onto a sidebar folder (move, UC-013) still works, and the system may hint that reordering is available in the list layout.

### BR-200: Grouped Layout Follows the Manual Folder Order

The grouped layout orders its folder cards and subfolder sections by the manual folder order (UC-102) — always, regardless of the user's bookmark sort mode — so the sidebar and the grouped view never disagree about folder order. (This replaces the previous alphabetical ordering of cards and sections.)

---

## Notes (non-normative)

- Storage: a `sortOrder` value on the `Bookmark` entity, mirroring the `Folder` one from UC-102 (migration backfills from creation order; sparse numbering with midpoint insertion). The ordering model is deliberately identical so backend and frontend logic can be shared.
- The per-user sort preference already lives in `CollectionAccess` settings (`SortField` enum, `CollectionSettingsService`); Manual becomes a new `SortField` constant surfaced in `BookmarkSortMenu.vue`.
- Bookmark drag-to-folder already exists (`useDndMove.moveBookmarkWithUndo`); this use case adds edge-zone insertion targets in the bookmark list, active only in Manual mode.
- The reorder persists via the bookmark move/update endpoint extended with a target position (same pattern as UC-102's folder move).
- Layouts: the list layout and the grouped (compact) layout (`BookmarkGroupedLayout.vue` / `GroupedBookmarkRow.vue`) offer insertion targets (BR-199); the grid layout is display-only for ordering. The grouped layout's card/section ordering switches from alphabetical to the UC-102 folder order (BR-200).
- The HTML export (UC-030) writes folders and bookmarks in their manual order, so an export of a hand-arranged collection round-trips that arrangement.
