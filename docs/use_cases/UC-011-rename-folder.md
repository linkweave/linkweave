# Use Case: Rename Folder

## Overview

**Use Case ID:** UC-011   
**Use Case Name:** Rename Folder   
**Primary Actor:** User   
**Goal:** Change the name of an existing folder to keep the organizational structure accurate.   
**Status:** Draft   

## Traceability

**Maps to:** FR-011

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one folder.
- The folder to rename exists.

## Main Success Scenario

1. User selects a folder from the folder list.
2. User chooses "Rename" from the folder's context menu.
3. System displays the rename form with the current folder name.
4. User enters a new folder name.
5. User submits the form.
6. System validates the new name is not empty.
7. System updates the folder name.
8. System displays the updated folder name in the folder list.

## Alternative Flows

### A1: Empty Folder Name

**Trigger:** User submits the form without entering a name (step 5).
**Flow:**

1. System displays an error message: "Folder name is required."
2. User enters a folder name.
3. Use case continues at step 5.

### A2: Cancel Rename

**Trigger:** User cancels the rename operation (step 4).
**Flow:**

1. System discards the changes.
2. Folder name remains unchanged.
3. Use case ends.

### A3: Same Name Entered

**Trigger:** User enters the same name as the current folder name (step 4).
**Flow:**

1. System accepts the input (no change is a valid operation).
2. Use case continues at step 7.

## Postconditions

### Success Postconditions

- The folder has a new name.
- All bookmarks in the folder remain associated with the folder.
- The folder's position in the hierarchy is unchanged.

### Failure Postconditions

- Folder name remains unchanged.
- System displays an error message.

## Business Rules

### BR-016: Rename Scope

Renaming a folder does not affect its contents or its position in the folder hierarchy.
