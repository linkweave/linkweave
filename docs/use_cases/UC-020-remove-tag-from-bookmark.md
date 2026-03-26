# Use Case: Remove Tag from Bookmark

## Overview

**Use Case ID:** UC-020   
**Use Case Name:** Remove Tag from Bookmark   
**Primary Actor:** User   
**Goal:** Remove an incorrect or unwanted tag from a bookmark.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one bookmark.
- The bookmark has at least one tag applied.

## Main Success Scenario

1. User selects a bookmark from the bookmark list.
2. User chooses "Manage Tags" from the bookmark's context menu.
3. System displays the tag management dialog showing currently applied tags.
4. User deselects one or more tags to remove.
5. User confirms the selection.
6. System removes the deselected tags from the bookmark.
7. System displays the updated tags on the bookmark.

## Alternative Flows

### A1: Remove All Tags

**Trigger:** User deselects all tags (step 4).
**Flow:**

1. System allows removing all tags (bookmark can have zero tags).
2. Use case continues at step 5.

### A2: Quick Remove from Bookmark Display

**Trigger:** User removes a tag directly from the bookmark's tag display (step 1).
**Flow:**

1. User clicks the remove icon (×) on a tag badge displayed on the bookmark.
2. System removes the tag from the bookmark immediately.
3. Use case ends.

### A3: Cancel Tag Management

**Trigger:** User cancels the tag management operation (step 4).
**Flow:**

1. System dismisses the tag management dialog.
2. Bookmark's tags remain unchanged.
3. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark no longer has the removed tags.
- The tags themselves remain in the collection for use on other bookmarks.

### Failure Postconditions

- Bookmark's tags remain unchanged.
- System displays an error message.

## Business Rules

### BR-042: Tag Preservation

Removing a tag from a bookmark does not delete the tag from the collection.

### BR-043: Empty Tags Allowed

A bookmark can have zero tags applied.
