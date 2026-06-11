# Handoff: Batch Move & Delete Bookmarks
## UC-074 (FR-078, FR-080) — selection model, batch bar, dialogs & states

**Prototype:** `UC-074 Batch Select.html` — interactive; click through the real flow. Design reference only; recreate in the existing codebase.

> The Tweaks panel in the prototype still exposes the rejected variants (`hover` / `always` entry, `ring` selected style) so they aren't re-explored. Everything below describes the **recommended** path only.

---

## Overview

Multi-select for the bookmark list with a batch action bar. Decided direction:

| Decision | Choice |
|---|---|
| Entry mechanic | **Explicit `Select` button in the toolbar** (rejected: hover checkbox, always-visible checkboxes) |
| Selected treatment | **Inset shrink** — the capture shrinks inside its frame, Photos-style (rejected: plain ring) |
| Batch bar placement | **Sticky bar between the toolbar and the list**, slides open when selection is non-empty |
| Actions | Move · Add tag · Copy URLs · **Delete** (destructive, separated by a divider) |

Selection is **transient UI state** — never persisted, cleared on navigation, action success, or Esc.

---

## Selection model

### Entering selection

1. **`Select` button** in the toolbar (primary, discoverable path). Click → selection mode: checkboxes appear on every card/row; the button relabels to **Cancel**.
2. **⌘/Ctrl-click** on any card or row (power-user path) — toggles that bookmark and implicitly enters selection mode. Works even when the `Select` button was never touched.

### While selecting

| Input | Behavior |
|---|---|
| Click on card/row | Toggles that bookmark (click no longer opens the link) |
| Click on checkbox | Toggles (same as card click in this mode; checkbox is the only toggle target *outside* selection mode) |
| **Shift-click** | Range select from the **anchor** (last toggled item) to the clicked item — *adds* the range, never removes |
| **⌘/Ctrl-click** | Toggle single item |
| **⌘/Ctrl-A** | Select all in current view (only intercepted while selecting) |
| **Esc** | Clear selection and exit selection mode |
| `×` in batch bar | Same as Esc |
| `Select all N` link in batch bar | Same as ⌘A |

Anchor rule: every toggle (click, checkbox, ⌘-click) moves the anchor to that item. Shift-click extends from the current anchor and does **not** move it. Shift-click with no anchor degrades to a plain toggle.

### Leaving selection

- Esc / `×` / `Cancel` button → clear, exit mode
- **Successful** Move / Delete / Add tag → toast, clear, exit (per UC-074 step 9 / step 8)
- **Failed** (atomic rollback) action → error toast, **selection retained** so the user can retry
- Copy URLs → toast, selection retained (non-mutating; users often copy then act again)

---

## Checkbox affordance

22px circle (19px in list rows), top-left of the capture frame. Hidden until selection mode; animates in with a scale+fade.

```css
.sel-check {
  position: absolute; top: 8px; left: 8px; z-index: 3;
  width: 22px; height: 22px; border-radius: 50%;
  display: grid; place-items: center;
  border: 1.5px solid rgba(255,255,255,.85);
  background: rgba(14,16,20,.45);
  box-shadow: 0 1px 5px rgba(0,0,0,.45);
  opacity: 0; transform: scale(.7); pointer-events: none;
  transition: opacity .13s, transform .13s, background .13s;
}
.sel-check.visible { opacity: 1; transform: scale(1); pointer-events: auto; }
.sel-check.checked {
  background: var(--primary);
  border: 1px solid color-mix(in oklab, var(--primary) 92%, white);
  /* white 13px check icon, stroke-width 3 */
}
/* list rows: 19px circle, top/left 6px, 11px check icon */
```

- While the (unchecked) checkbox is visible, a **contrast scrim** sits over the capture so the white circle survives light screenshots: `linear-gradient(160deg, rgba(10,12,16,.42), transparent 38%)`, fades with the checkbox.
- Hit target: pad to ≥ 32px via a transparent outline area if needed; the row/card itself toggles in selection mode anyway.

## Selected treatment — inset shrink

The capture shrinks inside its fixed frame; the gap exposes the frame background (`#0e1014`). The frame itself never changes size, so the grid/list does not reflow.

| Layout | Unselected | Selected |
|---|---|---|
| Grid cover | `inset: 0; border-radius: 0` | `inset: 10px 10px 4px; border-radius: 6px` |
| List thumb (124px) | `inset: 0` | `inset: 5px; border-radius: 4px` |

