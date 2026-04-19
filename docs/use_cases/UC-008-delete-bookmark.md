# Use Case: Delete Bookmark

## Overview

**Use Case ID:** UC-008   
**Use Case Name:** Delete Bookmark   
**Primary Actor:** User   
**Goal:** Move a bookmark that is no longer needed to the trashbin so it can be restored later or permanently deleted.
**Status:** Draft

## Traceability

**Maps to:** FR-008, FR-046

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
6. System moves the bookmark to the trashbin (soft delete), recording the original folder location.
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
6. System moves all selected bookmarks to the trashbin (soft delete), recording their original folder locations.
7. System updates the bookmark list.
8. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark is moved to the trashbin and no longer visible in the collection.
- All tag associations for the bookmark are removed.
- The bookmark is removed from any folder it was in.
- The bookmark can be restored from the trashbin (UC-041) or permanently deleted (UC-042).

### Failure Postconditions

- Bookmark remains unchanged.
- System displays an error message.

## Business Rules

### BR-049: Cascade Tag Removal

Deleting a bookmark removes all associations with tags; the tags themselves remain in the collection.

### BR-050: Soft Delete

Deleting a bookmark moves it to the trashbin. The bookmark can be restored from the trashbin (UC-041) or permanently deleted from there (UC-042).
