# Use Case: Share Project

## Overview

**Use Case ID:** UC-023   
**Use Case Name:** Share Project   
**Primary Actor:** Project Owner   
**Goal:** Grant another user access to a project so they can collaborate on the shared bookmark collection.   
**Status:** Draft   

## Preconditions

- The actor is authenticated.
- The actor owns the project they want to share.
- The target user exists in the system.

## Main Success Scenario

1. Project Owner opens the project settings.
2. Project Owner selects the option to share the project.
3. System displays the share form.
4. Project Owner enters the username of the user to share with.
5. System validates that the entered username exists.
6. System grants the target user access to the project.
7. System confirms the share with a success message.

## Alternative Flows

### A1: Username Does Not Exist

**Trigger:** The entered username is not found in the system (step 5).
**Flow:**

1. System displays an error message: "User not found."
2. Project Owner corrects the username or cancels.
3. Use case continues at step 4.

### A2: Project Already Shared With This User

**Trigger:** The target user already has access to this project (step 5).
**Flow:**

1. System displays an informational message: "This user already has access."
2. No duplicate access record is created.
3. Use case ends.

### A3: Owner Attempts to Share With Themselves

**Trigger:** The entered username matches the Project Owner's own username (step 5).
**Flow:**

1. System displays an error message: "You cannot share a project with yourself."
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The target user has access to the project.
- The target user can view and manage bookmarks in the shared project (via UC-024).

### Failure Postconditions

- No new access is granted.
- The project's access list remains unchanged.

## Business Rules

### BR-007: Owner-Only Sharing

Only the Project Owner may share or revoke access to their project.

### BR-008: No Duplicate Access

A user may hold at most one access record per project; sharing the same project twice with the same user is a no-op.
