# Use Case: Set Default Collection

## Overview

**Use Case ID:** UC-004   
**Use Case Name:** Set Default Collection   
**Primary Actor:** User   
**Goal:** Change which collection is the default so that login navigates directly to it.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to at least two collections.
- The user currently has a default collection designated.

## Main Success Scenario

1. User opens the collection list.
2. User selects a non-default collection and chooses "Set as Default".
3. System removes the default designation from the current default collection.
4. System marks the selected collection as the new default.
5. System confirms the change with a success message.

## Alternative Flows

*None. A user always has exactly one default collection; clearing without replacement is not permitted.*

## Postconditions

### Success Postconditions

- Exactly one collection is marked as the user's default.
- Subsequent logins navigate the user directly to the new default collection (via UC-002).

### Failure Postconditions

- The default designation remains unchanged.
- System displays an error message.

## Business Rules

### BR-005: Mandatory Default

A user must always have exactly one default collection. The default can be changed but never removed.

### BR-006: Default Scope

The default setting is per-user; changing a default does not affect other users' settings.
