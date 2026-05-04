# Use Case: Revoke Collection Access

## Overview

**Use Case ID:** UC-025   
**Use Case Name:** Revoke Collection Access   
**Primary Actor:** Collection Owner   
**Goal:** Remove another user's access to a shared collection so they can no longer view or modify its contents.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-026

---

## Preconditions

- The actor is authenticated.
- The actor owns the collection they want to revoke access to.
- At least one user other than the owner currently has access to the collection.

## Main Success Scenario

1. Collection Owner opens the collection settings.
2. Collection Owner selects the option to manage shared access.
3. System displays a list of users who currently have access to the collection.
4. Collection Owner selects a user and chooses "Revoke Access".
5. System prompts for confirmation.
6. Collection Owner confirms the revocation.
7. System removes the user's access record for the collection.
8. System confirms the revocation with a success message.

## Alternative Flows

### A1: Cancel Revocation

**Trigger:** Collection Owner cancels the revocation when prompted (step 6).
**Flow:**

1. System dismisses the confirmation dialog.
2. The user's access remains unchanged.
3. Use case ends.

### A2: Revoked User Currently Viewing Collection

**Trigger:** The user whose access is being revoked is currently viewing or editing the collection (step 7).
**Flow:**

1. System removes the access record.
2. On the revoked user's next API request, the system denies access and returns HTTP 403.
3. The revoked user's client displays an error and redirects to the collection list.

### A3: Last Shared User Revoked

**Trigger:** The revoked user is the only non-owner with access (step 7).
**Flow:**

1. System removes the user's access record.
2. System confirms the revocation and notes that the collection is no longer shared with anyone.
3. Use case ends.

## Postconditions

### Success Postconditions

- The target user no longer has access to the collection.
- The target user's collection list no longer includes the revoked collection.
- The collection's content is unchanged.

### Failure Postconditions

- The target user's access remains unchanged.
- System displays an error message.

## Business Rules

### BR-053: Owner-Only Revocation

Only the Collection Owner may revoke access to their collection.

### BR-054: Cannot Revoke Own Access

The owner cannot revoke their own access; the owner always has access to their collection.

### BR-055: Immediate Effect

Access revocation takes effect immediately; any subsequent API request by the revoked user to the collection is denied.
