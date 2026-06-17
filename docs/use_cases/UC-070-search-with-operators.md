# Use Case: Search Bookmarks with Operators

## Overview

**Use Case ID:** UC-070   
**Use Case Name:** Search Bookmarks with Operators   
**Primary Actor:** User   
**Goal:** Perform precise bookmark searches using a structured operator syntax in the search bar so that I can filter by tag, property, date, and folder without navigating through separate filter dialogs.   
**Status:** Done

## Traceability

**Maps to:** FR-071, FR-072, FR-073, FR-074

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

## Main Success Scenario

1. User clicks on the search bar (or activates it via keyboard shortcut: ⌘K / Ctrl+K or /).
2. User types a search query containing one or more operators (e.g., `#recipe created:>2026-01 folder:cooking`).
3. System parses the query into individual operators and free-text terms.
4. System filters the bookmark list in real time to show only bookmarks matching all operators and terms (AND logic).
5. System highlights matched operators in the search bar with visual distinction (e.g., colored tokens).
6. System displays the count of matching bookmarks.

### Supported Operators

- `#tagname` — filter by tag (exact match). Multi-word tags: `#"tag name"`.
- `-#tagname` — exclude tag.
- `folder:name` — filter by folder name (substring match).
- `property:value` — filter by property value (exact for select/boolean, substring for text).
- `created:YYYY-MM-DD` — filter by creation date. Supports `>` and `<` prefixes for ranges.
- `created:>today-Nd` — relative date filter (e.g., `created:>today-30d` for last 30 days).
- `note:keyword` — search within bookmark notes.
- `-term` — exclude free-text term from results.
- `match:OR` — switch from AND to OR logic for free-text terms.

## Alternative Flows

### A1: Autocomplete Suggestion

**Trigger:** User types a trigger character (`#`, `folder:`, `propertyname:`, `note:`) followed by one or more characters (step 2).
**Flow:**

1. System displays a dropdown with matching tags, folders, or property values from the current collection.
2. User selects an item to insert it as a complete operator.
3. Use case continues at step 4.

### A2: Invalid Operator Syntax

**Trigger:** Query contains malformed operator syntax (step 3).
**Flow:**

1. System underlines the invalid portion in red and shows a tooltip with syntax help.
2. The valid portions of the query still filter results.
3. Use case continues at step 4.

### A3: No Results

**Trigger:** No bookmarks match the combined operators (step 4).
**Flow:**

1. System displays empty list with message: "No bookmarks match your search."
2. Use case ends.

### A4: Combine with Sidebar Filters

**Trigger:** User has active tag or folder filters from the sidebar while typing operators (step 4).
**Flow:**

1. Sidebar filters and search operators combine with AND logic.
2. Use case continues at step 5.

### A5: Negation Operator

**Trigger:** User prefixes an operator with `-` (step 2).
**Flow:**

1. System excludes bookmarks matching that operator from results.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks matching all operators (and any active sidebar filters).

### Failure Postconditions

- Bookmark list remains unfiltered.

## Business Rules

### BR-079: Operator Parsing

Operators are space-delimited. Quoted strings (`"..."`) preserve spaces within values. The parser must handle nested quotes gracefully.

### BR-080: Case-Insensitive Matching

Tag names, folder names, property names, and free-text terms are matched case-insensitively.

### BR-081: AND Logic Default

All operators and free-text terms are combined using AND logic unless `match:OR` is specified. `match:OR` only applies to free-text terms, not to structured operators.

### BR-082: Client-Side Evaluation

Search operators are evaluated entirely client-side against data in the Pinia store, consistent with C-015. No server round-trip is required.

### BR-083: Autocomplete Triggers

Autocomplete dropdown appears for: `#` (tags), `folder:` (folders), any known property name followed by `:` (property values), `note:` (note content), `created:` (date suggestions). The dropdown shows up to 10 matching items.

### BR-084: Date Range Operators

Date operators support `YYYY-MM-DD`, `YYYY-MM`, `YYYY` formats. The `>` prefix means "after", `<` means "before". Without a prefix, the operator matches the exact date period (day, month, or year).

### BR-085: Relative Dates

Relative date expressions use the format `today-Nd` where N is a positive integer and `d` is the unit (d=days, w=weeks, m=months). Example: `created:>today-30d` means "created in the last 30 days".
