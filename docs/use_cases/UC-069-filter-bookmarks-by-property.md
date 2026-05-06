# Use Case: Filter Bookmarks by Property

## Overview

**Use Case ID:** UC-069   
**Use Case Name:** Filter Bookmarks by Property   
**Primary Actor:** User   
**Goal:** Filter the bookmark list by one or more property values so that I can quickly find bookmarks matching specific metadata criteria.   
**Status:** Open   

## Traceability

**Maps to:** FR-070

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.
- At least one property definition exists for the collection and at least one bookmark has a property value set.

## Main Success Scenario

1. User opens the filter panel or search bar in the collection view.
2. System displays available property filters for the collection, showing property names and their possible values.
3. User selects a property and one or more values to filter by.
4. System filters the bookmark list to show only bookmarks that have the selected property value(s).
5. System displays the count of matching bookmarks.
6. System highlights the active property filter in the UI.

## Alternative Flows

### A1: No Bookmarks Match

**Trigger:** No bookmarks have the selected property values (step 4).
**Flow:**

1. System displays empty list with message: "No bookmarks match the selected filters."
2. Use case ends.

### A2: Combine with Other Filters

**Trigger:** User has active tag, folder, or search filters (step 3).
**Flow:**

1. System applies all filters using AND logic: bookmarks must satisfy tag filter AND folder filter AND property filter AND search query.
2. Use case continues at step 5.

### A3: Clear Property Filter

**Trigger:** User clicks clear on the property filter (step 6).
**Flow:**

1. System removes the property filter.
2. System shows all bookmarks matching other active filters.
3. Use case ends.

### A4: No Properties Defined

**Trigger:** No property definitions exist for the collection (step 2).
**Flow:**

1. System does not show property filter options.
2. Standard tag and folder filters remain available.
3. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks matching the property filter (and any other active filters).

### Failure Postconditions

- Bookmark list remains unfiltered by property.

## Business Rules

### BR-076: Property Filter Logic

Multiple values within a single property filter use OR logic (e.g., priority=high OR priority=medium). Multiple property filters combine with AND logic.

### BR-077: Empty Value Filter

Filtering by "no value set" for a property must return all bookmarks where that property has no value assigned.

### BR-078: Filter Availability

A property filter only appears when at least one bookmark in the collection has a value set for that property.
