# Use Case: Restore from Trashbin

## Overview

**Use Case ID:** UC-041
**Use Case Name:** Restore Deleted Item
**Primary Actor:** User
**Goal:** Restore a soft-deleted bookmark or folder from the trashbin back to its original location in the collection.
**Status:** Draft

## Traceability

**Maps to:** FR-048

---

## Preconditions

- The user is authenticated.
- The trashbin contains at least one soft-deleted bookmark or folder.
- The user is viewing the trashbin (UC-040).

## Main Success Scenario

1. User selects a deleted bookmark or folder from the trashbin.
2. User chooses "Restore" from the item's context menu or action button.
3. System restores the item to its original location (parent folder or root of the collection).
4. System removes the item from the trashbin view.
5. System displays a confirmation that the item has been restored.

## Alternative Flows

### A1: Restore Folder with Contents

**Trigger:** The user restores a folder that contained bookmarks and subfolders (step 1).
**Flow:**

1. System restores the folder to its original location.
2. System restores all bookmarks and subfolders that were contained in the folder.
3. System removes the folder and all its contents from the trashbin view.
4. System displays a confirmation that the folder and its contents have been restored.
5. Use case ends.

### A2: Original Parent Folder Deleted

**Trigger:** The original parent folder of the item has also been deleted and is in the trashbin (step 3).
**Flow:**

1. System restores the item to the root of the collection.
2. System displays a notice that the original parent folder no longer exists and the item was restored to the root.
3. Use case continues at step 4.

### A3: Original Parent Folder Permanently Deleted

**Trigger:** The original parent folder was permanently deleted from the trashbin (step 3).
**Flow:**

1. System restores the item to the root of the collection.
2. System displays a notice that the original parent folder no longer exists and the item was restored to the root.
3. Use case continues at step 4.

### A4: Bulk Restore

**Trigger:** User selects multiple items to restore (step 1).
**Flow:**

1. User selects multiple items using checkboxes or multi-select.
2. User chooses "Restore Selected" from the action menu.
3. System restores all selected items to their original locations.
4. System removes all restored items from the trashbin view.
5. System displays a confirmation showing the count of restored items.
6. Use case ends.

## Postconditions

### Success Postconditions

- The restored item appears in its original location (or root if original parent is unavailable).
- The item is removed from the trashbin.
- The item is no longer marked as soft-deleted.

### Failure Postconditions

- The item remains in the trashbin.
- System displays an error message.

## Business Rules

### BR-061: Restore to Original Location

Items must be restored to the folder they were in before deletion. If that folder no longer exists (permanently deleted), the item is restored to the collection root.

### BR-062: Cascading Restore for Folders

Restoring a folder also restores all bookmarks and subfolders that were inside it at the time of deletion.

### BR-063: Collection Access Required

The user must have access to the collection the item belongs to in order to restore it.
