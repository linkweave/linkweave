# Handoff: Edit Bookmark Dialog — Collapsible Properties Section

## Overview

This describes a targeted behavior change to the **Edit Bookmark dialog**: the Properties section should become collapsible when a collection has many properties, with the collapsed/expanded state persisted per-user per-collection in localStorage.

## About the Design Files

`UC-067 Properties.html` is an HTML prototype (design reference, not production code). It demonstrates the intended look and behavior. Recreate this in the existing codebase using its established patterns and libraries.

## Fidelity

**High-fidelity.** The prototype reflects intended visual design and interaction behavior. Match the look of the section header (label, count badge, "N filled" hint, chevron) and the open/close animation.

---

## The Rule

| Property count | Default state | Collapsible? |
|---|---|---|
| ≤ 5 | Always expanded | No toggle — section header is static |
| 6+ | Closed on first visit; then last user state | Yes — section header is a toggle button |

The threshold is based on the number of **property definitions on the collection**, not the number of filled values on the bookmark being edited.

---

## Behavior Details

### Always-expanded (≤5 props)
- Section header renders as a non-interactive divider
- No chevron icon
- No localStorage read/write

### Collapsible (6+ props)

**Default state on first visit:**
- Closed by default

**Subsequent visits:**
- Read saved state from localStorage key (see below)
- Open if user last opened it; closed if user last closed it

**localStorage key:**
```
<user-prefixed-key>:propsExpanded:<collectionId>
```
Claude Code: the project already uses a user-scoped localStorage prefix convention — follow that pattern. The collection identifier should be whatever ID/slug is used elsewhere for per-collection persistence.

Values: `"true"` / `"false"` (string).

**On toggle:**
- Animate open/close (see Animation below)
- Write new state to localStorage immediately

---

## Section Header

Rendered in both modes (static divider vs. interactive toggle). Contains:

1. **Cube icon** — existing property icon
2. **"Properties" label** — uppercased, muted, small caps style (matches other field group labels)
3. **Count badge** — pill showing total number of property definitions, e.g. `8`
4. **"N filled" hint** — only visible when section is **collapsed** and at least one property has a value on this bookmark. Text: `"{n} filled"`. Muted, 10–11px. Gives the user a signal that there's data inside without forcing the section open.
5. **Horizontal rule** — flex-grows to fill remaining width
6. **Chevron** — only in collapsible mode. Points down when closed, up (rotated 180°) when open. Transitions on toggle.

Layout: single flex row, `align-items: center`, `gap: 8–10px`.

---

## Animation

Use a CSS grid height trick for smooth collapse without JS height measurement:

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
  padding-top: 14px; /* spacing between header and first field */
}
```

Toggle the `shut` class on the wrapper. The inner div must have `overflow: hidden` — the grid row collapsing is what clips it.

---

## Filled Count Calculation

```ts
const filledCount = propDefs.filter(pd => {
  const v = bookmark.props?.[pd.name];
  return v !== undefined && v !== null && v !== "";
}).length;
```

Show the hint only when: `filledCount > 0 && !isExpanded && isCollapsible`

---

## Files

| File | Purpose |
|---|---|
| `UC-067 Properties.html` | Full interactive prototype — open in browser to see the behavior live |

To see the collapsible behavior in the prototype:
1. Open `UC-067 Properties.html`
2. The Edit Bookmark dialog opens automatically on load
3. Use the **Tweaks panel** (bottom-left) to switch between **Always open / Smart collapse / Start closed** and toggle **4 props / 8 props**
