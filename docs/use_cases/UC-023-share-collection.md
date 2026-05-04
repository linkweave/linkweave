# Use Case: Share Collection

## Overview

**Use Case ID:** UC-023   
**Use Case Name:** Share Collection   
**Primary Actor:** Collection Owner   
**Goal:** Grant another user access to a collection so they can collaborate on the shared bookmark collection.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-023

---

## Preconditions

- The actor is authenticated.
- The actor owns the collection they want to share.
- The target user exists in the system.

## Main Success Scenario

1. Collection Owner opens the collection settings.
2. Collection Owner selects the option to share the collection.
3. System displays the share form.
4. Collection Owner enters the username of the user to share with.
5. System validates that the entered username exists.
6. System grants the target user access to the collection.
7. System confirms the share with a success message.

## Alternative Flows

### A1: Username Does Not Exist

**Trigger:** The entered username is not found in the system (step 5).
**Flow:**

1. System displays an error message: "User not found."
2. Collection Owner corrects the username or cancels.
3. Use case continues at step 4.

### A2: Collection Already Shared With This User

**Trigger:** The target user already has access to this collection (step 5).
**Flow:**

1. System displays an informational message: "This user already has access."
2. No duplicate access record is created.
3. Use case ends.

### A3: Owner Attempts to Share With Themselves

**Trigger:** The entered username matches the Collection Owner's own username (step 5).
**Flow:**

1. System displays an error message: "You cannot share a collection with yourself."
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The target user has access to the collection.
- The target user can view and manage bookmarks in the shared collection (via UC-024).

### Failure Postconditions

- No new access is granted.
- The collection's access list remains unchanged.

## Business Rules

### BR-007: Owner-Only Sharing

Only the Collection Owner may share or revoke access to their collection.

### BR-008: No Duplicate Access

A user may hold at most one access record per collection; sharing the same collection twice with the same user is a no-op.
