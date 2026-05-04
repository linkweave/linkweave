# Use Case: Delete Collection

## Overview

**Use Case ID:** UC-028   
**Use Case Name:** Delete Collection   
**Primary Actor:** User   
**Goal:** Delete a collection and all its contents so that resources no longer needed are removed from the system.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-038

---

## Preconditions

- The user is authenticated.
- The user owns at least one collection.

## Main Success Scenario

1. User opens the collection settings for a collection they own.
2. User selects the option to delete the collection.
3. System prompts the user with a confirmation dialog requiring them to type the collection name.
4. User types the collection name and confirms the deletion.
5. System validates the entered name matches the collection name exactly.
6. System deletes the collection and all associated bookmarks, folders, tags, and access grants.
7. System navigates the user to their next available collection (or the collection list if multiple remain).

## Alternative Flows

### A1: Name Does Not Match

**Trigger:** The entered name does not match the collection name exactly (step 5).
**Flow:**

1. System displays an error: "The entered name does not match the collection name."
2. Use case continues at step 4.

### A2: User Cancels

**Trigger:** The user cancels the confirmation dialog (step 4).
**Flow:**

1. System closes the confirmation dialog.
2. No data is deleted.
3. Use case ends.

### A3: Non-Owner Attempts Delete

**Trigger:** A user with shared (non-owner) access attempts to delete the collection (step 1).
**Flow:**

1. System denies access and displays an error: "Only the collection owner can delete a collection."
2. Use case ends.

### A4: Last Collection Deleted

**Trigger:** The user deletes their only collection (step 6).
**Flow:**

1. System deletes the collection.
2. System auto-provisions a new default collection for the user (as per UC-001).
3. System navigates the user to the new collection.

### A5: Default Collection Deleted

**Trigger:** The deleted collection was the user's default (step 6).
**Flow:**

1. System deletes the collection.
2. System sets the user's next available collection as the new default.
3. System navigates the user to the new default collection.

## Postconditions

### Success Postconditions

- The collection and all its bookmarks, folders, tags, and access grants are permanently removed.
- Shared users can no longer access the deleted collection.
- If the deleted collection was the default, a new default is assigned.

### Failure Postconditions

- No data is deleted.
- The collection and all its contents remain unchanged.

## Business Rules

### BR-054: Owner-Only Deletion

Only the Collection Owner may delete a collection.

### BR-055: Cascade Deletion

Deleting a collection must cascade and remove all associated bookmarks, folders, tags, and access grants.

### BR-056: Default Reassignment

When the default collection is deleted, the user's oldest remaining collection becomes the new default. If no collections remain, a new default collection is auto-provisioned.

### BR-057: Confirmation by Name

The user must type the exact collection name to confirm deletion, preventing accidental data loss.
