# Use Case: Import Browser Bookmarks

## Overview

**Use Case ID:** UC-031   
**Use Case Name:** Import Browser Bookmarks   
**Primary Actor:** User   
**Goal:** Migrate existing bookmarks from another browser into a Chainlink collection.   
**Status:** Draft   

## Traceability

**Maps to:** FR-031
**Implementation Plan:** [bookmark-import.md](../bookmark-import.md)

---

## Preconditions

- The user is authenticated.
- The user has write access to the target collection (owner or shared with write permission).
- The user has exported bookmarks from their browser as an HTML file (Netscape Bookmark File Format).

## Main Success Scenario

1. User navigates to the collection where they want to import bookmarks.
2. User selects "Import Bookmarks" from the collection menu.
3. System displays a file upload dialog or drop zone.
4. User selects the exported bookmarks HTML file.
5. System validates the file (non-null, has .html or .htm extension, size ≤ 5 MB).
6. System parses the HTML using the Netscape Bookmark File Format parser.
7. System recursively creates `Folder` entities at the collection root, preserving the folder hierarchy from the import file.
8. System creates `Bookmark` entities, each linked to the appropriate folder (or collection root if no folder).
9. System generates a unique import tag name: `imported=YYYY-MM-DD_N` (where N increments per import on that date).
10. System creates the import `Tag` entity in the target collection.
11. System applies the import tag to all imported bookmarks (across all folders).
12. System returns a success response with import summary: folders created, bookmarks created, import tag name.

## Alternative Flows

### A1: Invalid File

**Trigger:** User uploads a file that is not an HTML file, has wrong extension, or is empty (step 4).
**Flow:**

1. System returns HTTP 400 Bad Request.
2. System displays an error message: "Please upload a valid bookmarks HTML file."
3. Use case ends with no changes.

### A2: File Too Large

**Trigger:** User uploads a file larger than 5 MB (step 5).
**Flow:**

1. System returns HTTP 413 Payload Too Large.
2. System displays an error message: "File size exceeds 5 MB limit."
3. Use case ends with no changes.

### A3: No Collection Access

**Trigger:** User does not have write access to the target collection (step 2).
**Flow:**

1. System returns HTTP 403 Forbidden.
2. System displays an error message: "You do not have permission to modify this collection."
3. Use case ends with no changes.

### A4: Malformed HTML

**Trigger:** The uploaded file cannot be parsed as valid Netscape Bookmark HTML (step 6).
**Flow:**

1. System returns HTTP 400 Bad Request.
2. System displays an error message: "Invalid bookmark file format. Please export from your browser and try again."
3. Use case ends with no changes.

### A5: Empty Import

**Trigger:** The imported HTML file contains no bookmarks or folders (step 6).
**Flow:**

1. System creates the import tag (with no bookmarks attached).
2. System returns success response with foldersCreated=0, bookmarksCreated=0, importTag="imported=YYYY-MM-DD_N".
3. Use case ends.

### A6: Transaction Rollback

**Trigger:** An error occurs during entity creation (e.g., database constraint violation) (steps 7-11).
**Flow:**

1. System rolls back the entire transaction (no folders, bookmarks, or tags are persisted).
2. System returns HTTP 500 Internal Server Error.
3. System displays an error message: "An error occurred while importing bookmarks. Please try again."
4. Use case ends with no changes.

## Postconditions

### Success Postconditions

- All folders from the import file exist in the collection root, preserving hierarchy.
- All bookmarks from the import file exist in the collection, linked to their respective folders.
- An import tag exists in the collection with name `imported=YYYY-MM-DD_N`.
- Every imported bookmark has the import tag applied.
- The import summary is displayed to the user.

### Failure Postconditions

- No folders, bookmarks, or tags are created (atomic operation).
- The collection remains unchanged.
- An appropriate error message is displayed.

## Business Rules

### BR-060: Supported Import Format

Only the Netscape Bookmark File Format (HTML) is supported. This is the universal export format used by all major browsers (Chrome, Firefox, Safari, Edge, Brave, Opera). Internal browser formats (e.g., Chrome JSON, Firefox SQLite) are not supported.

### BR-061: Duplicate Bookmarks

Duplicate URLs within the same collection are allowed. The import process does not check for existing URLs or perform de-duplication.

### BR-062: Transactional Import

Import is performed as a single database transaction. All entities (folders, bookmarks, tags) are created atomically. If any part fails, the entire import is rolled back.

### BR-063: Import Tag Naming

Import tags follow the naming convention `imported=YYYY-MM-DD_N` where:
- `YYYY-MM-DD` is the current date
- `N` is an incrementing counter starting at 1, incremented per import run on that date

This allows users to distinguish between multiple import batches on the same day.

### BR-064: Import to Collection Root

Imported folder hierarchy is placed at the collection root level. Folders from the import file become top-level folders (parent = null) in the collection. Nesting within the import file is preserved by setting the `parent` field appropriately.

### BR-065: Maximum File Size

The maximum allowed upload size for bookmark import is 5 MB. This limit covers bookmark collections with thousands of entries while preventing excessive server load.

### BR-066: Tag Color Assignment

The auto-generated import tag is assigned a default color from the application's color palette.
