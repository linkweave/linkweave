# Use Case: Auto-Tag Bookmark by URL Pattern

## Overview

**Use Case ID:** UC-045
**Use Case Name:** Auto-Tag Bookmark by URL Pattern
**Primary Actor:** User
**Goal:** Present tag suggestions in a dedicated UI area based on the entered URL, so that the user can review, select/deselect individual suggestions, and accept them into the bookmark's tag selection with a single action.
**Status:** Implemented (with custom user-defined rules)

## Traceability

**Maps to:** FR-051

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection.
- The user is creating a new bookmark (UC-005) or editing an existing bookmark (UC-007).
- At least one auto-tag rule is configured for the collection (or system defaults exist).

## Main Success Scenario

1. User enters a URL in the bookmark creation or edit form.
2. System analyzes the URL against configured auto-tag rules.
3. System identifies one or more matching rules (e.g., subdomain `dev.myproject.com` matches rule for `dev`).
4. System displays a dedicated "Suggested Tags" section below the URL field, showing each matched tag as a toggleable chip. All suggestions are pre-selected (checked) by default.
5. User reviews the suggested tags and toggles individual tags on or off as desired.
6. User clicks the "Accept Suggestions" button.
7. System moves the selected suggested tags into the standard tag dropdown/selection, adding them to any manually selected tags.
8. System clears the "Suggested Tags" section.
9. User submits the form.
10. System saves the bookmark with the final tag selection.

## Alternative Flows

### A1: No Matching Rules

**Trigger:** No auto-tag rules match the entered URL (step 2).
**Flow:**

1. The "Suggested Tags" section is not displayed.
2. Use case continues — user manually selects tags or submits without tags.

### A2: Multiple Matching Rules

**Trigger:** The URL matches multiple auto-tag rules simultaneously (step 3).
**Flow:**

1. System displays all matching tags as toggleable chips in the "Suggested Tags" section (e.g., URL `dev.api.myproject.com` shows both `dev` and `api` chips).
2. All matching tags are pre-selected (checked) by default.
3. Use case continues at step 5.

### A3: Suggested Tag Does Not Exist Yet

**Trigger:** A matching rule references a tag that does not exist in the collection (step 4).
**Flow:**

1. System displays the tag name in the "Suggested Tags" section with a visual indicator that it will be created upon acceptance.
2. When the user accepts, the tag is created and added to the bookmark.
3. Use case continues at step 6.

### A4: URL Changed After Suggestions Appear

**Trigger:** User modifies the URL after suggestions were displayed (step 1).
**Flow:**

1. System re-analyzes the updated URL against the auto-tag rules.
2. System replaces the contents of the "Suggested Tags" section with the new suggestions, pre-selected by default.
3. Use case continues at step 5.

### A5: User Dismisses Suggestions Without Accepting

**Trigger:** User ignores the "Suggested Tags" section and submits the form without clicking "Accept Suggestions" (step 9).
**Flow:**

1. System saves the bookmark without any of the suggested tags.
2. Only manually selected tags (if any) are applied.

### A6: User Deselects All Suggestions

**Trigger:** User toggles off all suggested tags (step 5).
**Flow:**

1. The "Accept Suggestions" button is disabled or hidden since no tags are selected.
2. User can still submit the form without any auto-suggested tags.

### A7: Partial Acceptance with Existing Manual Tags

**Trigger:** User has already manually selected tags in the tag dropdown before accepting suggestions (step 6).
**Flow:**

1. System adds only the selected suggested tags to the existing manual selection without removing any manually chosen tags.
2. Use case continues at step 8.

## Postconditions

### Success Postconditions

- The bookmark is saved with the final tag selection (manually selected + accepted suggestions).
- Any tags created by auto-tag rules exist in the collection.

### Failure Postconditions

- Bookmark is saved without auto-suggested tags if the auto-tag analysis fails.
- No error is displayed to the user — auto-tagging is a best-effort feature.

## Business Rules

### BR-070: Subdomain-Based Tagging

When a URL contains a recognized environment subdomain pattern (e.g., `dev.`, `uat.`, `staging.`, `prod.`, `test.`), the system should suggest a tag matching the subdomain prefix (e.g., `dev`, `uat`, `staging`, `prod`, `test`).

### BR-071: Suggestions Are Separate from Tag Selection

Suggested tags appear in a dedicated "Suggested Tags" section, distinct from the standard tag selection. They are not applied to the bookmark until the user explicitly clicks "Accept Suggestions."

### BR-072: Best-Effort Auto-Tagging

Auto-tagging is a convenience feature. If the URL analysis fails or no rules match, the bookmark is still created successfully. Auto-tagging must never block bookmark creation or editing.

### BR-073: Accept Does Not Remove Manual Tags

When suggestions are accepted, any tags the user has already manually selected must be preserved.

### BR-074: Toggleable Suggestions

Each suggested tag can be individually toggled on or off before acceptance. Only the tags that remain selected when "Accept Suggestions" is clicked are added to the bookmark.

### BR-075: Suggestions Are Not Persisted Without Acceptance

If the user submits the form without clicking "Accept Suggestions," no suggested tags are applied to the bookmark.
