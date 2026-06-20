# Use Case: Batch Move and Delete Bookmarks

## Overview

**Use Case ID:** UC-074   
**Use Case Name:** Batch Move and Delete Bookmarks   
**Primary Actor:** User   
**Goal:** Move multiple bookmarks to a folder or delete them in one action so that I can reorganize or clean up efficiently.   
**Status:** Done

## Traceability

**Maps to:** FR-078, FR-080

---

## Preconditions

- The user is authenticated.
- The user has write access to the collection.
- The user has selected two or more bookmarks.

## Main Success Scenario (Move)

1. User selects multiple bookmarks in the bookmark list.
2. System displays a batch action toolbar showing the number of selected bookmarks.
3. User clicks the "Move" button in the batch toolbar.
4. System displays a folder selection dialog (same as single-bookmark move dialog).
5. User selects a target folder (or "No Folder" for root).
6. User confirms the move.
7. System moves all selected bookmarks to the target folder atomically.
8. System displays a success toast: "Moved N bookmarks to [folder name]."
9. System clears the selection.

## Main Success Scenario (Delete)

1. User selects multiple bookmarks in the bookmark list.
2. System displays a batch action toolbar showing the number of selected bookmarks.
3. User clicks the "Delete" button in the batch toolbar.
4. System displays a confirmation dialog: "Move N bookmarks to trashbin?"
5. User confirms.
6. System soft-deletes all selected bookmarks (sets deletedAt) atomically.
7. System displays a success toast: "N bookmarks moved to trashbin."
8. System clears the selection and refreshes the trashbin count.

## Alternative Flows

### A1: Partial Move Failure

**Trigger:** One or more bookmarks fail to move (step 7 of Move).
**Flow:**

1. System rolls back all moves.
2. System displays error: "Failed to move bookmarks. No changes were made."
3. Use case ends.

### A2: Partial Delete Failure

**Trigger:** One or more bookmarks fail to delete (step 6 of Delete).
**Flow:**

1. System rolls back all deletions.
2. System displays error: "Failed to delete bookmarks. No changes were made."
3. Use case ends.

### A3: Cancel Delete

**Trigger:** User cancels the confirmation dialog (step 5 of Delete).
**Flow:**

1. System closes the dialog.
2. Selection is maintained.
3. No changes are made.

### A4: Batch Exceeds Limit

**Trigger:** More than 500 bookmarks are selected (step 7 of Move or step 6 of Delete).
**Flow:**

1. System rejects the batch with a validation error (HTTP 400) indicating the 500-bookmark limit (C-017). The caller must reduce the selection and retry.

## Postconditions

### Success Postconditions

- (Move) All selected bookmarks are in the target folder. Folder bookmark counts are updated.
- (Delete) All selected bookmarks are in the trashbin. Trashbin count is updated.

### Failure Postconditions

- No bookmarks are moved or deleted.

## Business Rules

### BR-097: Batch Move Atomicity

Batch move operations are atomic (NFR-018). Either all bookmarks are moved or none are.

### BR-098: Batch Delete Atomicity

Batch delete operations are atomic. Either all bookmarks are soft-deleted or none are.

### BR-099: Soft Delete Only

Batch delete always moves bookmarks to the trashbin. Permanent deletion must be done from the trashbin view one at a time or via "Empty Trashbin."

### BR-100: Folder Validation

The target folder must belong to the same collection as the bookmarks. Cross-collection moves are not supported.

### BR-101: Move to Root

Selecting "No Folder" moves bookmarks to the collection root (folder = null).
