# Use Case: Apply Tag to Bookmark

## Overview

**Use Case ID:** UC-019   
**Use Case Name:** Apply Tag to Bookmark   
**Primary Actor:** User   
**Goal:** Categorize a bookmark by applying one or more tags to it.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one bookmark.
- At least one tag exists in the collection.

## Main Success Scenario

1. User selects a bookmark from the bookmark list.
2. User chooses "Manage Tags" from the bookmark's context menu.
3. System displays the tag management dialog with available tags.
4. User selects one or more tags to apply.
5. User confirms the selection.
6. System applies the selected tags to the bookmark.
7. System displays the updated tags on the bookmark.

## Alternative Flows

### A1: Create New Tag

**Trigger:** User wants to apply a tag that does not exist yet (step 4).
**Flow:**

1. User types a new tag name in the tag input field.
2. System offers to create the new tag.
3. User confirms creation.
4. System creates the tag.
5. System applies the new tag to the bookmark.
6. Use case continues at step 7.

### A2: Tag Already Applied

**Trigger:** User selects a tag that is already applied to the bookmark (step 4).
**Flow:**

1. System ignores the duplicate selection (no error).
2. Use case continues at step 5.

### A3: Cancel Tag Management

**Trigger:** User cancels the tag management operation (step 4).
**Flow:**

1. System dismisses the tag management dialog.
2. Bookmark's tags remain unchanged.
3. Use case ends.

### A4: Quick Tag from Bookmark List

**Trigger:** User applies a tag directly from the bookmark list view (step 1).
**Flow:**

1. User clicks the tag icon on a bookmark card.
2. System displays a quick tag selection dropdown.
3. User selects tags to apply or remove.
4. System updates the bookmark's tags immediately.
5. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark has the selected tags applied.
- The tags are visible on the bookmark.

### Failure Postconditions

- Bookmark's tags remain unchanged.
- System displays an error message.

## Business Rules

### BR-040: Multiple Tags

A bookmark can have multiple tags applied simultaneously.

### BR-041: Cross-Collection Tags

Tags from other collections cannot be applied to a bookmark; only tags within the same collection are available.
