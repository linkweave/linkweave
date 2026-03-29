# Use Case: Create Tag

## Overview

**Use Case ID:** UC-015   
**Use Case Name:** Create Tag   
**Primary Actor:** User   
**Goal:** Create a new tag to label and categorize bookmarks.   
**Status:** Draft   

## Traceability

**Maps to:** FR-015

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User selects "Create Tag" from the tag menu.
2. System displays the tag creation form.
3. User enters a tag name.
4. User submits the form.
5. System validates the tag name is not empty.
6. System validates the tag name is unique within the collection.
7. System auto-assigns a color from the predefined palette.
8. System creates the tag in the current collection.
9. System displays the new tag in the tag list with its assigned color.

## Alternative Flows

### A1: Empty Tag Name

**Trigger:** User submits the form without entering a name (step 4).
**Flow:**

1. System displays an error message: "Tag name is required."
2. User enters a tag name.
3. Use case continues at step 4.

### A2: Duplicate Tag Name

**Trigger:** User enters a tag name that already exists in the collection (step 6).
**Flow:**

1. System displays an error message: "A tag with this name already exists."
2. User enters a different tag name.
3. Use case continues at step 4.

### A3: Create During Bookmark Edit

**Trigger:** User creates a new tag while editing a bookmark (step 1).
**Flow:**

1. User is in the bookmark edit form and types a new tag name in the tag field.
2. System offers to create the new tag.
3. User confirms creation.
4. System creates the tag.
5. System applies the new tag to the bookmark.
6. Use case ends.

## Postconditions

### Success Postconditions

- A new tag exists in the collection with an auto-assigned color.
- The tag is visible in the tag list.
- The tag is available to be applied to bookmarks.

### Failure Postconditions

- No tag is created.
- System displays an error message.

## Business Rules

### BR-032: Tag Scope

Tags are scoped to a collection; the same tag name can exist in different collections.

### BR-033: Tag Uniqueness

Tag names must be unique within a collection.

### BR-034: Tag Name Length

Tag names must not exceed 50 characters.

### BR-051: Auto-Assigned Tag Color

When a tag is created, the system assigns a color from a predefined palette automatically.
