# Use Case: View Folders

## Overview

**Use Case ID:** UC-010   
**Use Case Name:** View Folders   
**Primary Actor:** User   
**Goal:** See all folders in a collection to understand the organizational structure.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User opens a collection.
2. System displays the folder tree showing all folders in hierarchical order.
3. System shows the number of bookmarks contained in each folder.

## Alternative Flows

### A1: No Folders Exist

**Trigger:** The collection has no folders (step 2).
**Flow:**

1. System displays an empty folder section with a message: "No folders yet."
2. Use case ends.

### A2: Expand/Collapse Folder

**Trigger:** User clicks on a folder that has subfolders (step 2).
**Flow:**

1. System toggles the expanded/collapsed state of the folder.
2. Subfolders are shown or hidden accordingly.
3. Use case continues.

## Postconditions

### Success Postconditions

- The user can see all folders in the collection.
- The hierarchical structure is clearly displayed.

### Failure Postconditions

- System displays an error message.
- Folder list is not shown.

## Business Rules

### BR-014: Folder Visibility

A user can only view folders in collections they own or have been granted access to.

### BR-015: Bookmark Count

The bookmark count displayed for a folder includes only direct children, not bookmarks in subfolders.
