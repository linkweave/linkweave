# Use Case: Add Bookmark Note

## Overview

**Use Case ID:** UC-075   
**Use Case Name:** Add Bookmark Note   
**Primary Actor:** User   
**Goal:** Add a personal note to a bookmark so that I can remember why I saved it or add context beyond what the page title and description provide.   
**Status:** Open   

## Traceability

**Maps to:** FR-081

---

## Preconditions

- The user is authenticated.
- The user has write access to the collection.
- A bookmark exists in the collection.

## Main Success Scenario

1. User opens a bookmark's detail view or edit dialog.
2. System displays a "Notes" text area.
3. User types a personal note (plain text, up to 10,000 characters).
4. User saves the bookmark.
5. System persists the note on the bookmark.
6. System displays a preview of the note (truncated to 2 lines) on the bookmark card.

## Alternative Flows

### A1: Edit Existing Note

**Trigger:** Bookmark already has a note (step 3).
**Flow:**

1. System displays the existing note text in the text area.
2. User modifies it.
3. Save updates the note in place.

### A2: Clear Note

**Trigger:** User deletes all text in the note field (step 3).
**Flow:**

1. System sets the note to null on save.
2. The notes section is hidden from the bookmark card.

### A3: Note Too Long

**Trigger:** User exceeds 10,000 characters (step 3).
**Flow:**

1. System shows a character count and prevents further input.
2. A validation message indicates the limit.

### A4: Note is Private

**Trigger:** Another user views the same bookmark in a shared collection (step 6).
**Flow:**

1. Notes are private: each user sees only their own notes on shared bookmarks.
2. Other users' notes are not visible.

## Postconditions

### Success Postconditions

- The bookmark has a note persisted.
- The note preview appears on the bookmark card.

### Failure Postconditions

- The note is not saved. Bookmark remains unchanged.

## Business Rules

### BR-102: Note Content

Notes support plain text only. No markdown, HTML, or rich text.

### BR-103: Note Length

Notes are limited to 10,000 characters.

### BR-104: Note Privacy

Notes are per-user per-bookmark. In shared collections, each user has their own private notes on the same bookmark.

### BR-105: Note Searchable

Notes are included in search operator evaluation via the `note:keyword` operator (UC-070). They are also included in free-text search results.
