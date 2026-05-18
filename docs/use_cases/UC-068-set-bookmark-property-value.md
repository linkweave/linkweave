# Use Case: Set Bookmark Property Value

## Overview

**Use Case ID:** UC-068   
**Use Case Name:** Set Bookmark Property Value   
**Primary Actor:** User   
**Goal:** Assign or change a property value on a bookmark so that the bookmark carries structured metadata.   
**Status:** Done

## Traceability

**Maps to:** FR-069

---

## Preconditions

- The user is authenticated.
- The user has write access to the collection.
- At least one property definition exists for the collection (UC-067).

## Main Success Scenario

1. User opens the bookmark create or edit dialog.
2. System displays a "Properties" section listing all property definitions for the collection, each with an appropriate input widget (text input, date picker, dropdown, toggle, number input).
3. User fills in or modifies one or more property values.
4. System validates property values against their type and allowed values (for select/multi-select).
5. User submits the bookmark.
6. System persists the property values linked to the bookmark.
7. System displays the bookmark with its property values in the bookmark list or detail view.

## Alternative Flows

### A1: Invalid Value Type

**Trigger:** User enters a non-numeric value for a number property or invalid date for a date property (step 4).
**Flow:**

1. The input widget prevents or rejects the invalid value (e.g., number fields reject non-numeric input; date fields enforce ISO format).
2. Use case continues at step 4.

### A2: Value Not in Allowed List

**Trigger:** User attempts to enter a value not in the allowed values for a select property (step 4).
**Flow:**

1. The dropdown/select widget only presents valid options from the definition's allowed values.
2. Use case continues at step 4.

### A3: No Property Definitions

**Trigger:** No property definitions exist for the collection (step 2).
**Flow:**

1. System does not display the Properties section.
2. The bookmark form works as before without properties.
3. Use case continues at step 5.

## Postconditions

### Success Postconditions

- The bookmark has the specified property values persisted.
- Property values are visible on the bookmark card/detail.

### Failure Postconditions

- The bookmark is unchanged. Validation errors are displayed.

## Business Rules

### BR-072: Optional Properties

All properties are optional. A bookmark may have zero or more property values set.

### BR-073: Type Validation

Property values must match their definition type. Number properties accept numeric values only. Date properties accept ISO 8601 dates. Boolean properties accept true/false.

### BR-074: Single Value

Text, date, select, boolean, and number properties hold a single value. Multi-select properties hold a set of values from the allowed list.
