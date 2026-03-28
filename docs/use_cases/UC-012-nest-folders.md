# Use Case: Nest Folders

## Overview

**Use Case ID:** UC-012   
**Use Case Name:** Nest Folders   
**Primary Actor:** User   
**Goal:** Move a folder inside another folder to create a hierarchical structure.   
**Status:** Draft   

## Traceability

**Maps to:** FR-012

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least two folders.
- The source folder and target parent folder exist.

## Main Success Scenario

1. User selects a folder from the folder list.
2. User chooses "Move to Folder" from the folder's context menu.
3. System displays a list of available target folders.
4. User selects a target parent folder.
5. System validates that the target is not the source folder itself or a descendant.
6. System moves the source folder into the target folder.
7. System updates the folder tree to reflect the new hierarchy.

## Alternative Flows

### A1: Move to Root

**Trigger:** User wants to move the folder to the root level (step 3).
**Flow:**

1. User selects "Root Level" or "No Parent" option.
2. System moves the folder to the root level of the collection.
3. Use case continues at step 7.

### A2: Circular Reference Prevention

**Trigger:** User attempts to move a folder into itself or one of its descendants (step 4).
**Flow:**

1. System displays an error message: "Cannot move a folder into itself or its subfolders."
2. System does not perform the move.
3. Use case continues at step 3.

### A3: Cancel Move

**Trigger:** User cancels the move operation (step 3).
**Flow:**

1. System dismisses the folder selection dialog.
2. Folder hierarchy remains unchanged.
3. Use case ends.

## Postconditions

### Success Postconditions

- The source folder is now a child of the target folder.
- All bookmarks in the source folder remain associated with it.
- The folder tree reflects the new hierarchy.

### Failure Postconditions

- Folder hierarchy remains unchanged.
- System displays an error message.

## Business Rules

### BR-017: Circular Reference

A folder cannot be moved into itself or any of its descendants.

### BR-018: Nesting Depth

There is no enforced limit on folder nesting depth.

### BR-019: Cross-Collection Move

Folders cannot be moved between different collections.
