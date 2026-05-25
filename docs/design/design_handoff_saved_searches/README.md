# Handoff: Saved Searches & Smart Collections
## FR-075 · FR-076 · FR-077 (partial) — v2

> Replaces `design_handoff_saved_searches/README.md`. Use this document; the previous one is outdated.

**Prototype:** `FR-075-076 Saved Searches v2.html` — open in a browser to explore all flows live. Design reference only; recreate in the existing codebase.

---

## Overview

Users can save a named search query (with all its operators) and pin it to the sidebar as a **Smart Collection** — a virtual folder that always shows the live result set for that query. No static snapshots.

---

## Color system — Smart Collections

Three UI concepts now each have a distinct color. **All three must be kept visually separate.**

| Concept | Color | Usage |
|---|---|---|
| Tags | `--primary` (blue) | Tag pills, tag dots, primary actions |
| Properties | `#a78bfa` (purple) | Property token chips, property cube icon |
| **Smart Collections** | `--ss` (teal) | Sidebar funnel icons, filter strip pill, save trigger |

### Token values

```css
/* dark mode */
--ss: oklch(64% 0.10 188);

/* light mode */
--ss: oklch(44% 0.10 188);
```

Use `color-mix(in oklab, var(--ss) N%, transparent)` for tinted backgrounds and borders throughout — do not hardcode hex values.

---

## FR-075 — Save a Search

### Save trigger

Icon-only **funnel** button at the far right of the filter chip strip. Visible only when:
- `tokens.length > 0` (the strip is shown), **and**
- No saved search is currently active (see Pill section below)

Appearance:
- 24×24px, solid `--ss` border, teal tinted background (`color-mix(in oklab, var(--ss) 6%, transparent)`)
- Icon: filter funnel, `--ss` color
- Clicking opens the Save Popover

> The Variation A (icon in search bar) and Variation B (labeled inline button) were explored and rejected. Variation C (right-aligned strip icon) is the only implemented variant.

### Save Popover

Fixed-position popover anchored below the trigger. **Only one state now** — new searches only. The update flow has moved to the pill (see below).

#### State — New search
Triggered when the user types a query from scratch and hits the save icon. No active saved search.

- Title: "Save search"
- Query preview — monospace, muted, truncated with ellipsis
- Name input, autofocused
- Buttons: [Cancel] [Save]

#### Dismiss
Escape key · click outside (backdrop) · any action button

#### On save
- New entry added to the Smart Collections list
- New entry set as the active saved search
- Toast: `Saved "[name]"`

---

## Active Saved Search Pill

This is the primary affordance for communicating that the user is currently "inside" a saved search context. It lives at the **right end of the filter chip strip**, replacing the save trigger whenever a saved search is active.

### States

#### 1 — Matched (query = saved query exactly)

```
⊙  Quarkus drafts  ×
```

- Funnel icon in `--ss`
- Solid teal border, teal tinted background
- `×` button to deselect (see Deselect below)
- No save/update affordance — nothing has changed

#### 2 — Dirty (query has drifted from saved query)

```
⊙  Quarkus drafts  ·  Update  ×
```

- Dashed teal border, lighter tinted background (signals "unsaved changes")
- Same `×` button
- "Update" text button — clicking immediately saves the current query into the active saved search, no popover
- On update: toast `Updated "[name]"`, pill returns to Matched state

### CSS

```css
.ss-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 4px 2px 8px;
  border-radius: 9999px;
  font-size: 11.5px;
  font-weight: 500;
  background: color-mix(in oklab, var(--ss) 12%, transparent);
  border: 1px solid color-mix(in oklab, var(--ss) 35%, transparent);
  color: var(--ss);
  transition: border-color 0.15s, background 0.15s;
}

.ss-pill.dirty {
  background: color-mix(in oklab, var(--ss) 7%, transparent);
  border-style: dashed;
}
```

### Logic

```
activeSsId = null          → no pill shown; save trigger visible (if tokens exist)
activeSsId set, exact      → Matched pill; save trigger hidden
activeSsId set, drifted    → Dirty pill; save trigger hidden
```

"Exact" means `activeSs.query.trim() === currentQuery.trim()`.

---

## Deselect

Two affordances to drop the saved search context. Both keep the current query intact — they only clear `activeSsId`.

