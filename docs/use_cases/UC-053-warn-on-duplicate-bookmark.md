# Use Case: Warn on Duplicate Bookmark

## Overview

**Use Case ID:** UC-053
**Use Case Name:** Warn on Duplicate Bookmark
**Primary Actor:** User
**Goal:** Alert the user when they are about to create a bookmark whose URL already exists in the same collection, preventing unintentional duplicates.
**Status:** Implemented

## Traceability

**Maps to:** FR-053

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.

## Main Success Scenario

1. User enters a URL in the bookmark creation form.
2. System checks whether a bookmark with the same URL already exists in the current collection.
3. System displays a warning indicating that a bookmark with this URL already exists, showing the existing bookmark's title and folder location.
4. User acknowledges the warning and decides whether to proceed or cancel.
5. If user proceeds, the bookmark is created (duplicate allowed).
6. If user cancels, the form remains open for editing.

## Alternative Flows

### A1: No Duplicate Found

**Trigger:** The URL does not exist in the current collection (step 2).
**Flow:**

1. No warning is displayed.
2. Use case continues with normal bookmark creation (UC-005).

### A2: Multiple Duplicates Found

**Trigger:** Multiple bookmarks with the same URL exist in the collection (step 2).
**Flow:**

1. System displays a warning listing all matching bookmarks with their titles and folder locations.
2. Use case continues at step 4.

### A3: Duplicate Warning on Edit

**Trigger:** User edits a bookmark's URL to match an existing bookmark's URL.
**Flow:**

1. System displays the duplicate warning.
2. User decides whether to proceed or revert.
3. If user proceeds, the edit is saved.

### A4: Duplicate Warning on Import

**Trigger:** User imports bookmarks (UC-031) and one or more URLs already exist in the collection.
**Flow:**

1. System shows a summary of duplicates found during import.
2. User chooses to skip or import duplicates.
3. Import proceeds according to user's choice.

## Postconditions

### Success Postconditions

- User was informed about existing bookmarks with the same URL.
- The user's decision (proceed or cancel) is respected.

### Failure Postconditions

- Bookmark creation or edit proceeds without warning.
- No data is lost.

## Business Rules

### BR-080: URL Comparison

URLs are compared using normalized forms: scheme and host are lowercased, trailing slashes are stripped, and query parameter order is ignored. Fragment identifiers (`#...`) are excluded from comparison.

### BR-081: Warning Scope

Duplicate checks are scoped to the current collection. Bookmarks in other collections are not considered duplicates.

### BR-082: Warning is Non-Blocking

The duplicate warning does not prevent the user from creating or editing the bookmark. It is advisory only.
