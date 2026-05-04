# Use Case: Create Bookmark

## Overview

**Use Case ID:** UC-005   
**Use Case Name:** Create Bookmark   
**Primary Actor:** User   
**Goal:** Save a web resource with a URL and title to the collection.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-005

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User selects "Create Bookmark" from the bookmark menu.
2. System displays the bookmark creation form.
3. User enters a URL.
4. User enters a title.
5. User optionally enters a description.
6. User optionally selects a folder from the folder dropdown.
7. User optionally selects one or more tags from the tag dropdown.
8. User submits the form.
9. System validates that URL and title are not empty.
10. System validates the URL format.
11. System creates the bookmark in the current collection, associated with the selected folder and tags if any were chosen.
12. System displays the new bookmark in the bookmark list.

## Alternative Flows

### A1: Empty URL or Title

**Trigger:** User submits the form without entering a URL or title (step 8).
**Flow:**

1. System displays an error message indicating which field is required.
2. User fills in the missing field(s).
3. Use case continues at step 8.

### A2: Invalid URL Format

**Trigger:** User enters a malformed URL (step 10).
**Flow:**

1. System displays an error message: "Please enter a valid URL."
2. User corrects the URL.
3. Use case continues at step 8.

### A3: Auto-Fetch Title

**Trigger:** User enters only a URL and requests auto-fetch (step 3).
**Flow:**

1. System attempts to fetch the page title from the URL.
2. If successful, System populates the title field with the fetched title.
3. If unsuccessful, System leaves the title field empty for manual entry.
4. Use case continues at step 5.

### A4: Create New Tag During Creation

**Trigger:** User types a tag name that does not exist in the collection (step 7).
**Flow:**

1. System displays an option to create the new tag.
2. User confirms creation.
3. System creates the new tag and adds it to the selection.
4. Use case continues at step 7.

### A5: Create Bookmark from Folder View

**Trigger:** User creates a bookmark while viewing a specific folder (step 1).
**Flow:**

1. User opens a folder and selects "Create Bookmark" from within the folder.
2. System displays the bookmark creation form with the current folder pre-selected.
3. Use case continues at step 3.

## Postconditions

### Success Postconditions

- A new bookmark exists in the collection.
- The bookmark is associated with the selected folder (if any).
- The bookmark has the selected tags applied (if any).
- The bookmark is visible in the bookmark list.

### Failure Postconditions

- No bookmark is created.
- System displays an error message.

## Business Rules

### BR-025: URL Format

The URL must be a valid HTTP or HTTPS URL.

### BR-026: Duplicate URLs

Duplicate URLs are allowed within the same collection. The system displays a non-blocking warning when a duplicate URL is detected (see UC-053).
