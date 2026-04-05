# Use Case: Search Bookmarks

## Overview

**Use Case ID:** UC-032   
**Use Case Name:** Search Bookmarks   
**Primary Actor:** User   
**Goal:** Find specific bookmarks by typing a search query that matches against title, URL, and tag name.   
**Status:** Draft   

## Traceability

**Maps to:** FR-032

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

## Main Success Scenario

1. User clicks on the search bar (or activates it via keyboard shortcut, e.g. ⌘E / Ctrl+E).
2. User types a search query.
3. System filters the bookmark list in real time to show only bookmarks where the query matches the bookmark's title, URL, or tag name (case-insensitive substring match).
4. System highlights the active search in the UI.
5. System displays the count of matching bookmarks.

## Alternative Flows

### A1: No Results Found

**Trigger:** No bookmarks match the search query (step 3).
**Flow:**

1. System displays an empty bookmark list with a message: "No bookmarks found."
2. Use case ends.

### A2: Clear Search

**Trigger:** User wants to remove the search filter (step 3).
**Flow:**

1. User clicks the clear button (X) in the search bar or deletes the query text.
2. System displays all bookmarks in the current context (collection, folder, or tag filter).
3. Use case ends.

### A3: Combine with Tag Filter

**Trigger:** User wants to search within bookmarks that have specific tags (step 3).
**Flow:**

1. User selects one or more tags from the tag filter.
2. User types a search query.
3. System filters to show bookmarks that match the search query AND have all selected tags.
4. Use case continues at step 4.

### A4: Combine with Folder Filter

**Trigger:** User wants to search within a specific folder (step 3).
**Flow:**

1. User selects a folder from the folder tree.
2. User types a search query.
3. System filters to show bookmarks that match the search query AND are in the selected folder.
4. Use case continues at step 4.

### A5: Combine with Tag and Folder Filters

**Trigger:** User wants to search within a folder and with specific tags (step 3).
**Flow:**

1. User selects a folder from the folder tree.
2. User selects one or more tags from the tag filter.
3. User types a search query.
4. System filters to show bookmarks that match the search query AND are in the selected folder AND have all selected tags.
5. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks matching the search query (and any active tag/folder filters).
- The active search query is visible in the search bar.

### Failure Postconditions

- Bookmark list remains unfiltered.
- System displays an error message.

## Business Rules

### BR-048: Case-Insensitive Search

The search query is matched case-insensitively against bookmark titles, URLs, and tag names.

### BR-049: Substring Matching

The search uses substring matching (partial match), not exact or prefix-only matching.

### BR-050: Combined Filter Logic

When a search query, selected tags, and/or a selected folder are all active, they are combined using AND logic. A bookmark must satisfy all active conditions to be displayed.

### BR-051: Search Scope

Search only applies to the current collection.

### BR-052: Minimum Query Length

Search filtering is only applied when the search query contains at least 2 characters. Queries with 0 or 1 character do not filter the bookmark list.

### BR-053: Real-Time Filtering

Results update in real time as the user types, without requiring a submit action.
