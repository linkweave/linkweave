# Use Case: Delete Tag

## Overview

**Use Case ID:** UC-018   
**Use Case Name:** Delete Tag   
**Primary Actor:** User   
**Goal:** Remove a tag that is no longer needed from the collection.   
**Status:** Draft   

## Traceability

**Maps to:** FR-018

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one tag.
- The tag to delete exists.

## Main Success Scenario

1. User selects a tag from the tag list.
2. User chooses "Delete" from the tag's context menu.
3. System prompts for confirmation.
4. User confirms the deletion.
5. System removes all bookmark associations with the tag.
6. System deletes the tag.
7. System updates the tag list to remove the deleted tag.

## Alternative Flows

### A1: Cancel Deletion

**Trigger:** User cancels the deletion when prompted (step 4).
**Flow:**

1. System dismisses the confirmation dialog.
2. Tag and its associations remain unchanged.
3. Use case ends.

### A2: Tag in Use

**Trigger:** The tag is associated with one or more bookmarks (step 3).
**Flow:**

1. System prompts for confirmation, indicating how many bookmarks are currently using this tag.
2. User confirms the deletion.
3. Use case continues at step 5.

## Postconditions

### Success Postconditions

- The tag no longer exists in the collection.
- All bookmark associations with the tag are removed.
- Bookmarks that were tagged are not deleted.

### Failure Postconditions

- Tag and its associations remain unchanged.
- System displays an error message.

## Business Rules

### BR-038: Bookmark Preservation

Deleting a tag does not delete the bookmarks that were tagged; only the tag association is removed.

### BR-039: Undo Not Supported

Tag deletion is permanent; there is no undo functionality.
