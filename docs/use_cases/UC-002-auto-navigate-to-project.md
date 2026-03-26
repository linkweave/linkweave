# Use Case: Auto-Navigate to Project

## Overview

**Use Case ID:** UC-002   
**Use Case Name:** Auto-Navigate to Project   
**Primary Actor:** User   
**Goal:** Be taken directly to the default project after login without any manual selection.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has exactly one default project (guaranteed by BR-003).

## Main Success Scenario

1. User completes login.
2. System retrieves the user's default project.
3. System navigates the user directly to the default project.

## Alternative Flows

### A1: User Has No Projects

**Trigger:** No projects exist for the user after login.
**Flow:**

1. System triggers UC-001 (Auto-Provision on First Login) to create a default project.
2. Use case continues at step 2.

## Postconditions

### Success Postconditions

- The user is viewing their default project.

### Failure Postconditions

- The user is shown an error message.
- The user remains on the post-login landing screen.

## Business Rules

### BR-003: Exactly One Default Per User

Every user must have exactly one default project at all times. A user with no default is only possible transiently during first login, resolved immediately by UC-001.
