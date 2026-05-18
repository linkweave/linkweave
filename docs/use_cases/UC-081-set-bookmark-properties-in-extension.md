# Use Case: Set Bookmark Properties in Extension

## Overview

**Use Case ID:** UC-081
**Use Case Name:** Set Bookmark Properties in Extension
**Primary Actor:** User
**Goal:** Assign property values to a bookmark when saving it from the browser extension popup so that structured metadata is captured at save time without switching to the web app.
**Status:** Open

## Traceability

**Maps to:** FR-087, FR-069

---

## Preconditions

- The user is authenticated in the extension (session cookie from the web app).
- The user has write access to the selected collection.
- At least one property definition exists for the selected collection (UC-067).

## Main Success Scenario

1. User opens the extension popup (browser action or context menu).
2. Extension pre-fills URL and title from the active tab and loads collection data including property definitions.
3. System displays a "Properties" section in the save form listing all property definitions for the collection, each with an appropriate input widget (text input, number input, date picker, select dropdown, multi-select toggle buttons, boolean switch).
4. User fills in one or more property values alongside the bookmark's URL, title, description, folder, and tags.
5. User submits the bookmark.
6. System creates the bookmark via `POST /bookmarks`.
7. System persists the property values via `PUT /bookmarks/{bookmarkId}/properties`.
8. System displays a success state confirming the bookmark was saved with properties.

## Alternative Flows

### A1: No Property Definitions

**Trigger:** The selected collection has no property definitions (step 3).
**Flow:**

1. System does not display the Properties section.
2. The save form works as before without properties.
3. Use case continues at step 5.

### A2: User Switches Collection

**Trigger:** User selects a different collection from the collection switcher (step 3).
**Flow:**

1. System reloads the collection data (folders, tags, property definitions).
2. The Properties section is updated to reflect the new collection's definitions.
3. Any previously entered property values are cleared.
4. Use case continues at step 3.

### A3: All Property Values Left Empty

**Trigger:** User does not fill in any property values (step 5).
**Flow:**

1. System skips the `PUT /bookmarks/{bookmarkId}/properties` call entirely.
2. Bookmark is created without property values.
3. Use case continues at step 8.

### A4: Bookmark Creation Fails

**Trigger:** The `POST /bookmarks` call fails with an error (step 6).
**Flow:**

1. System displays an error notification.
2. The form remains open with all entered values (including property values) preserved.
3. User can correct the issue and retry.

### A5: Properties Feature Disabled

**Trigger:** The `chainlink.feature.bookmark-properties.enabled` config property is `false` on the backend (step 2).
**Flow:**

1. The collection data contains no property definitions.
2. Use case follows A1 (no Properties section displayed).

## Postconditions

### Success Postconditions

- The bookmark exists with the specified property values persisted.
- Property values are visible when viewing the bookmark in the web app.

### Failure Postconditions

- The bookmark is unchanged. If creation succeeded but property persistence failed, the bookmark exists without property values and an error is displayed.

## Business Rules

### BR-081: Reuse Web App Property Components

The extension must reuse the same generated API client types and, where feasible, the same input components (`BookmarkPropertyInput.vue`) and encoding utilities (`propertyValueMapper.ts`) as the web app to ensure consistent behavior across both surfaces.

### BR-082: Properties Submitted Separately

Property values are submitted as a separate `PUT` request after bookmark creation, consistent with the web app's pattern. The property payload is encoded using the same `encodePropertyValueMap` utility.

### BR-083: Popup Size Constraint

The Properties section must fit within the extension popup's fixed width (400 px). Property input widgets must be compact and scrollable. Multi-select and select types should use a dropdown popover rather than inline pill lists to conserve vertical space.
