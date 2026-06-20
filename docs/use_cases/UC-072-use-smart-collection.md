# Use Case: Use Smart Collection

## Overview

**Use Case ID:** UC-072   
**Use Case Name:** Use Smart Collection   
**Primary Actor:** User   
**Goal:** Access a saved search pinned as a virtual folder in the sidebar so that I can quickly view its live results with a single click.   
**Status:** Done

## Traceability

**Maps to:** FR-076, FR-077

---

## Preconditions

- The user is authenticated.
- At least one saved search exists for the current collection (UC-061).

## Main Success Scenario

1. User clicks on a smart collection (saved search) in the sidebar.
2. System evaluates the saved search's query against the current bookmark data.
3. System displays the live result set in the bookmark list view.
4. System shows a visual indicator that this is a smart collection (not a regular folder), along with the underlying query.
5. User browses the filtered bookmarks.

## Alternative Flows

### A1: No Results

**Trigger:** The query matches zero bookmarks (step 3).
**Flow:**

1. System displays empty list with the query shown and a message: "No bookmarks match this saved search."
2. Use case ends.

### A2: Edit Smart Collection

**Trigger:** User clicks the edit icon on the smart collection (step 4).
**Flow:**

1. System opens the saved search dialog pre-filled with the current name and query.
2. User modifies either and saves.
3. The smart collection updates immediately.
4. Use case ends.

### A3: Delete Smart Collection

**Trigger:** User clicks the delete icon on the smart collection (step 4).
**Flow:**

1. System shows a confirmation dialog.
2. On confirm, the smart collection is removed from the sidebar.
3. No bookmarks are deleted.
4. Use case ends.

### A4: Rename Smart Collection

**Trigger:** User clicks the rename icon on the smart collection (step 4).
**Flow:**

1. System shows a rename dialog.
2. User enters a new name.
3. System validates uniqueness and updates the name.
4. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark list shows the live result set of the saved query.

### Failure Postconditions

- Bookmark list shows previous state.

## Business Rules

### BR-089: Live Evaluation

Smart collections always show the current result set. They never cache or snapshot results.

### BR-090: Non-Destructive Deletion

Deleting a smart collection only removes the saved search. It never deletes bookmarks.

### BR-091: Smart Collection Ordering

Smart collections appear in the sidebar below regular folders, ordered alphabetically by name.
