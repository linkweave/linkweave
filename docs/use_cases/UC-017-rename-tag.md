# Use Case: Rename Tag

## Overview

**Use Case ID:** UC-017   
**Use Case Name:** Rename Tag   
**Primary Actor:** User   
**Goal:** Change the name of an existing tag to correct or improve labeling.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to a collection containing at least one tag.
- The tag to rename exists.

## Main Success Scenario

1. User selects a tag from the tag list.
2. User chooses "Rename" from the tag's context menu.
3. System displays the rename form with the current tag name.
4. User enters a new tag name.
5. User submits the form.
6. System validates the new name is not empty.
7. System validates the new name is unique within the collection.
8. System updates the tag name.
9. System displays the updated tag name in the tag list.

## Alternative Flows

### A1: Empty Tag Name

**Trigger:** User submits the form without entering a name (step 5).
**Flow:**

1. System displays an error message: "Tag name is required."
2. User enters a tag name.
3. Use case continues at step 5.

### A2: Duplicate Tag Name

**Trigger:** User enters a tag name that already exists in the collection (step 7).
**Flow:**

1. System displays an error message: "A tag with this name already exists."
2. User enters a different tag name.
3. Use case continues at step 5.

### A3: Cancel Rename

**Trigger:** User cancels the rename operation (step 4).
**Flow:**

1. System discards the changes.
2. Tag name remains unchanged.
3. Use case ends.

### A4: Same Name Entered

**Trigger:** User enters the same name as the current tag name (step 4).
**Flow:**

1. System accepts the input (no change is a valid operation).
2. Use case continues at step 8.

## Postconditions

### Success Postconditions

- The tag has a new name.
- All bookmarks previously tagged with this tag still have the tag applied.
- The tag's associations with bookmarks are preserved.

### Failure Postconditions

- Tag name remains unchanged.
- System displays an error message.

## Business Rules

### BR-037: Rename Preserves Associations

Renaming a tag does not affect which bookmarks are tagged with it.
