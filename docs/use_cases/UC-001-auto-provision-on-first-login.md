# Use Case: Auto-Provision on First Login

## Overview

**Use Case ID:** UC-001   
**Use Case Name:** Auto-Provision on First Login   
**Primary Actor:** User   
**Goal:** Automatically receive a default collection on first login so bookmarks can be saved immediately without manual setup.   
**Status:** Draft   

## Traceability

**Maps to:** FR-001

---

## Preconditions

- The user has valid credentials in the system.
- The user has never logged in before (no existing collection for this user).

## Main Success Scenario

1. User submits valid login credentials.
2. System authenticates the user.
3. System detects that no collection exists for this user.
4. System creates a default collection named "My Bookmarks" owned by the user.
5. System navigates the user directly to the new collection.

## Alternative Flows

### A1: User Already Has a Collection

**Trigger:** System detects an existing collection for the user (step 3).
**Flow:**

1. System skips auto-provisioning.
2. Use case continues with UC-002 (Auto-Navigate to Collection).

## Postconditions

### Success Postconditions

- A default collection exists for the user.
- The user is the owner of the new collection.
- The user is navigated to the collection view.

### Failure Postconditions

- No collection is created.
- System displays an error message.
- The user remains on the login screen.

## Business Rules

### BR-001: Default Collection Name

The auto-provisioned collection must be named "My Bookmarks".

### BR-002: Single Auto-Provision

Auto-provisioning runs only once per user; subsequent logins do not create additional collections.
