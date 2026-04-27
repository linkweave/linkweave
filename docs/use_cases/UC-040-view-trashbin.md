# Use Case: View Trashbin

## Overview

**Use Case ID:** UC-040
**Use Case Name:** View Trashbin
**Primary Actor:** User
**Goal:** View all soft-deleted bookmarks and folders in the trashbin so the user can decide whether to restore or permanently delete them.
**Status:** Implemented

## Traceability

**Maps to:** FR-046, FR-047

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.

## Main Success Scenario

1. User opens the user menu.
2. User selects "Trashbin" from the menu.
3. System displays the trashbin view showing all soft-deleted bookmarks and folders belonging to the user.
4. For each item, the system shows the item name, type (bookmark or folder), original location, and deletion date.
5. User browses the list of deleted items.

## Alternative Flows

### A1: Empty Trashbin

**Trigger:** No deleted items exist (step 3).
**Flow:**

1. System displays an empty state message: "The trashbin is empty."
2. Use case ends.

### A2: Deleted Folder with Contents

**Trigger:** A deleted folder is shown in the trashbin (step 3).
**Flow:**

1. System shows the folder as a single entry.
2. User can expand the folder to see the bookmarks and subfolders that were contained within it.
3. Use case continues at step 5.

### A3: Item Count Indicator

**Trigger:** User views the user menu before opening the trashbin (step 1).
**Flow:**

1. System displays a badge or count next to the "Trashbin" menu item indicating the number of items in the trashbin.
2. Use case continues at step 2.

## Postconditions

### Success Postconditions

- The user can see all their soft-deleted items with relevant metadata.
- Items remain in the trashbin unchanged.

### Failure Postconditions

- System displays an error message.
- User remains on the current view.

## Business Rules

### BR-058: Trashbin Scope

The trashbin shows only items belonging to collections the user has access to.

### BR-059: Deletion Date Display

Each trashbin entry must display the date and time the item was soft-deleted.

### BR-060: Original Location Tracking

Each trashbin entry must record the original parent folder (or root) so the item can be restored to its previous location.
