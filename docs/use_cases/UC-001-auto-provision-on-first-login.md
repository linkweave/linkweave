# Use Case: Auto-Provision on First Login

## Overview

**Use Case ID:** UC-001   
**Use Case Name:** Auto-Provision on First Login   
**Primary Actor:** User   
**Goal:** Automatically receive a default project on first login so bookmarks can be saved immediately without manual setup.   
**Status:** Draft   

## Preconditions

- The user has valid credentials in the system.
- The user has never logged in before (no existing project for this user).

## Main Success Scenario

1. User submits valid login credentials.
2. System authenticates the user.
3. System detects that no project exists for this user.
4. System creates a default project named "My Bookmarks" owned by the user.
5. System navigates the user directly to the new project.

## Alternative Flows

### A1: User Already Has a Project

**Trigger:** System detects an existing project for the user (step 3).
**Flow:**

1. System skips auto-provisioning.
2. Use case continues with UC-002 (Auto-Navigate to Project).

## Postconditions

### Success Postconditions

- A default project exists for the user.
- The user is the owner of the new project.
- The user is navigated to the project view.

### Failure Postconditions

- No project is created.
- System displays an error message.
- The user remains on the login screen.

## Business Rules

### BR-001: Default Project Name

The auto-provisioned project must be named "My Bookmarks".

### BR-002: Single Auto-Provision

Auto-provisioning runs only once per user; subsequent logins do not create additional projects.
