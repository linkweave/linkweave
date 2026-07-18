# Use Case: Reorder Folders in Sidebar

## Overview

**Use Case ID:** UC-102   
**Use Case Name:** Reorder Folders in Sidebar   
**Primary Actor:** User   
**Goal:** Arrange folders in the sidebar into a custom, persistent order by dragging them to a new position.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-098
**Related:** UC-012 (Nest Folders — drop *onto* a folder), UC-076 (Sort Bookmarks), FR-045 (Drag and Drop Reorganization)

---

## Preconditions

- The user is authenticated.
- The user has edit access to the collection (viewers see the order but cannot change it).
- The collection contains at least two folders at the same level (siblings).
- The user is on a device with pointer-based drag and drop (drag is disabled on touch devices).

## Main Success Scenario

1. User starts dragging a folder in the sidebar folder tree.
2. System highlights valid drop positions as the user moves the folder:
   - Hovering over the **upper or lower edge** of another folder row shows an insertion line before or after that folder.
   - Hovering over the **middle** of another folder row highlights the row as a nesting target (existing behavior, UC-012).
3. User drops the folder on an insertion line between two sibling folders.
4. System places the folder at that position among the siblings.
5. System persists the new order.
6. System displays the updated folder tree; the new order is visible to all members of the collection and preserved across sessions and devices.

## Alternative Flows

### A1: Drop Onto a Folder (Nesting)

**Trigger:** User drops the folder on the middle of another folder row (step 3).
**Flow:**

1. System moves the folder into the target folder as its child (existing behavior, UC-012).
2. The folder keeps its previous order position number; the system slots it among its new siblings according to that number.
3. Use case continues at step 5.

### A2: Drop at a Specific Position Inside a New Parent

**Trigger:** User drags the folder over an expanded folder's children and drops it on an insertion line between them (step 3).
**Flow:**

1. System moves the folder into the new parent folder.
2. System assigns the folder the order position where it was dropped (overriding its previous position number).
3. Use case continues at step 5.

### A3: Undo Reorder

**Trigger:** User chooses "Undo" from the confirmation notification after a reorder (step 6).
**Flow:**

1. System restores the folder's previous position (and previous parent, if the drop also changed it).
2. System persists and displays the restored order.
3. Use case ends.

### A4: Persistence Fails

**Trigger:** System cannot save the new order, e.g. network error (step 5).
**Flow:**

1. System reverts the sidebar to the previous order.
2. System displays an error message.
3. Use case ends.

### A5: Invalid Drop Target

**Trigger:** User drops the folder onto itself, one of its descendants, or outside any drop zone (step 3).
**Flow:**

1. System cancels the drag; no positions change.
2. Use case ends.

### A6: Viewer Without Edit Rights

**Trigger:** A collection member with view-only access attempts to drag a folder (step 1).
**Flow:**

1. System does not initiate the drag; the folder tree remains read-only.
2. Use case ends.

## Postconditions

### Success Postconditions

- The folder occupies its new position among its siblings.
- The order is stored on the folders themselves and is identical for every member of the collection.
- The order survives reloads, new sessions, and other devices.

### Failure Postconditions

- The folder order remains unchanged.
- System displays an error message.

## Business Rules

### BR-186: Manual Order Is the Only Folder Order

Folders have exactly one order: the manual order. There is no sort-mode selector for folders. Before a user ever reorders, folders appear in their creation order, which becomes the initial manual order.

### BR-187: Order Is Scoped to Sibling Groups

An order position is meaningful among siblings — folders sharing the same parent (or sharing "no parent" at root level) within a collection. Reordering never changes a folder's parent; only a drop onto a folder row (A1) or between another folder's children (A2) does.

### BR-188: Order Is Shared Collection Data

The folder order is part of the collection, not a per-user preference. All members see the same order. Only members with edit rights may change it.

### BR-189: Reparented Folders Keep Their Position Number

When a folder is dropped *onto* another folder (nesting, A1), it keeps its previous order position number and is ranked among its new siblings by that number. Only an explicit drop *between* two folders assigns a new position.

### BR-190: New Folders Append at the End

A newly created folder receives the last position among its siblings.

### BR-191: Deterministic Tie-Breaking

If two siblings ever hold the same position number (e.g. after a reparent per BR-189), the system orders them deterministically (older folder first) so the tree never flickers or renders differently between clients.

---

## Notes (non-normative)

- Storage: a `sortOrder` value on the `Folder` entity (migration backfills it from creation order). Sparse numbering (e.g. steps of 1000, midpoint insertion) avoids rewriting all siblings on every drop.
- The sidebar already implements folder drag-and-drop for nesting (`FolderTreeNode.vue`, `useDndMove`) with undo and cycle prevention; this use case extends the same gesture with edge-zone insertion targets and extends `PATCH /folders/{id}/move` with an optional `position` (anchor sibling id + BEFORE/AFTER placement).
- [UC-103](UC-103-reorder-bookmarks-within-folder.md) covers manually reordering **bookmarks within a folder**, reusing the same ordering model (sparse shared sort key, append-at-end, keep-number-on-move).
