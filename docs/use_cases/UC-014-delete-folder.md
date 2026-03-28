# Use Case: Delete Folder

## Overview

**Use Case ID:** UC-014   
**Use Case Name:** Delete Folder   
**Primary Actor:** User   
**Goal:** Remove a folder that is no longer needed from the collection.   
**Status:** Draft   

## Traceability

**Maps to:** FR-014

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one folder.
- The folder to delete exists.

## Main Success Scenario

1. User selects a folder from the folder list.
2. User chooses "Delete" from the folder's context menu.
3. System prompts for confirmation, warning that all bookmarks in the folder will be permanently deleted.
4. User confirms the deletion.
5. System deletes all bookmarks in the folder.
6. System deletes the folder.
7. System updates the folder list to remove the deleted folder.

## Alternative Flows

### A1: Folder Contains Subfolders

**Trigger:** The folder to delete contains subfolders (step 3).
**Flow:**

1. System prompts for confirmation, warning that all subfolders and their bookmarks will be permanently deleted.
2. User confirms the deletion.
3. System recursively deletes all bookmarks in all subfolders.
4. System deletes all subfolders.
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

- The folder no longer exists in the collection.
- All bookmarks that were in the folder are permanently deleted.
- All subfolders that were nested under the folder are also deleted with their bookmarks.

### Failure Postconditions

- Folder and its contents remain unchanged.
- System displays an error message.

## Business Rules

### BR-022: Bookmark Deletion

Deleting a folder permanently deletes all bookmarks it contains.

### BR-023: Recursive Deletion

Deleting a folder also deletes all its subfolders and all bookmarks within them.

### BR-024: Undo Not Supported

Folder and bookmark deletion is permanent; there is no undo functionality.
