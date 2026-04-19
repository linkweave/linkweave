# Use Case: Reorganize Items via Drag and Drop

## Overview

**Use Case ID:** UC-039
**Use Case Name:** Reorganize Items via Drag and Drop
**Primary Actor:** User
**Goal:** Reorganize folders and bookmarks within a collection by dragging and dropping them to new positions or parents.
**Status:** Draft

## Traceability

**Maps to:** FR-045

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one folder or bookmark.

## Main Success Scenario

1. User starts dragging a folder or bookmark from the sidebar or bookmark list.
2. System visually indicates the dragged item (e.g., opacity change, drag ghost).
3. User hovers over a valid drop target (a folder or a position between items).
4. System highlights the drop target to indicate where the item will be placed.
5. User drops the item on the target.
6. System validates the move (no circular reference, same collection, not the root folder).
7. System moves the item to the new position or parent.
8. System updates the UI to reflect the new structure.

## Alternative Flows

### A1: Drag a Bookmark onto a Folder

**Trigger:** User drags a bookmark onto a folder (step 3).
**Flow:**

1. System highlights the folder as a valid drop target.
2. User drops the bookmark on the folder.
3. System moves the bookmark into the folder.
4. Use case continues at step 8.

### A2: Drag a Folder into Another Folder (Nesting)

**Trigger:** User drags a folder onto another folder (step 3).
**Flow:**

1. System validates that the target folder is not the source folder itself or a descendant.
2. System highlights the folder as a valid drop target.
3. User drops the folder.
4. System moves the folder to be a child of the target folder.
5. Use case continues at step 8.

### A3: Reorder Items within the Same Level

**Trigger:** User drags an item to a position between siblings (step 3).
**Flow:**

1. System shows an insertion indicator between the items.
2. User drops the item at the desired position.
3. System updates the sort order of the item and its siblings.
4. Use case continues at step 8.

### A4: Invalid Drop — Circular Reference

**Trigger:** User drags a folder onto one of its own descendants (step 5).
**Flow:**

1. System displays an error message: "Cannot move a folder into itself or its subfolders."
2. System does not perform the move.
3. The dragged item returns to its original position.
4. Use case ends.

### A5: Invalid Drop — Root Folder

**Trigger:** User attempts to drag the root folder (step 1).
**Flow:**

1. System does not initiate the drag operation for the root folder.
2. Use case ends.

### A6: Invalid Drop — Cross-Collection

**Trigger:** User attempts to drop an item onto a folder in a different collection (step 5).
**Flow:**

1. System displays an error message: "Items cannot be moved between collections."
2. System does not perform the move.
3. The dragged item returns to its original position.
4. Use case ends.

### A7: Cancel Drag

**Trigger:** User presses Escape or releases the item over an invalid area (step 3).
**Flow:**

1. System cancels the drag operation.
2. The dragged item returns to its original position.
3. Use case ends.

## Postconditions

### Success Postconditions

- The dragged item is moved to the new parent folder or position.
- The folder tree and/or bookmark list reflect the updated structure.
- Sort order is updated for all affected siblings.

### Failure Postconditions

- Item positions and hierarchy remain unchanged.
- System displays an appropriate error message if applicable.

## Business Rules

### BR-040: Root Folder Immovable

The root folder of a collection cannot be dragged or moved. It is always the top-level container.

### BR-041: Same-Collection Constraint

Items can only be moved within the same collection. Cross-collection drag and drop is not supported.

### BR-042: Circular Reference Prevention

A folder cannot be dropped into itself or any of its descendants.

### BR-043: Bookmark Single-Folder Membership

A bookmark can belong to at most one folder at a time. Dropping a bookmark into a new folder removes it from its previous folder.

### BR-044: Visual Feedback

The system must provide continuous visual feedback during drag operations: a drag ghost for the dragged item, highlight for valid drop targets, and an insertion indicator for reordering.
