# Use Case: View Bookmarks

## Overview

**Use Case ID:** UC-006   
**Use Case Name:** View Bookmarks   
**Primary Actor:** User   
**Goal:** Browse all bookmarks in a collection to find saved resources.   
**Status:** Draft   

## Traceability

**Maps to:** FR-006

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is viewing a collection.

## Main Success Scenario

1. User opens a collection.
2. System displays all bookmarks in the collection.
3. System shows each bookmark's title, URL, and description (if present).
4. System displays any tags applied to each bookmark.

## Alternative Flows

### A1: No Bookmarks Exist

**Trigger:** The collection has no bookmarks (step 2).
**Flow:**

1. System displays an empty bookmark section with a message: "No bookmarks yet."
2. System displays a prompt to create the first bookmark.
3. Use case ends.

### A2: View Bookmark Details

**Trigger:** User clicks on a bookmark to view details (step 2).
**Flow:**

1. System displays the bookmark's full details including URL, title, description, tags, and folder.
2. User can edit or delete the bookmark from this view.
3. Use case continues.

### A3: Open Bookmark URL

**Trigger:** User clicks the bookmark's URL (step 2).
**Flow:**

1. System opens the URL in a new browser tab or window.
2. Use case continues.

## Postconditions

### Success Postconditions

- The user can see all bookmarks in the collection.
- Bookmark metadata (title, URL, description, tags) is displayed.

### Failure Postconditions

- System displays an error message.
- Bookmark list is not shown.

## Business Rules

### BR-027: Bookmark Visibility

A user can only view bookmarks in collections they own or have been granted access to.

### BR-028: Display Order

Bookmarks are displayed in reverse chronological order by creation date by default (newest first).
