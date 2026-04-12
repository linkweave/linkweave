# Use Case: Edit Collection

## Overview

**Use Case ID:** UC-027   
**Use Case Name:** Edit Collection   
**Primary Actor:** User   
**Goal:** Change a collection's name so that it accurately reflects its purpose.   
**Status:** Draft   

## Traceability

**Maps to:** FR-037

---

## Preconditions

- The user is authenticated.
- The user owns or has access to at least one collection.

## Main Success Scenario

1. User opens the collection settings for a collection they own.
2. System displays the collection details with an editable name field.
3. User modifies the collection name and confirms.
4. System validates the collection name is not empty and does not exceed the maximum length.
5. System updates the collection name.
6. System confirms the change with a success message.

## Alternative Flows

### A1: Collection Name Is Empty

**Trigger:** The user submits the form without entering a name (step 4).
**Flow:**

1. System displays a validation error: "Collection name is required."
2. Use case continues at step 3.

### A2: Collection Name Exceeds Maximum Length

**Trigger:** The entered name exceeds the maximum allowed length (step 4).
**Flow:**

1. System displays a validation error indicating the maximum length.
2. Use case continues at step 3.

### A3: User Cancels

**Trigger:** The user cancels the edit form (step 3).
**Flow:**

1. System closes the form without saving changes.
2. Use case ends.

### A4: Non-Owner Attempts Edit

**Trigger:** A user with shared (non-owner) access attempts to edit the collection name (step 1).
**Flow:**

1. System denies access and displays an error: "Only the collection owner can edit collection details."
2. Use case ends.

## Postconditions

### Success Postconditions

- The collection name is updated.
- All users with access to the collection see the updated name.

### Failure Postconditions

- The collection name remains unchanged.

## Business Rules

### BR-052: Owner-Only Edit

Only the Collection Owner may edit the collection's name.

### BR-053: Collection Name Length

A collection name must be between 1 and 255 characters (same as BR-050).
