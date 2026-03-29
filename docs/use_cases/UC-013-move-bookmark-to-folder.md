# Use Case: Move Bookmark to Folder

## Overview

**Use Case ID:** UC-013   
**Use Case Name:** Move Bookmark to Folder   
**Primary Actor:** User   
**Goal:** Organize a bookmark by placing it into a specific folder.   
**Status:** Draft   

## Traceability

**Maps to:** FR-013

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one bookmark.
- The target folder exists (or user chooses to remove from folder).

## Main Success Scenario

1. User selects a bookmark from the bookmark list.
2. User chooses "Move to Folder" from the bookmark's context menu.
3. System displays a list of folders in the current collection.
4. User selects a target folder.
5. System moves the bookmark to the selected folder.
6. System updates the bookmark list to reflect the change.

## Alternative Flows

### A1: Remove from Folder

**Trigger:** User wants to move the bookmark out of any folder (step 3).
**Flow:**

1. User selects "No Folder" or "Root" option.
2. System removes the bookmark's folder association.
3. Bookmark appears in the uncategorized bookmark list.
4. Use case continues at step 6.

### A2: Bookmark Already in Target Folder

**Trigger:** User selects the folder the bookmark is already in (step 4).
**Flow:**

1. System accepts the selection (no change is a valid operation).
2. Use case continues at step 6.

### A3: Drag and Drop

**Trigger:** User drags a bookmark onto a folder (alternative to step 1-4).
**Flow:**

1. User drags the bookmark onto a target folder.
2. System highlights the folder to indicate the drop target.
3. User drops the bookmark.
4. System moves the bookmark to the folder.
5. Use case continues at step 6.

### A4: Cancel Move

**Trigger:** User cancels the move operation (step 3).
**Flow:**

1. System dismisses the folder selection dialog.
2. Bookmark's folder association remains unchanged.
3. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark is associated with the selected folder.
- The bookmark appears when viewing the folder's contents.

### Failure Postconditions

- Bookmark's folder association remains unchanged.
- System displays an error message.

## Business Rules

### BR-020: Single Folder Membership

A bookmark can belong to at most one folder at a time.

### BR-021: Cross-Collection Move

Bookmarks cannot be moved to folders in different collections.
