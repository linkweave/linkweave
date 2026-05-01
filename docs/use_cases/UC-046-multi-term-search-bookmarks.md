# Use Case: Multi-Term Search Bookmarks

## Overview

**Use Case ID:** UC-046
**Use Case Name:** Multi-Term Search Bookmarks
**Primary Actor:** User
**Goal:** Find specific bookmarks by typing multiple search terms separated by whitespace, where each term is matched independently and results are combined with AND logic. Terms wrapped in single quotes are treated as a single literal phrase.
**Status:** Implemented

## Traceability

**Maps to:** FR-032 (enhancement)
**Supersedes:** UC-032 (single-term search — the query parsing logic from UC-032 is replaced; all other flows remain valid)

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

## Main Success Scenario

1. User clicks on the search bar (or activates it via keyboard shortcut: ⌘K / Ctrl+K or `/`).
2. User types a search query containing multiple whitespace-separated terms (e.g., `prod foobar`).
3. System parses the query into individual search terms, treating whitespace as a delimiter unless the whitespace is between single-quote characters.
4. System filters the bookmark list in real time to show only bookmarks where **every** parsed term matches the bookmark's title, URL, description, or tag name (case-insensitive substring match, AND logic across terms).
5. System highlights the active search in the UI.
6. System displays the count of matching bookmarks.

## Alternative Flows

### A1: Quoted Phrase Search

**Trigger:** User wraps multiple words in single quotes within the query (step 2).
**Flow:**

1. User types a query containing a quoted phrase (e.g., `'my project' deploy`).
2. System parses the query into two terms: the literal phrase `my project` and the single term `deploy`.
3. System filters to show bookmarks where the bookmark contains the substring `my project` **and** the substring `deploy` (each matched against title, URL, description, or tag name).
4. Use case continues at step 5.

### A2: Single Term (Backward Compatible)

**Trigger:** User types a single word with no quotes (step 2).
**Flow:**

1. System parses the query into a single search term.
2. System filters identically to the existing single-term search behavior.
3. Use case continues at step 5.

### A3: No Results Found

**Trigger:** No bookmarks match all parsed search terms (step 4).
**Flow:**

1. System displays an empty bookmark list with a message: "No bookmarks found."
2. Use case ends.

### A4: Clear Search

**Trigger:** User wants to remove the search filter (step 4).
**Flow:**

1. User clicks the clear button (X) in the search bar or deletes the query text.
2. System displays all bookmarks in the current context (collection, folder, or tag filter).
3. Use case ends.

### A5: Combine with Tag Filter

**Trigger:** User wants to search within bookmarks that have specific tags (step 4).
**Flow:**

1. User selects one or more tags from the tag filter.
2. User types a multi-term search query.
3. System filters to show bookmarks that match **all** search terms AND have all selected tags.
4. Use case continues at step 5.

### A6: Combine with Folder Filter

**Trigger:** User wants to search within a specific folder (step 4).
**Flow:**

1. User selects a folder from the folder tree.
2. User types a multi-term search query.
3. System filters to show bookmarks that match **all** search terms AND are in the selected folder.
4. Use case continues at step 5.

### A7: Combine with Tag and Folder Filters

**Trigger:** User wants to search within a folder and with specific tags (step 4).
**Flow:**

1. User selects a folder from the folder tree.
2. User selects one or more tags from the tag filter.
3. User types a multi-term search query.
4. System filters to show bookmarks that match **all** search terms AND are in the selected folder AND have all selected tags.
5. Use case continues at step 5.

### A8: Only Quoted Terms

**Trigger:** User enters a query consisting entirely of quoted phrases (e.g., `'my project'`).
**Flow:**

1. System parses the query into the single literal phrase `my project`.
2. System filters to show bookmarks where any searchable field contains the substring `my project`.
3. Use case continues at step 5.

### A9: Unclosed Quote

**Trigger:** User enters a query with an opening single quote but no closing quote (e.g., `'my project`).
**Flow:**

1. System treats the entire string from the opening quote to the end of the input as a single term (i.e., `my project`).
2. System filters using that single term.
3. Use case continues at step 5.

### A10: Empty Quoted Term

**Trigger:** User enters adjacent single quotes with no content between them (e.g., `prod '' foobar`).
**Flow:**

1. System ignores the empty quoted segment.
2. System parses the query into two terms: `prod` and `foobar`.
3. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks matching **all** parsed search terms (and any active tag/folder filters).
- The active search query is visible in the search bar exactly as the user typed it.

### Failure Postconditions

- Bookmark list remains unfiltered.
- System displays an error message.

## Business Rules

### BR-054: Term Delimiter

Whitespace separates individual search terms unless it occurs between single-quote characters. Single quotes (`'`) are the only quoting mechanism — double quotes (`"`) have no special meaning and are treated as literal characters.

### BR-055: AND Logic Across Terms

All parsed terms are combined with AND logic. A bookmark must match **every** term to be included in the results. Each individual term is matched using OR logic across searchable fields (title, URL, description, or tag name).

### BR-056: Case-Insensitive Search

Each parsed term is matched case-insensitively against bookmark titles, URLs, descriptions, and tag names.

### BR-057: Substring Matching

Each term uses substring matching (partial match), not exact or prefix-only matching. A quoted phrase is matched as a continuous substring (e.g., `'my project'` matches `"this is my project page"` but not `"my awesome project"` unless both `my` and `project` appear as a continuous substring).

### BR-058: Combined Filter Logic

When a search query, selected tags, and/or a selected folder are all active, they are combined using AND logic. A bookmark must satisfy all active conditions to be displayed.

### BR-059: Search Scope

Search only applies to the current collection.

### BR-060: Minimum Query Length

Search filtering is only applied when the parsed query yields at least one term of 2 or more characters. Queries that produce no valid terms (e.g., only single characters or empty) do not filter the bookmark list.

### BR-061: Real-Time Filtering

Results update in real time as the user types, without requiring a submit action.

### BR-062: Original Query Preservation

The search bar always displays the raw query string as typed by the user. Parsing is performed internally for filtering but does not alter the visible input.

---

## Examples

| Raw Query Input | Parsed Terms | Matching Rule |
|----------------|-------------|---------------|
| `prod foobar` | `["prod", "foobar"]` | Bookmark must contain both `"prod"` AND `"foobar"` (each in any field) |
| `'my project' deploy` | `["my project", "deploy"]` | Bookmark must contain the phrase `"my project"` AND `"deploy"` |
| `prod` | `["prod"]` | Single-term search (unchanged behavior) |
| `'hello world'` | `["hello world"]` | Bookmark must contain the phrase `"hello world"` |
| `'unclosed quote` | `["unclosed quote"]` | Unclosed quote treated as a single term to end of input |
| `prod '' foobar` | `["prod", "foobar"]` | Empty quoted segment ignored |
| `a bc` | `["bc"]` | Term `"a"` is under minimum length, only `"bc"` is used |
