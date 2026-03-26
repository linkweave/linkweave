# Use Case: Filter Bookmarks by Folder

## Overview

**Use Case ID:** UC-022   
**Use Case Name:** Filter Bookmarks by Folder   
**Primary Actor:** User   
**Goal:** View only bookmarks that are in a specific folder.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.
- At least one folder exists in the collection.

## Main Success Scenario

1. User views the folder tree in a collection.
2. User clicks on a specific folder.
3. System filters the bookmark list to show only bookmarks in the selected folder.
4. System highlights the selected folder in the folder tree.
5. System displays the count of bookmarks in the folder.

## Alternative Flows

### A1: Empty Folder

**Trigger:** The selected folder contains no bookmarks (step 3).
**Flow:**

1. System displays an empty bookmark list with a message: "This folder is empty."
2. System offers options to create a bookmark or delete the folder.
3. Use case ends.

### A2: View All Bookmarks (Root)

**Trigger:** User wants to see all bookmarks not in any folder (step 1).
**Flow:**

1. User selects "Unfiled" or "Root" from the folder tree.
2. System displays bookmarks that are not in any folder.
3. Use case continues at step 4.

### A3: Navigate to Subfolder

**Trigger:** User clicks on a folder that has subfolders (step 2).
**Flow:**

1. System expands the folder to show subfolders.
2. User clicks on a subfolder.
3. System filters to show bookmarks in the subfolder only.
4. Use case continues at step 4.

### A4: Combine with Tag Filter

**Trigger:** User wants to filter by both folder and tag (step 3).
**Flow:**

1. User has already filtered by a folder.
2. User clicks on a tag.
3. System filters to show bookmarks that are both in the folder AND have the tag.
4. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks in the selected folder.
- The selected folder is highlighted in the folder tree.

### Failure Postconditions

- Bookmark list remains unfiltered.
- System displays an error message.

## Business Rules

### BR-046: Non-Recursive Display

Filtering by a folder shows only direct children; bookmarks in subfolders are not included.

### BR-047: Folder Selection Persistence

The folder selection remains active until the user selects a different folder or navigates away.
