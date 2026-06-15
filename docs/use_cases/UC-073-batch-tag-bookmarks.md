# Use Case: Batch Tag Bookmarks

## Overview

**Use Case ID:** UC-073   
**Use Case Name:** Batch Tag Bookmarks   
**Primary Actor:** User   
**Goal:** Add or remove tags from multiple bookmarks at once so that I can categorize large groups efficiently.   
**Status:** Open   

## Traceability

**Maps to:** FR-078, FR-079

---

## Preconditions

- The user is authenticated.
- The user has write access to the collection.
- The user has selected two or more bookmarks (via checkboxes or Shift/Ctrl+Click).

## Main Success Scenario

1. User selects multiple bookmarks in the bookmark list.
2. System displays a batch action toolbar showing the number of selected bookmarks.
3. User clicks the "Tag" button in the batch toolbar.
4. System displays a batch tag dialog showing all tags in the collection as toggleable chips.
5. User toggles tags on (to add) or off (to remove) for the selected bookmarks.
6. System shows a preview: "Add [tag1, tag2] to 15 bookmarks. Remove [tag3] from 3 bookmarks."
7. User confirms the operation.
8. System applies the tag changes to all selected bookmarks atomically.
9. System displays a success toast with the count of affected bookmarks.
10. System clears the selection and hides the batch toolbar.

## Alternative Flows

### A2: Partial Failure

**Trigger:** One or more bookmarks fail to update (step 8).
**Flow:**

1. System rolls back all changes (NFR-018).
2. System displays an error: "Failed to update tags. No changes were made."
3. Use case ends.

### A3: Select All

**Trigger:** User clicks "Select All" checkbox (step 1).
**Flow:**

1. System selects all bookmarks currently visible (respecting active filters).
2. If the operation is submitted with more than 500 bookmarks selected, the system rejects it with an error indicating the 500-bookmark limit (C-017).
3. Use case continues at step 2.

### A4: Deselect All

**Trigger:** User clicks "Clear Selection" (step 2).
**Flow:**

1. System deselects all bookmarks and hides the batch toolbar.
2. Use case ends.

### A5: No Collection Access

**Trigger:** User is a read-only member of the collection (step 3).
**Flow:**

1. System disables the "Tag" button.
2. Use case cannot proceed.

## Postconditions

### Success Postconditions

- All selected bookmarks have the tag changes applied.
- Tag counts in the sidebar are updated.

### Failure Postconditions

- No bookmarks are modified. All changes are rolled back.

## Business Rules

### BR-092: Atomic Batch

Batch tag operations are atomic. Either all selected bookmarks are updated or none are.

### BR-093: Tag Toggle Semantics

Toggling a tag ON adds it to all selected bookmarks that don't already have it. Toggling a tag OFF removes it from all selected bookmarks that have it. Bookmarks unaffected by a toggle are not modified.

### BR-094: Batch Size Limit

Up to 500 bookmarks may be processed in a single batch (C-017). Larger selections are rejected with a validation error.

### BR-095: Selection Persistence

The selection is maintained when scrolling the bookmark list. Selected count is always visible in the batch toolbar.

### BR-096: No Duplicates

Adding a tag that a bookmark already has is a no-op for that bookmark. No error is raised.
