# Use Case: Edit Bookmark

## Overview

**Use Case ID:** UC-007   
**Use Case Name:** Edit Bookmark   
**Primary Actor:** User   
**Goal:** Modify a bookmark's URL, title, description, folder, or tags to keep it up to date.   
**Status:** Draft   

## Traceability

**Maps to:** FR-007

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one bookmark.
- The bookmark to edit exists.

## Main Success Scenario

1. User selects a bookmark from the bookmark list.
2. User chooses "Edit" from the bookmark's context menu.
3. System displays the edit form with the current bookmark data (URL, title, description, folder, and tags).
4. User modifies the URL, title, description, folder, or tags.
5. User submits the form.
6. System validates that URL and title are not empty.
7. System validates the URL format.
8. System updates the bookmark with all changes.
9. System displays the updated bookmark in the bookmark list.

## Alternative Flows

### A1: Empty URL or Title

**Trigger:** User clears the URL or title field and submits (step 5).
**Flow:**

1. System displays an error message indicating which field is required.
2. User fills in the missing field(s).
3. Use case continues at step 5.

### A2: Invalid URL Format

**Trigger:** User enters a malformed URL (step 7).
**Flow:**

1. System displays an error message: "Please enter a valid URL."
2. User corrects the URL.
3. Use case continues at step 5.

### A3: Cancel Edit

**Trigger:** User cancels the edit operation (step 4).
**Flow:**

1. System discards the changes.
2. Bookmark data remains unchanged.
3. Use case ends.

### A4: No Changes Made

**Trigger:** User submits the form without modifying any field (step 5).
**Flow:**

1. System accepts the submission (no change is a valid operation).
2. Use case continues at step 8.

### A5: Create New Tag During Edit

**Trigger:** User types a tag name that does not exist in the collection (step 4).
**Flow:**

1. System displays an option to create the new tag.
2. User confirms creation.
3. System creates the new tag and adds it to the selection.
4. Use case continues at step 4.

### A6: Remove All Tags

**Trigger:** User deselects all tags in the tag dropdown (step 4).
**Flow:**

1. System allows removing all tags (bookmark can have zero tags).
2. Use case continues at step 5.

## Postconditions

### Success Postconditions

- The bookmark has updated URL, title, description, folder, or tags as specified.
- The bookmark's updated_at timestamp is set to the current time.

### Failure Postconditions

- Bookmark data remains unchanged.
- System displays an error message.

## Business Rules

### BR-029: All Fields Editable

Editing a bookmark allows changes to URL, title, description, folder, and tags in a single form.

### BR-030: Empty Tags Allowed

A bookmark can have zero tags applied.
