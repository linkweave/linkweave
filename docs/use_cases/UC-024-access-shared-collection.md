# Use Case: Access Shared Collection

## Overview

**Use Case ID:** UC-024   
**Use Case Name:** Access Shared Collection   
**Primary Actor:** User   
**Goal:** Open and work with a collection that another user has shared with them.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-024

---

## Preconditions

- The user is authenticated.
- Another user has shared a collection with this user (via UC-023).

## Main Success Scenario

1. User navigates to the collection list (UC-003).
2. System displays all collections the user has access to, including shared collections.
3. User selects a shared collection from the list.
4. System navigates the user to the shared collection.
5. User can view and manage bookmarks within the shared collection.

## Alternative Flows

### A1: Access Has Been Revoked

**Trigger:** The user selects a shared collection whose access has since been revoked (step 3).
**Flow:**

1. System denies access and displays an error message: "You no longer have access to this collection."
2. System removes the collection from the user's collection list.
3. Use case ends.

### A2: Shared Collection No Longer Exists

**Trigger:** The collection was deleted by the owner after being shared (step 3).
**Flow:**

1. System displays an error message: "This collection is no longer available."
2. System removes the stale entry from the user's collection list.
3. Use case ends.

## Postconditions

### Success Postconditions

- The user is viewing the shared collection and can manage its bookmarks.

### Failure Postconditions

- The user is not granted access to the collection.
- System displays an appropriate error message.

## Business Rules

### BR-009: Read and Write Access

A user granted access to a shared collection may view and manage all bookmarks, folders, and tags within that collection.

### BR-010: No Ownership Transfer

Being granted access to a shared collection does not make the user an owner; only the original Collection Owner can share or delete the collection.

### BR-011: Access Boundary

A user may only access resources within collections they own or have been granted access to; access to other collections returns HTTP 403.
