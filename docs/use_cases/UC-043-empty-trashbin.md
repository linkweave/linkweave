# Use Case: Empty Trashbin

## Overview

**Use Case ID:** UC-043
**Use Case Name:** Empty Trashbin
**Primary Actor:** User
**Goal:** Permanently delete all items in the trashbin at once so that the user can quickly free up space and remove all soft-deleted items they no longer need.
**Status:** Draft

## Traceability

**Maps to:** FR-049

---

## Preconditions

- The user is authenticated.
- The trashbin contains at least one soft-deleted bookmark or folder.
- The user is viewing the trashbin (UC-040).

## Main Success Scenario

1. User chooses "Empty Trashbin" from the trashbin view header or action menu.
2. System prompts for confirmation, warning that all items in the trashbin will be permanently deleted and the action is irreversible.
3. User confirms the action.
4. System permanently deletes all bookmarks and folders in the trashbin.
5. System removes all tag associations for permanently deleted bookmarks.
6. System displays an empty trashbin view with a message confirming all items have been removed.

## Alternative Flows

### A1: Cancel Empty Trashbin

**Trigger:** User cancels the confirmation dialog (step 3).
**Flow:**

1. System dismisses the confirmation dialog.
2. All items remain in the trashbin.
3. Use case ends.

### A2: Empty Trashbin Not Available

**Trigger:** The trashbin is already empty (step 1).
**Flow:**

1. System disables or hides the "Empty Trashbin" action.
2. Use case ends.

## Postconditions

### Success Postconditions

- All soft-deleted items are permanently removed from the database.
- All tag associations for deleted bookmarks are removed.
- The trashbin is empty.
- No items can be recovered.

### Failure Postconditions

- Items remain in the trashbin.
- System displays an error message.

## Business Rules

### BR-067: Irreversible Action

Emptying the trashbin is irreversible. All items are permanently deleted and cannot be recovered.

### BR-068: Cascading Deletion

Folders in the trashbin are permanently deleted together with all their contained bookmarks and subfolders.

### BR-069: Tag Cleanup

All tag associations for bookmarks being permanently deleted are removed. The tags themselves remain in the collection.
