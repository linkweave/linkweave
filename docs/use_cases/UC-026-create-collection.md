# Use Case: Create Collection

## Overview

**Use Case ID:** UC-026   
**Use Case Name:** Create Collection   
**Primary Actor:** User   
**Goal:** Create a new collection so that the user can organize bookmarks in a separate workspace.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-036

---

## Preconditions

- The user is authenticated.

## Main Success Scenario

1. User opens the collection list.
2. User selects the option to create a new collection.
3. System displays a form with a collection name field.
4. User enters a collection name and confirms.
5. System validates the collection name is not empty and does not exceed the maximum length.
6. System creates the collection with the user as the owner.
7. System navigates the user to the newly created collection.

## Alternative Flows

### A1: Collection Name Is Empty

**Trigger:** The user submits the form without entering a name (step 5).
**Flow:**

1. System displays a validation error: "Collection name is required."
2. Use case continues at step 4.

### A2: Collection Name Exceeds Maximum Length

**Trigger:** The entered name exceeds the maximum allowed length (step 5).
**Flow:**

1. System displays a validation error indicating the maximum length.
2. Use case continues at step 4.

### A3: User Cancels

**Trigger:** The user cancels the creation form (step 4).
**Flow:**

1. System closes the form without creating a collection.
2. Use case ends.

## Postconditions

### Success Postconditions

- A new collection exists with the user as the owner.
- The user has full access to the collection.
- The new collection is not the user's default (the existing default remains unchanged).

### Failure Postconditions

- No new collection is created.
- The user remains on the collection list.

## Business Rules

### BR-049: Collection Name Uniqueness

A collection name does not need to be unique across the system; multiple users may have collections with the same name.

### BR-050: Collection Name Length

A collection name must be between 1 and 255 characters.

### BR-051: Ownership on Creation

The user who creates a collection is automatically assigned as the collection owner with full access rights.
