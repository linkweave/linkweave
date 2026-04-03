# Use Case: Filter Bookmarks by Tag

## Overview

**Use Case ID:** UC-021   
**Use Case Name:** Filter Bookmarks by Tag   
**Primary Actor:** User   
**Goal:** View only bookmarks that have a specific tag applied.   
**Status:** Done   

## Traceability

**Maps to:** FR-021

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.
- At least one tag exists in the collection.

## Main Success Scenario

1. User views the tag list in a collection.
2. User clicks on a specific tag.
3. System filters the bookmark list to show only bookmarks with the selected tag.
4. System highlights the active filter in the UI.
5. System displays the count of matching bookmarks.

## Alternative Flows

### A1: No Bookmarks Match

**Trigger:** No bookmarks have the selected tag (step 3).
**Flow:**

1. System displays an empty bookmark list with a message: "No bookmarks with this tag."
2. Use case ends.

### A2: Clear Filter

**Trigger:** User wants to remove the tag filter (step 3).
**Flow:**

1. User clicks "Clear Filter" or selects "All Bookmarks."
2. System displays all bookmarks in the collection.
3. Use case ends.

### A3: Combine with Folder Filter

**Trigger:** User wants to filter by both tag and folder (step 3).
**Flow:**

1. User has already filtered by a folder.
2. User clicks on a tag.
3. System filters to show bookmarks that are both in the folder AND have the tag.
4. Use case continues at step 4.

### A4: Multiple Tag Filter

**Trigger:** User wants to filter by multiple tags (step 2).
**Flow:**

1. User selects multiple tags using checkboxes or Ctrl+Click.
2. System filters to show bookmarks that have ANY of the selected tags (OR logic).
3. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks with the selected tag(s).
- The active filter is clearly indicated.

### Failure Postconditions

- Bookmark list remains unfiltered.
- System displays an error message.

## Business Rules

### BR-044: Filter Persistence

The tag filter remains active until the user clears it or navigates away.

### BR-045: Filter Scope

Tag filtering only applies to the current collection.
