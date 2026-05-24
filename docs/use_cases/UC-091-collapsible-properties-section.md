# Use Case: Collapsible Properties Section in Edit Bookmark Dialog

## Overview

**Use Case ID:** UC-091
**Use Case Name:** Collapsible Properties Section in Edit Bookmark Dialog
**Primary Actor:** User
**Goal:** Keep the Edit Bookmark dialog manageable when a collection has many properties by making the Properties section collapsible, while surfacing a "filled" hint so the user knows data is present without having to expand the section.
**Status:** Open

## Traceability

**Maps to:** FR-091
**Extends:** UC-068 (Set Bookmark Property Value)

---

## Preconditions

- The user is authenticated.
- The user has write access to the collection.
- The Edit (or Create) Bookmark dialog is open.
- At least one property definition exists for the collection.

---

## The Rule

| Property count on collection | Default state | Collapsible? |
|---|---|---|
| ≤ 5 | Always expanded | No — section header is a static divider |
| 6+ | Closed on first visit; last user state thereafter | Yes — section header is a toggle button |

The threshold is the count of **property definitions on the collection**, not the number of filled values on the bookmark being edited.

---

## Main Success Scenario (6+ properties)

1. User opens the Edit Bookmark dialog for a bookmark in a collection with 6 or more property definitions.
2. System reads the saved collapsed/expanded state from localStorage key `{userEmail}:propsExpanded:{collectionId}`.
3. If no saved state exists, the section renders **collapsed** (closed by default on first visit).
4. The section header shows: cube icon · "PROPERTIES" label · count badge (total property definitions) · "N filled" hint (only when ≥ 1 property has a value) · horizontal rule · chevron pointing down.
5. User clicks the section header toggle.
6. System animates the section open using the CSS grid height trick (grid-template-rows 0fr → 1fr).
7. Chevron rotates 180° to point upward. "N filled" hint is hidden while expanded.
8. System writes `"true"` to the localStorage key.
9. User interacts with property inputs (UC-068 main flow applies).
10. On the next visit to this dialog, the section opens in the expanded state (step 2 reads `"true"`).

## Main Success Scenario (≤ 5 properties)

1. User opens the Edit Bookmark dialog for a bookmark in a collection with 5 or fewer property definitions.
2. System renders the Properties section **always expanded** with a static non-interactive divider as the header.
3. No chevron is shown. No localStorage read or write occurs.
4. User interacts with property inputs normally (UC-068).

---

## Alternative Flows

### A1: User collapses an open section

**Trigger:** User clicks the section header while the section is expanded (step 5, reversed).
**Flow:**
1. System animates the section closed (grid-template-rows 1fr → 0fr, opacity 1 → 0).
2. Chevron rotates back to pointing down.
3. "N filled" hint appears if `filledCount > 0`.
4. System writes `"false"` to the localStorage key.

### A2: No properties have a value (collapsed)

**Trigger:** Section is collapsed and no property on this bookmark has a value.
**Flow:**
1. "N filled" hint is not shown — section header shows only icon, label, count badge, rule, and chevron.

### A3: Collection shrinks below threshold after user stored a preference

**Trigger:** Properties are deleted so the collection now has ≤ 5 definitions, but a stored localStorage key exists.
**Flow:**
1. System ignores the stored key (threshold check determines mode, not localStorage).
2. Section renders as static/always-expanded.
3. Stale localStorage entry is left in place (harmless).

---

## Postconditions

### Success Postconditions

- User preference (expanded/collapsed) for the Properties section is persisted per-user per-collection in localStorage.
- The bookmark dialog remains functional regardless of collapse state.

### Failure Postconditions

- If localStorage is unavailable (e.g., private browsing with storage disabled), the section falls back to the default state (collapsed for 6+ props, expanded for ≤ 5) without error.

---

## Business Rules

### BR-091-1: Collapse Threshold

The collapsible/always-expanded mode is determined solely by the count of **property definitions** on the collection at dialog-open time. Filled values on the bookmark do not affect the mode.

### BR-091-2: First-Visit Default

A collection with 6+ properties defaults to **closed** when no localStorage state exists for that user + collection combination.

### BR-091-3: localStorage Key Format

```
{userEmail}:propsExpanded:{collectionId}
```

Values: `"true"` (expanded) / `"false"` (collapsed). The `{userEmail}` prefix follows the existing user-scoped key convention used throughout the project (see C-009).

### BR-091-4: "N Filled" Hint Visibility

The hint is shown only when all three conditions are met:
- Section is in collapsible mode (6+ props)
- Section is currently collapsed
- At least one property on the bookmark has a non-empty, non-null value

### BR-091-5: Filled Count Calculation

```ts
const filledCount = propDefs.filter(pd => {
  const v = bookmark.props?.[pd.name]
  return v !== undefined && v !== null && v !== ''
}).length
```

### BR-091-6: Animation

Collapse/expand uses the CSS grid height trick (no JS height measurement):

```css
.props-collapse-wrap {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.22s ease, opacity 0.18s ease;
  opacity: 1;
}
.props-collapse-wrap.shut {
  grid-template-rows: 0fr;
  opacity: 0;
}
.props-collapse-inner {
  overflow: hidden;
  padding-top: 14px;
}
```

The `shut` class is toggled on the wrapper; the inner div must have `overflow: hidden`.

---

## Section Header Layout

Single flex row (`align-items: center`, `gap: 8–10px`) containing:

1. **Cube icon** — existing `Box` icon from `@lucide/vue`
2. **"Properties" label** — uppercased, muted, small caps style (matches other field group labels in the dialog)
3. **Count badge** — pill showing total number of property definitions (e.g. `8`)
4. **"N filled" hint** — only when collapsed and `filledCount > 0`. Muted, 10–11px. Text: `"{n} filled"`.
5. **Horizontal rule** — `flex: 1`, `height: 1px`, `background: border`
6. **Chevron** — only in collapsible mode. Points down (closed), rotates 180° (open). CSS transition on toggle.

In always-expanded mode (≤ 5 props): no chevron, no count badge, no "N filled" hint — plain divider row matching the existing section header style in `BookmarkDialog.vue`.

---

## Design Reference

See `docs/design/design_handoff_edit_bookmark_properties/UC-067 Properties.html` — open in browser and use the **Tweaks panel** (bottom-left) to toggle between collapse modes and property counts.
