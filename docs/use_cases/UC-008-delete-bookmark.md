# Use Case: Delete Bookmark

## Overview

**Use Case ID:** UC-008   
**Use Case Name:** Delete Bookmark   
**Primary Actor:** User   
**Goal:** Remove a bookmark that is no longer needed from the collection.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-008

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one bookmark.
- The bookmark to delete exists.

## Main Success Scenario

1. User selects a bookmark from the bookmark list.
2. User chooses "Delete" from the bookmark's context menu.
3. System prompts for confirmation.
4. User confirms the deletion.
5. System removes all tag associations from the bookmark.
6. System deletes the bookmark.
7. System updates the bookmark list to remove the deleted bookmark.

## Alternative Flows

### A1: Cancel Deletion

**Trigger:** User cancels the deletion when prompted (step 4).
**Flow:**

1. System dismisses the confirmation dialog.
2. Bookmark remains unchanged.
3. Use case ends.

### A2: Bulk Delete

**Trigger:** User selects multiple bookmarks for deletion (step 1).
**Flow:**

1. User selects multiple bookmarks using checkboxes or multi-select.
2. User chooses "Delete Selected" from the action menu.
3. System prompts for confirmation showing the count of bookmarks to delete.
4. User confirms the deletion.
5. System removes all tag associations from all selected bookmarks.
6. System deletes all selected bookmarks.
7. System updates the bookmark list.
8. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark no longer exists in the collection.
- All tag associations for the bookmark are removed.
- The bookmark is removed from any folder it was in.

### Failure Postconditions

- Bookmark remains unchanged.
- System displays an error message.

## Business Rules

### BR-049: Cascade Tag Removal

Deleting a bookmark removes all associations with tags; the tags themselves remain in the collection.

### BR-050: Undo Not Supported

Bookmark deletion is permanent; there is no undo functionality.