| Affordance | Location |
|---|---|
| `×` on the pill | Right end of the pill, always visible |
| Click active sidebar row again | Clicking the highlighted row toggles it off |

Deselect does **not** clear the search query. After deselecting, the filter strip shows the raw token chips and the save trigger reappears.

---

## FR-076 — Smart Collections Sidebar

### Section placement
Collapsible section in the left sidebar, between Folders and Tags.

### Section header
- Label: "SMART COLLECTIONS" — 10px, uppercase, muted
- Chevron at far right, rotates 180° when open
- Clicking the header toggles expanded / collapsed
- No inline "+" button

### Collapse state — localStorage

    <user-prefix>:smartCollectionsExpanded

Value: `"true"` / `"false"`. Default: **open**.

### Each row

- Icon: filter funnel at `--ss` teal (not purple — updated from v1)
- Name label, truncated with ellipsis
- On hover: ellipsis (⋯) icon button at far right

### Click behavior

```
row is inactive   → load query into search bar, set activeSsId, highlight row
row is active     → clear activeSsId (deselect), query unchanged, row un-highlights
```

### Active row style

```css
background: color-mix(in oklab, var(--primary) 12%, var(--secondary));
color: var(--primary);
```

---

## FR-077 — Manage (hover actions)

### Ellipsis menu

Appears on row hover. Opens a small context menu positioned below the trigger.

| Action | Behavior |
|---|---|
| **Rename** | Row label becomes inline input, pre-filled. Enter or blur → commit. Escape → cancel. Toast: `Renamed to "[name]"` |
| **Delete** | Remove immediately. If this was the active saved search, clear `activeSsId`. Toast: `Deleted "[name]"`. Does not affect any bookmarks. |

### Edit query

No separate "edit query" UI. The flow is:

1. Click Smart Collection row → query loads into search bar, row highlights, Matched pill appears
2. Modify the query → pill goes Dirty state
3. Click **Update** in the pill → query saved, pill returns to Matched, toast confirms

---

## Filter Strip Layout

Left to right:

```
[Filters icon] [Filters label]  [save trigger?]  [token chips...]  [result count]  [Clear all]  [ss-pill?]
```

- Save trigger and ss-pill are mutually exclusive and always right-aligned
- Token chips are between the label and the right-side controls
- `chip-count` uses `margin-left: auto` to push the right-side group to the far end

---

## Tags Section — Collapsible (recommended)

| Setting | Tags | Smart Collections |
|---|---|---|
| Default | Open | Open |
| localStorage key | `<user-prefix>:tagsExpanded` | `<user-prefix>:smartCollectionsExpanded` |
| "+" in header | No | No |

---

## Collapse Animation

```css
.section-wrap {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.22s ease, opacity 0.18s ease;
  opacity: 1;
}
.section-wrap.shut {
  grid-template-rows: 0fr;
  opacity: 0;
  pointer-events: none;
}
.section-inner {
  overflow: hidden;
}
```

Toggle `.shut` on the wrapper. The inner element must have `overflow: hidden`.

---

## NFR-019 — Evaluation Consistency

Saved search queries use the same tokenizer and matching logic as the live search bar. Client-side and server-side evaluation must produce identical results.

```
token      := neg? (tag | property | key-op-value | quoted | text)
neg        := "-"
tag        := "#" word
property   := "property:" key ("=" | ">" | "<" | ">=" | "<=") value
key-op-val := word ":" non-whitespace
quoted     := '"' .* '"'
text       := non-whitespace+
```

---

## Files

| File | Purpose |
|---|---|
| `FR-075-076 Saved Searches v2.html` | Interactive prototype — open in browser to explore all flows |

### Exploring the prototype
1. Open `FR-075-076 Saved Searches v2.html`
2. Click any Smart Collection row in the sidebar → Matched pill appears at right end of strip
3. Edit the query → pill goes Dirty (dashed border, "Update" appears)
4. Click **Update** → toast fires, pill returns to Matched
5. Click **×** on the pill → deselects, query intact, save trigger reappears
6. Click the active sidebar row again → same deselect behavior
7. Clear the query entirely → strip hides, no pill
8. Use **Tweaks → Theme** to check both dark and light
