# Use Case: Edit Tag

## Overview

**Use Case ID:** UC-017   
**Use Case Name:** Edit Tag   
**Primary Actor:** User   
**Goal:** Change the name and/or color of an existing tag.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one tag.
- The tag to edit exists.

## Main Success Scenario

1. User selects a tag from the tag list.
2. User chooses "Edit" from the tag's context menu.
3. System displays the edit form with the current tag name and color.
4. User modifies the tag name and/or color.
5. User submits the form.
6. System validates the name is not empty.
7. System validates the name is unique within the collection (if name changed).
8. System updates the tag.
9. System displays the updated tag in the tag list.

## Alternative Flows

### A1: Empty Tag Name

**Trigger:** User clears the name field and submits (step 5).
**Flow:**

1. System displays an error message: "Tag name is required."
2. User enters a tag name.
3. Use case continues at step 5.

### A2: Duplicate Tag Name

**Trigger:** User enters a name that already exists in the collection (step 7).
**Flow:**

1. System displays an error message: "A tag with this name already exists."
2. User enters a different tag name.
3. Use case continues at step 5.

### A3: Cancel Edit

**Trigger:** User cancels the edit operation (step 4).
**Flow:**

1. System discards the changes.
2. Tag remains unchanged.
3. Use case ends.

### A4: No Changes Made

**Trigger:** User submits the form without modifying any field (step 5).
**Flow:**

1. System accepts the submission (no change is a valid operation).
2. Use case continues at step 8.

## Postconditions

### Success Postconditions

- The tag has updated name and/or color.
- All bookmarks previously tagged with this tag still have the tag applied.
- The tag's associations with bookmarks are preserved.

### Failure Postconditions

- Tag name remains unchanged.
- System displays an error message.

## Business Rules

### BR-037: Edit Preserves Associations

Editing a tag does not affect which bookmarks are tagged with it.

### BR-052: Tag Color Format

Tag colors must be valid 7-character hex color codes (e.g., #3b82f6).
