# Use Case: Permanently Delete from Trashbin

## Overview

**Use Case ID:** UC-042
**Use Case Name:** Permanently Delete from Trashbin
**Primary Actor:** User
**Goal:** Permanently remove a bookmark or folder from the trashbin so that it can no longer be restored.
**Status:** Implemented

## Traceability

**Maps to:** FR-049

---

## Preconditions

- The user is authenticated.
- The trashbin contains at least one soft-deleted bookmark or folder.
- The user is viewing the trashbin (UC-040).

## Main Success Scenario

1. User selects a deleted bookmark or folder from the trashbin.
2. User chooses "Delete Permanently" from the item's context menu or action button.
3. System prompts for confirmation, warning that the action is irreversible.
4. User confirms the permanent deletion.
5. System permanently deletes the item from the database.
6. System removes the item from the trashbin view.
7. System displays a confirmation that the item has been permanently deleted.

## Alternative Flows

### A1: Permanently Delete Folder with Contents

**Trigger:** The user permanently deletes a folder (step 1).
**Flow:**

1. System prompts for confirmation, warning that the folder and all its contained bookmarks and subfolders will be permanently deleted.
2. User confirms the permanent deletion.
3. System permanently deletes all bookmarks and subfolders within the folder.
4. System permanently deletes the folder.
5. System removes the folder and all its contents from the trashbin view.
6. Use case ends.

### A2: Cancel Permanent Deletion

**Trigger:** User cancels the confirmation dialog (step 4).
**Flow:**

1. System dismisses the confirmation dialog.
2. Item remains in the trashbin.
3. Use case ends.

### A3: Bulk Permanent Delete

**Trigger:** User selects multiple items to permanently delete (step 1).
**Flow:**

1. User selects multiple items using checkboxes or multi-select.
2. User chooses "Delete Permanently" from the action menu.
3. System prompts for confirmation showing the count of items to be permanently deleted.
4. User confirms the permanent deletion.
5. System permanently deletes all selected items.
6. System removes all deleted items from the trashbin view.
7. Use case ends.

## Postconditions

### Success Postconditions

- The item is permanently removed from the database and cannot be recovered.
- All tag associations for permanently deleted bookmarks are removed.
- The item no longer appears in the trashbin.

### Failure Postconditions

- The item remains in the trashbin.
- System displays an error message.

## Business Rules

### BR-064: Irreversible Action

Permanent deletion from the trashbin is irreversible. Items cannot be restored after this action.

### BR-065: Cascading Permanent Deletion for Folders

Permanently deleting a folder from the trashbin also permanently deletes all bookmarks and subfolders contained within it.

### BR-066: Tag Cleanup

Permanently deleting a bookmark removes all its tag associations. The tags themselves remain in the collection.
