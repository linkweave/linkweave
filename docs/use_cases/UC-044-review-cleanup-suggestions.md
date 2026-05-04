# Use Case: Review Cleanup Suggestions

## Overview

**Use Case ID:** UC-044
**Use Case Name:** Review Cleanup Suggestions
**Primary Actor:** User
**Goal:** View bookmarks that have not been used for a configurable period and move selected ones to the trashbin to declutter the collection.
**Status:** Implemented

## Traceability

**Maps to:** FR-050

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection containing bookmarks.
- Bookmark usage tracking data exists (clickCount, lastClickedAt).

## Main Success Scenario

1. User opens the user menu.
2. User selects "Cleanup Suggestions" from the menu.
3. System displays a list of bookmarks that have not been clicked in over the configured threshold (default 6 months), ordered by last clicked date ascending (oldest first).
4. For each bookmark, the system shows: title, URL, folder location, last clicked date (or "Never clicked"), and click count.
5. User selects one or more bookmarks from the list.
6. User chooses "Move to Trash".
7. System soft-deletes the selected bookmarks (same mechanism as the trashbin).
8. System removes the moved bookmarks from the suggestions list and displays a confirmation.

## Alternative Flows

### A1: No Stale Bookmarks

**Trigger:** No bookmarks match the inactivity criteria (step 3).
**Flow:**

1. System displays an empty state message: "Your collection is tidy! No stale bookmarks found."
2. Use case ends.

### A2: Never-Clicked Bookmarks

**Trigger:** A bookmark has `lastClickedAt = null` and `clickCount = 0` and is older than the threshold (step 3).
**Flow:**

1. System labels the bookmark as "Never clicked" instead of showing a date.
2. Never-clicked bookmarks appear at the top of the suggestions list.
3. Use case continues at step 4.

### A3: Adjust Inactivity Threshold

**Trigger:** User changes the inactivity period (step 3).
**Flow:**

1. User selects a different threshold from the available options (e.g., 3 months, 6 months, 12 months).
2. System refreshes the suggestions list based on the new threshold.
3. Use case continues at step 4.

### A4: Select All Suggestions

**Trigger:** User wants to move all suggested bookmarks to trash at once (step 5).
**Flow:**

1. User clicks "Select All".
2. System selects all bookmarks in the suggestions list.
3. User chooses "Move to Trash".
4. System soft-deletes all selected bookmarks.
5. System displays the empty state message.
6. Use case ends.

### A5: Dismiss Suggestion

**Trigger:** User wants to keep a suggested bookmark and not see it in cleanup suggestions for a while (step 5).
**Flow:**

1. User clicks "Dismiss" on a suggested bookmark.
2. System records the dismissal timestamp on the bookmark.
3. System removes the bookmark from the current suggestions list.
4. The bookmark will not appear in cleanup suggestions again until the dismissal period has elapsed.
5. Use case continues at step 4.

## Postconditions

### Success Postconditions

- Selected bookmarks are soft-deleted and moved to the trashbin (viewable via UC-040).
- Dismissed bookmarks have a dismissal timestamp recorded and will not be suggested again until the dismissal period expires.
- The suggestions list reflects the updated state (moved or dismissed items removed).

### Failure Postconditions

- Bookmarks remain unchanged.
- System displays an error message.

## Business Rules

### BR-070: Default Inactivity Threshold

The default inactivity threshold is 6 months. A bookmark is considered stale if its `lastClickedAt` is older than the threshold, or if it has never been clicked (`lastClickedAt IS NULL`) and was created more than the threshold ago.

### BR-071: Collection Scope

Cleanup suggestions are scoped to collections the user has access to. Bookmarks from collections the user cannot access are never shown.

### BR-072: Never-Clicked Bookmarks

Bookmarks with `clickCount = 0` and `lastClickedAt = null` that were created more than the threshold ago are included in suggestions and labeled "Never clicked".

### BR-073: Dismissal Period

A dismissed suggestion will not reappear in cleanup suggestions for the same duration as the inactivity threshold (default 6 months). After that period elapses, if the bookmark is still inactive, it may be suggested again.

### BR-074: Suggestion Eligibility

A bookmark is eligible for cleanup suggestions when both conditions are met:
1. The bookmark is inactive: `lastClickedAt < (now - threshold)` or (`lastClickedAt IS NULL` and `timestampErstellt < (now - threshold)`).
2. The bookmark is not in a dismissed state: `suggestionDismissedAt IS NULL` or `suggestionDismissedAt < (now - threshold)`.