```css
.capture-wrap { position: absolute; inset: 0; overflow: hidden;
  transition: inset .15s cubic-bezier(.2,.7,.3,1), border-radius .15s; }
.card.selected .capture-wrap { inset: 10px 10px 4px; border-radius: 6px; }
```

Plus on the container:
- Grid card: `border-color: var(--primary)` + `box-shadow: 0 0 0 1px rgb(59 130 246 / .53)`
- List row: background `rgba(59,130,246,.09)`; thumb border goes primary with the same 1px shadow ring
- `prefers-reduced-motion: reduce` → drop the inset transition, snap instantly

## List view (124px leading thumbnail)

Selection composes with the FR-080 list treatment — nothing moves when selection mode starts:

- Checkbox overlays the **thumbnail** top-left (19px). No leading checkbox column — reserving one would shift text on mode entry.
- Row click toggles in selection mode; row hover background `rgba(255,255,255,.025)`, selected `rgba(59,130,246,.09)`.
- Tags stay right-aligned before the ⋯ menu; unaffected by selection.

## Previews off — favicon ⇄ checkbox swap

When the FR-080 **Previews** toggle is off (or a collection forces `previews: off`), there is no capture frame to host the checkbox. The selection affordance becomes an **in-place swap**: in selection mode the favicon flips into a round checkbox in the exact same slot (the Gmail/Drive avatar-flip pattern). Nothing reflows — the checkbox inherits the favicon's box.

| Layout | Favicon slot | Swap size |
|---|---|---|
| Grid card (previews off) | 22px, leading in the title row | 22px circle, 12px check |
| List row (previews off) | 16–18px, inline in the title row | 18px circle, 10px check |

```css
.swap { position: relative; width: 22px; height: 22px; }
.swap .favicon, .swap .checkbox { position: absolute; inset: 0;
  transition: opacity .13s, transform .15s; }
/* resting */
.swap .checkbox { opacity: 0; transform: scale(.6) rotate(30deg); pointer-events: none; }
/* selection mode */
.swap.checking .favicon  { opacity: 0; transform: scale(.6) rotate(-30deg); }
.swap.checking .checkbox { opacity: 1; transform: scale(1); pointer-events: auto; }

.swap .checkbox { border-radius: 50%; border: 1.5px solid #6e7480; background: transparent; }
.swap .checkbox[aria-checked="true"] { background: var(--primary);
  border: 1px solid color-mix(in oklab, var(--primary) 92%, white); }
```

- The unchecked circle uses a **muted border (`#6e7480`)**, not the white used over captures — it sits on card background, not imagery.
- Selected card (grid, previews off): the inset treatment has no capture to shrink, so selection reads as **ring + background tint** — border primary, `box-shadow: 0 0 0 1px rgb(59 130 246 / .53)`, background `color-mix(in oklab, var(--primary) 8%, var(--card))`.
- Selected row (list, previews off): same `rgba(59,130,246,.09)` row tint as with previews on.
- The counter-rotation on the flip (favicon out at −30°, checkbox in from +30°) is what sells the swap; with `prefers-reduced-motion: reduce` use a plain crossfade.
- The swap is also the **hover target** if the hover entry mechanic is ever adopted: hovering the title row flips the favicon, same as covers do.
- Identity trade-off: while selecting, the favicon is hidden. Acceptable — the title and URL remain, and selection mode is transient. Do **not** try to show both (favicon + separate checkbox column); that's the reflow we're avoiding.

---

## Batch action bar

Sticky, sits **between the toolbar and the scroll area** (never overlaps content; the grid keeps its own scroll). Slides open via `max-height` when `selected.size > 0`.

```css
.batch-bar {
  display: flex; align-items: center; gap: 8px;
  height: 46px; padding: 0 18px;
  background: color-mix(in oklab, var(--primary) 9%, #171a1f);
  border-bottom: 1px solid color-mix(in oklab, var(--primary) 90%, black);
}
.batch-bar-clip { overflow: hidden; max-height: 0;
  transition: max-height .18s cubic-bezier(.2,.7,.3,1); }
.batch-bar-clip.open { max-height: 52px; }
```

Contents, left → right:

