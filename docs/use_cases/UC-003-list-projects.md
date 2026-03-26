# Use Case: List Projects

## Overview

**Use Case ID:** UC-003   
**Use Case Name:** List Projects   
**Primary Actor:** User   
**Goal:** View all projects the user has access to and navigate to a non-default project.   
**Status:** Draft   

## Preconditions

- The user is authenticated.
- The user has access to at least one project.

## Main Success Scenario

1. User navigates to the project list.
2. System displays all projects the user has access to, indicating which one is the default.
3. User selects a project from the list.
4. System navigates the user to the selected project.

## Alternative Flows

### A1: User Navigates Away Without Selecting

**Trigger:** User closes the project list without selecting a project (step 3).
**Flow:**

1. System remains on the current view without navigating.
2. Use case ends.

## Postconditions

### Success Postconditions

- The user is viewing the selected project.

### Failure Postconditions

- The user remains on the project list screen.
- No project is opened.

## Business Rules

### BR-004: Access Visibility

The list must only show projects the user owns or has been granted access to; other users' projects must not be visible.

### BR-007: Default Indicator

The project currently marked as the user's default must be visually distinguished in the list.
