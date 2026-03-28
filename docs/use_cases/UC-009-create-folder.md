# Use Case: Create Folder

## Overview

**Use Case ID:** UC-009   
**Use Case Name:** Create Folder   
**Primary Actor:** User   
**Goal:** Create a new folder to organize bookmarks within a collection.   
**Status:** Draft   

## Traceability

**Maps to:** FR-009

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User selects "Create Folder" from the folder menu.
2. System displays the folder creation form.
3. User enters a folder name.
4. User submits the form.
5. System validates the folder name is not empty.
6. System creates the folder in the current collection at root level.
7. System displays the new folder in the folder list.

## Alternative Flows

### A1: Empty Folder Name

**Trigger:** User submits the form without entering a name (step 4).
**Flow:**

1. System displays an error message: "Folder name is required."
2. User enters a folder name.
3. Use case continues at step 4.

### A2: Create in Subfolder

**Trigger:** User wants to create the folder inside an existing folder (step 1).
**Flow:**

1. User selects an existing folder and chooses "Create Subfolder" from its context menu.
2. System displays the folder creation form with parent folder pre-selected.
3. Use case continues at step 3.
4. System creates the folder as a child of the selected parent folder.

## Postconditions

### Success Postconditions

- A new folder exists in the collection.
- The folder is visible in the folder list.

### Failure Postconditions

- No folder is created.
- System displays an error message.

## Business Rules

### BR-012: Folder Scope

Folders are scoped to a collection; the same folder name can exist in different collections.

### BR-013: Duplicate Folder Names

Duplicate folder names are allowed within the same parent folder (no uniqueness constraint).
