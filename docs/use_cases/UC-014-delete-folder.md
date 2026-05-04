# Use Case: Delete Folder

## Overview

**Use Case ID:** UC-014   
**Use Case Name:** Delete Folder   
**Primary Actor:** User   
**Goal:** Move a folder that is no longer needed to the trashbin so it can be restored later or permanently deleted.
**Status:** Implemented

## Traceability

**Maps to:** FR-014, FR-046

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one folder.
- The folder to delete exists.

## Main Success Scenario

1. User selects a folder from the folder list.
2. User chooses "Delete" from the folder's context menu.
3. System prompts for confirmation, noting that all bookmarks in the folder will be moved to the trashbin.
4. User confirms the deletion.
5. System moves all bookmarks in the folder to the trashbin, recording their original locations.
6. System moves the folder to the trashbin (soft delete), recording its original parent location.
7. System updates the folder list to remove the deleted folder.

## Alternative Flows

### A1: Folder Contains Subfolders

**Trigger:** The folder to delete contains subfolders (step 3).
**Flow:**

1. System prompts for confirmation, noting that all subfolders and their bookmarks will be moved to the trashbin.
2. User confirms the deletion.
3. System recursively moves all bookmarks in all subfolders to the trashbin, recording their original locations.
4. System moves all subfolders to the trashbin, recording their original parent locations.
5. Use case continues at step 5.

### A2: Cancel Deletion

**Trigger:** User cancels the deletion when prompted (step 4).
**Flow:**

1. System dismisses the confirmation dialog.
2. Folder and its contents remain unchanged.
3. Use case ends.

### A3: Empty Folder

**Trigger:** The folder to delete contains no bookmarks or subfolders (step 3).
**Flow:**

1. System prompts for confirmation without warnings about bookmarks.
2. User confirms the deletion.
3. System deletes the folder.
4. Use case continues at step 7.

## Postconditions

### Success Postconditions

- The folder is moved to the trashbin and no longer visible in the collection.
- All bookmarks that were in the folder are moved to the trashbin.
- All subfolders that were nested under the folder are also moved to the trashbin with their bookmarks.
- Items can be restored from the trashbin (UC-041) or permanently deleted (UC-042).

### Failure Postconditions

- Folder and its contents remain unchanged.
- System displays an error message.

## Business Rules

### BR-022: Soft Delete Bookmarks

Deleting a folder moves all bookmarks it contains to the trashbin. They can be restored (UC-041) or permanently deleted (UC-042).

### BR-023: Recursive Soft Delete

Deleting a folder also moves all its subfolders and all bookmarks within them to the trashbin.

### BR-024: Soft Delete

Folder and bookmark deletion moves items to the trashbin. Items can be permanently deleted from the trashbin (UC-042, UC-043).
