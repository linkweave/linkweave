# Use Case: Set Default Project

## Overview

**Use Case ID:** UC-004   
**Use Case Name:** Set Default Project   
**Primary Actor:** User   
**Goal:** Change which project is the default so that login navigates directly to it.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to at least two projects.
- The user currently has a default project designated.

## Main Success Scenario

1. User opens the project list.
2. User selects a non-default project and chooses "Set as Default".
3. System removes the default designation from the current default project.
4. System marks the selected project as the new default.
5. System confirms the change with a success message.

## Alternative Flows

*None. A user always has exactly one default project; clearing without replacement is not permitted.*

## Postconditions

### Success Postconditions

- Exactly one project is marked as the user's default.
- Subsequent logins navigate the user directly to the new default project (via UC-002).

### Failure Postconditions

- The default designation remains unchanged.
- System displays an error message.

## Business Rules

### BR-005: Mandatory Default

A user must always have exactly one default project. The default can be changed but never removed.

### BR-006: Default Scope

The default setting is per-user; changing a default does not affect other users' settings.
