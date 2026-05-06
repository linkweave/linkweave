# Use Case: Sort Bookmarks

## Overview

**Use Case ID:** UC-076   
**Use Case Name:** Sort Bookmarks   
**Primary Actor:** User   
**Goal:** Choose how bookmarks are sorted in the bookmark list and have the preference remembered per collection.   
**Status:** Open   

## Traceability

**Maps to:** FR-082

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

## Main Success Scenario

1. User clicks the sort dropdown in the bookmark list header.
2. System displays sort options: Title (A→Z), Title (Z→A), Newest first, Oldest first, Most clicked, Recently clicked.
3. User selects a sort option.
4. System re-sorts the bookmark list immediately.
5. System persists the sort preference for this collection.
6. Next time the user opens this collection, the bookmark list uses the saved sort order.

## Alternative Flows

### A1: Default Sort

**Trigger:** User has never set a sort preference for the collection (step 6).
**Flow:**

1. System uses "Newest first" as the default.

### A2: Sort with Active Filters

**Trigger:** User has active tag, folder, or search filters (step 4).
**Flow:**

1. System sorts the filtered result set, not the entire collection.

### A3: Change Default Sort

**Trigger:** User opens Settings dialog and changes the default sort preference for new collections (step 3).
**Flow:**

1. System saves the global default.
2. Existing collections are not affected.

## Postconditions

### Success Postconditions

- The bookmark list is sorted by the selected criterion.
- The preference is persisted.

### Failure Postconditions

- The bookmark list remains sorted by the previous criterion.

## Business Rules

### BR-106: Sort Options

Available sort options: Title ascending, Title descending, Creation date descending (newest first), Creation date ascending (oldest first), Click count descending (most clicked), Last clicked date descending (recently clicked).

### BR-107: Per-Collection Persistence

Sort preference is stored per collection. Changing sort in one collection does not affect other collections.

### BR-108: Default Sort

The default sort for collections where no preference is set is "Creation date descending" (newest first).

### BR-109: Stable Sort

When two bookmarks have the same value for the sort criterion, they are secondarily sorted by creation date descending as a tiebreaker.
