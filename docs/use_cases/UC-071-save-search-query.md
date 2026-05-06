# Use Case: Save Search Query

## Overview

**Use Case ID:** UC-071   
**Use Case Name:** Save Search Query   
**Primary Actor:** User   
**Goal:** Save the current search query (including operators) with a name so that I can re-run it later without retyping.   
**Status:** Open   

## Traceability

**Maps to:** FR-075

---

## Preconditions

- The user is authenticated.
- The user has an active search query in the search bar (free-text, operators, or both).
- The search query is not empty.

## Main Success Scenario

1. User clicks the "Save Search" button (appears next to the search bar when a query is active).
2. System displays a dialog with the current query (read-only) and a name input field.
3. User enters a name for the saved search.
4. System validates the name is non-empty and unique within the collection.
5. System saves the search query with the name, scoped to the current collection.
6. System displays a confirmation toast: "Search saved."
7. The saved search appears in the sidebar under a "Saved Searches" section.

## Alternative Flows

### A1: Duplicate Name

**Trigger:** A saved search with this name already exists in the collection (step 4).
**Flow:**

1. System shows error "A saved search with this name already exists."
2. User must choose a different name.
3. Use case continues at step 3.

### A2: Empty Name

**Trigger:** User submits without entering a name (step 4).
**Flow:**

1. System shows validation error "Name is required."
2. Use case continues at step 3.

### A3: No Active Query

**Trigger:** The search bar is empty when user attempts to save (step 1).
**Flow:**

1. The "Save Search" button is disabled.
2. Use case cannot proceed.

## Postconditions

### Success Postconditions

- A named saved search exists for the collection, visible in the sidebar.
- Clicking it re-applies the query.

### Failure Postconditions

- No saved search is created.

## Business Rules

### BR-086: Saved Search Scope

Saved searches are scoped to the collection they were created in. They do not appear in other collections.

### BR-087: Saved Search Contents

The saved search stores the exact query string including all operators, free-text terms, and negations. It does not store sidebar filter state (tag clicks, folder selection).

### BR-088: Saved Search Limit

A collection may have up to 20 saved searches.
