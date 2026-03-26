# Use Case: Access Shared Project

## Overview

**Use Case ID:** UC-024   
**Use Case Name:** Access Shared Project   
**Primary Actor:** User   
**Goal:** Open and work with a project that another user has shared with them.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- Another user has shared a project with this user (via UC-023).

## Main Success Scenario

1. User navigates to the project list (UC-003).
2. System displays all projects the user has access to, including shared projects.
3. User selects a shared project from the list.
4. System navigates the user to the shared project.
5. User can view and manage bookmarks within the shared project.

## Alternative Flows

### A1: Access Has Been Revoked

**Trigger:** The user selects a shared project whose access has since been revoked (step 3).
**Flow:**

1. System denies access and displays an error message: "You no longer have access to this project."
2. System removes the project from the user's project list.
3. Use case ends.

### A2: Shared Project No Longer Exists

**Trigger:** The project was deleted by the owner after being shared (step 3).
**Flow:**

1. System displays an error message: "This project is no longer available."
2. System removes the stale entry from the user's project list.
3. Use case ends.

## Postconditions

### Success Postconditions

- The user is viewing the shared project and can manage its bookmarks.

### Failure Postconditions

- The user is not granted access to the project.
- System displays an appropriate error message.

## Business Rules

### BR-009: Read and Write Access

A user granted access to a shared project may view and manage all bookmarks, folders, and tags within that project.

### BR-010: No Ownership Transfer

Being granted access to a shared project does not make the user an owner; only the original Project Owner can share or delete the project.

### BR-011: Access Boundary

A user may only access resources within projects they own or have been granted access to; access to other projects returns HTTP 403.
