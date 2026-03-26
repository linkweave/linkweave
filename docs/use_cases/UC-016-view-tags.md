# Use Case: View Tags

## Overview

**Use Case ID:** UC-016   
**Use Case Name:** View Tags   
**Primary Actor:** User   
**Goal:** See all tags in a collection to understand available categories.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User opens a collection.
2. System displays the tag list showing all tags in the collection.
3. System shows the number of bookmarks associated with each tag.

## Alternative Flows

### A1: No Tags Exist

**Trigger:** The collection has no tags (step 2).
**Flow:**

1. System displays an empty tag section with a message: "No tags yet."
2. Use case ends.

### A2: View Bookmarks by Tag

**Trigger:** User clicks on a tag (step 2).
**Flow:**

1. System filters the bookmark list to show only bookmarks with that tag.
2. Use case continues.

## Postconditions

### Success Postconditions

- The user can see all tags in the collection.
- The bookmark count for each tag is displayed.

### Failure Postconditions

- System displays an error message.
- Tag list is not shown.

## Business Rules

### BR-035: Tag Visibility

A user can only view tags in collections they own or have been granted access to.

### BR-036: Unused Tags

Tags with zero bookmarks are still displayed in the tag list.
