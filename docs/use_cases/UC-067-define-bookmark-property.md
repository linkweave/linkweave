# Use Case: Define Bookmark Property

## Overview

**Use Case ID:** UC-067   
**Use Case Name:** Define Bookmark Property   
**Primary Actor:** Collection Owner   
**Goal:** Create a named, typed property definition at the collection level so that bookmarks in that collection can carry structured metadata.   
**Status:** Done   

## Traceability

**Maps to:** FR-069

---

## Preconditions

- The user is authenticated.
- The user has access to the target collection (owner or member).

## Main Success Scenario

1. User navigates to the collection settings or property management view.
2. User selects "Add Property".
3. System displays a form with fields: name, type (text, date, select, multi-select, boolean, number), and optional allowed values (for select/multi-select types).
4. User fills in the property name and selects a type.
5. User optionally defines allowed values (for select/multi-select types).
6. System validates the property name is unique within the collection and not empty.
7. System creates the property definition and makes it available on all bookmarks in the collection.
8. System displays the updated list of property definitions.

## Alternative Flows

### A1: Duplicate Name

**Trigger:** Property name already exists in collection (step 6).
**Flow:**

1. Backend rejects the request with a validation error. System shows error "A property with this name already exists."
2. User must choose a different name.
3. Use case continues at step 4.

### A2: Empty Name

**Trigger:** Property name is blank (step 6).
**Flow:**

1. System shows validation error "Property name is required."
2. Use case continues at step 4.

### A3: No Allowed Values for Select

**Trigger:** Type is select or multi-select but no allowed values provided (step 5).
**Flow:**

1. System shows error "At least one allowed value is required for select types."
2. Use case continues at step 5.

### A4: No Collection Access

**Trigger:** User does not have access to the collection (step 2).
**Flow:**

1. System returns HTTP 403 Forbidden.
2. Use case ends.

## Postconditions

### Success Postconditions

- A new property definition exists for the collection.
- It appears as an editable field on all bookmark create/edit dialogs.

### Failure Postconditions

- No property is created. The collection is unchanged.

## Business Rules

### BR-067: Property Name Uniqueness

Property names must be unique within a collection (case-sensitive). Names are restricted to `\w` characters (letters, digits, underscore) and hyphens by the frontend schema; the backend enforces uniqueness via a composite unique index on `(name, collection_id)`.

### BR-068: Property Types

Supported types: text, date, select, multi-select, boolean, number.

### BR-069: Allowed Values

Select and multi-select types require at least one allowed value. Text, date, boolean, and number types do not support allowed values.

### BR-070: Collection Access Required

Property definitions can be created, edited, or deleted by any user with access to the collection.

### BR-071: Property Count

There is no hard limit on the number of property definitions per collection. The user decides how many properties to define.
