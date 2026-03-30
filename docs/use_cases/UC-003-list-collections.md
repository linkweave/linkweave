# Use Case: List Collections

## Overview

**Use Case ID:** UC-003   
**Use Case Name:** List Collections   
**Primary Actor:** User   
**Goal:** View all collections the user has access to and navigate to a non-default collection.   
**Status:** Implemented   

## Traceability

**Maps to:** FR-003

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.

## Main Success Scenario

1. User navigates to the collection list.
2. System displays all collections the user has access to, indicating which one is the default.
3. User selects a collection from the list.
4. System navigates the user to the selected collection.

## Alternative Flows

### A1: User Navigates Away Without Selecting

**Trigger:** User closes the collection list without selecting a collection (step 3).
**Flow:**

1. System remains on the current view without navigating.
2. Use case ends.

## Postconditions

### Success Postconditions

- The user is viewing the selected collection.

### Failure Postconditions

- The user remains on the collection list screen.
- No collection is opened.

## Business Rules

### BR-004: Access Visibility

The list must only show collections the user owns or has been granted access to; other users' collections must not be visible.

### BR-048: Default Indicator

The collection currently marked as the user's default must be visually distinguished in the list.
