# Use Case: Navigate Folder Hierarchy

## Overview

**Use Case ID:** UC-029   
**Use Case Name:** Navigate Folder Hierarchy via Breadcrumb   
**Primary Actor:** User   
**Goal:** See the current position in the folder tree and quickly navigate to ancestor folders.   
**Status:** Draft   

## Traceability

**Maps to:** FR-029

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.
- At least one folder exists in the collection.

## Main Success Scenario

1. User views a collection with a folder tree in the sidebar.
2. User clicks on a folder in the folder tree.
3. System highlights the selected folder in the sidebar.
4. System displays a breadcrumb above the bookmark list showing the full folder path: `All Bookmarks / Parent / SelectedFolder`.
5. System filters the bookmark list to show only bookmarks in the selected folder.
6. The last segment of the breadcrumb (current folder) is visually emphasized (bold, foreground color).

## Alternative Flows

### A1: Navigate to Parent via Breadcrumb

**Trigger:** User clicks an ancestor folder segment in the breadcrumb (step 4).
**Flow:**

1. System updates the selected folder to the clicked ancestor.
2. System updates the breadcrumb to show the shorter path.
3. System filters bookmarks to the ancestor folder.
4. System updates the sidebar highlight to the new selection.
5. Use case continues at step 4.

### A2: Navigate to Root via Breadcrumb

**Trigger:** User clicks "All Bookmarks" in the breadcrumb (step 4).
**Flow:**

1. System sets the selected folder to `null` (root).
2. System hides the breadcrumb (no folder selected).
3. System displays all bookmarks in the collection (unfiltered).
4. System updates the sidebar to highlight "All Bookmarks".
5. Use case ends.

### A3: Navigate to Root via Sidebar

**Trigger:** User clicks "All Bookmarks" in the sidebar (step 1).
**Flow:**

1. System sets the selected folder to `null` (root).
2. System hides the breadcrumb.
3. System displays all bookmarks in the collection.
4. Use case ends.

### A4: Delete Selected Folder

**Trigger:** User deletes the currently selected folder (step 5).
**Flow:**

1. System resets the selection to `null` (root).
2. System hides the breadcrumb.
3. System displays all bookmarks in the collection.
4. Use case ends.

## Postconditions

### Success Postconditions

- The breadcrumb accurately reflects the path to the selected folder.
- The bookmark list shows only bookmarks in the selected folder.
- The selected folder is highlighted in the sidebar.

### Failure Postconditions

- The breadcrumb and bookmark list remain in their previous state.
- System displays an error message.

## Business Rules

### BR-050: Client-Side Folder Filtering

Filtering bookmarks by folder is performed client-side. All bookmarks for the collection are fetched once and filtered by matching `bookmark.data.folderId` against the selected folder ID. Only direct children are shown (non-recursive).

### BR-051: Folder Path Resolution

The breadcrumb path is built by walking the `parentId` chain from the selected folder upward to the root. Folders are ordered root-first in the path array.

### BR-052: Selection Reset on Folder Deletion

When the currently selected folder is deleted, the selection resets to `null` (All Bookmarks view).