1. `×` clear button (26px, same as Esc)
2. **`N selected`** — 13px / 700, `tabular-nums` so the count doesn't jitter
3. `Select all N` link (primary-tinted text; hidden once everything is selected)
4. `esc` keycap hint (monospace, 10.5px, bordered chip)
5. *(spacer)*
6. `Move` · `Add tag` · `Copy URLs` — standard 30px toolbar buttons (same component as the Previews toggle)
7. 1px divider
8. `Delete` — same button, text `#f87171`. Destructive action is **last and separated**.

The toolbar `Select` button shows the active (pressed) style while selecting and relabels to **Cancel**.

---

## Dialogs & flows

### Move (UC-074 Move 3–9)

Reuses the **single-bookmark move dialog** unchanged except the title: `Move N bookmarks`. Folder radio list scoped to the current collection (BR-100), first entry *"No folder (collection root)"* in italic (BR-101). Confirm = `Move here` (primary).

- Success → toast `Moved N bookmarks to {folder}.` → clear selection
- The picker itself was **out of scope** for this design — the prototype shows a minimal stand-in

### Delete (UC-074 Delete 3–8)

Confirmation dialog, destructive styling:

- Title: **`Move N bookmarks to trashbin?`**
- Body: `They can be restored from the trashbin. Nothing is permanently deleted.` (BR-099 — soft delete only; copy says so instead of a scary warning)
- Buttons: `Cancel` (ghost) / `Move to trashbin` (filled `#c93838`)
- Cancel / Esc → close, **selection maintained** (A3)
- Confirm → rows/cards animate out (fade + scale .92 / slide-left, 240ms) → toast `N bookmarks moved to trashbin.` → **sidebar Trashbin count badge updates** (primary-tinted pill)

### Add tag

Small anchored popover under the button: existing tags with color dots. Pick → toast `Tagged N bookmarks with "tag".` → clear selection.

### Copy URLs

No dialog. Writes newline-separated `https://` URLs to the clipboard → toast `Copied N URLs to clipboard.` Selection retained.

---

## Error & edge states

### Atomic failure (A1 / A2, BR-097 / BR-098)

Backend rolls back; the UI shows an **error toast** and keeps the selection:

- Move: `Failed to move bookmarks. No changes were made.`
- Delete: `Failed to delete bookmarks. No changes were made.`

Error toast = same toast component with alert icon `#f87171` and border `rgba(201,56,56,.55)`. Toggle **Simulate batch failure** in the prototype's Tweaks to see it.

### Toasts

Bottom-center, stacked, auto-dismiss 4s. Dark chip (`#22252e`, 1px `--border`), 13px, leading icon — success check `#4ade80`, error alert `#f87171`.

### >500 chunking (A4) — not prototyped

Recommendation when implemented: keep the batch bar open and swap its right side for an inline progress (`Moving 1,240 bookmarks… 500/1,240`) with the actions disabled. Do not use a modal — the operation is safe to watch from the list. Flag for a follow-up design pass if >500 selections are actually common.

---

## Accessibility

- Checkboxes: `role="checkbox"` + `aria-checked`; cards/rows get `aria-selected` while in selection mode
- Batch bar: `role="toolbar"`, announced via `aria-live="polite"` count ("5 selected")
- `Select` button: `aria-pressed`
- Dialogs: focus trap, confirm button auto-focused, Esc closes (and does **not** also clear the selection underneath)
- All keyboard paths above work without a pointer once selection mode is entered via the `Select` button

## Business-rule mapping

| Rule | Where it lands in this design |
|---|---|
| BR-097/098 atomicity | Single error toast + retained selection; no per-item partial states in the UI |
| BR-099 soft delete | Dialog copy + trashbin count update; no "permanent delete" anywhere in the batch bar |
| BR-100 same-collection | Folder picker lists only the current collection's folders |
| BR-101 move to root | "No folder (collection root)" as the first picker entry |

## Files

| File | Purpose |
|---|---|
| `UC-074 Batch Select.html` | Interactive prototype (grid + list, all flows) |

### Exploring the prototype
1. Open `UC-074 Batch Select.html`
2. Click **Select** in the toolbar → click cards/rows; try shift-click and ⌘-click; ⌘A; Esc
3. Switch grid ⇄ list with the segmented control — selection survives the switch
4. Toggle **Previews** off and re-enter selection — favicons flip into checkboxes in place, in both layouts
5. Run **Delete** to see the confirm dialog, removal animation, trashbin badge and toast; **Move** for the folder dialog
6. Tweaks → **Simulate batch failure** for the A1/A2 rollback error state
