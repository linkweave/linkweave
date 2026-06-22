# Handoff: Batch Select — Move & Delete Bookmarks (UC-074)

## Overview
Multi-select for the bookmark view (product: *linkweave*) with a batch action bar: select N bookmarks, then **Move**, **Add tag**, **Copy URLs**, or **Delete** them in one action. Implements **UC-074** (maps to FR-078, FR-080). Works in both **grid** and **list** layouts, and in both **previews-on** and **previews-off** modes.

Decided design direction:

| Decision | Choice |
|---|---|
| Entry mechanic | **Explicit `Select` button in the toolbar** + ⌘/Ctrl-click as a power path. (A hybrid that also shows a hover checkbox is under consideration — the prototype's Tweaks panel can demo it — but **button entry is the spec until told otherwise.**) |
| Selected treatment | **Inset shrink** — the capture shrinks inside its fixed frame (Photos-style); no reflow |
| Batch bar | **Sticky bar between the toolbar and the scroll area**, slides open when selection is non-empty |
| Previews off | **Favicon ⇄ checkbox swap** in place (Gmail/Drive avatar-flip pattern) |

## About the Design Files
The files in this bundle are **design references created in HTML/React** — a runnable prototype showing intended look and behavior, **not production code to copy directly**. Bookmark thumbnails are CSS-drawn faux pages standing in for real screenshots; the move/tag data is hardcoded.

Your task is to **recreate this design in the target codebase**: a **Vue 3 + TypeScript** app. Relevant existing components: `frontend/src/components/bookmark/BookmarkCard.vue`, `BookmarkList.vue`, `BookmarkListToolbar.vue`, `BookmarkLayoutToggle.vue`, and `frontend/src/views/CollectionView.vue`. Extend those — do not introduce React. React snippets in this doc are **behavioral pseudocode**; port the logic, not the framework. Reuse the app's existing toast system, dialog/modal component, and folder-picker dialog (UC-074 specifies the move dialog is the **same dialog as single-bookmark move**) if they exist; only build new ones where the codebase has none.

## Fidelity
**High-fidelity.** Colors, spacing, typography, timing, and interaction details are final and intended to be matched, with two exceptions: (1) faux-page thumbnails → real capture `<img>`s in production; (2) sidebar/header chrome in the prototype is context only — match the app's real chrome.

---

## Screens / Views

### 1. Toolbar additions

The existing list toolbar (`BookmarkListToolbar.vue`) gains a **Select** button, placed left of the Previews toggle:

- Same ghost-button style as the Previews toggle: `height: 30px; padding: 0 10px; border-radius: 7px; border: 1px solid #363d49; background: transparent; color: #a3a3a3; font-size: 12px; font-weight: 600; gap: 6px`.
- Resting label: check icon (13px) + `Select`.
- While selection mode is active: relabels to **×-icon + `Cancel`** and takes the active style: `border-color: color-mix(in oklab, #3b82f6 90%, black); background: rgba(59,130,246,.13); color: #9cc0ff`. `aria-pressed` reflects the mode.
- Click toggles selection mode (entering shows checkboxes; leaving clears any selection).

### 2. Batch action bar

A horizontal bar **between the toolbar and the scroll region** — sticky, never overlaps content. Animates open/closed by clipping `max-height` (closed `0` → open `52px`, `transition: max-height .18s cubic-bezier(.2,.7,.3,1)`). Open whenever `selectedIds.size > 0`.

**Bar:** `height: 46px; padding: 0 18px; display: flex; align-items: center; gap: 8px; background: color-mix(in oklab, #3b82f6 9%, #171a1f); border-bottom: 1px solid color-mix(in oklab, #3b82f6 90%, black)`.

Contents, left → right:
1. **`×` clear button** — 26×26, transparent, icon 15px, color `#ededed`. Same effect as Esc.
2. **Count** — `"N selected"`, 13px / 700, `#ededed`, `font-variant-numeric: tabular-nums` (count must not jitter as it changes).
3. **`Select all N` link** — text button, 12.5px / 600, color `#9cc0ff`. Hidden when everything is already selected.
4. **`esc` keycap hint** — monospace 10.5px, color `#7d828c`, `border: 1px solid #363d49; border-radius: 4px; padding: 1px 5px`.
5. *(flex spacer)*
6. **Action buttons** — `Move` (folder-with-arrow icon), `Add tag` (tag icon), `Copy URLs` (copy icon). Same 30px ghost-button style as toolbar buttons. Hover: `background: #252a32`.
7. **1px vertical divider** — `width: 1px; height: 20px; background: #363d49; margin: 0 4px`.
8. **`Delete`** — same button style, text + icon color `#f87171`. Destructive action is last and separated.

### 3. Grid card — selection (previews ON)

Extends `BookmarkCard.vue` (16:9 cover + body):

- **Checkbox**: 22px circle, absolute `top: 8px; left: 8px; z-index: 3` over the cover. Unchecked: `border: 1.5px solid rgba(255,255,255,.85); background: rgba(14,16,20,.45); box-shadow: 0 1px 5px rgba(0,0,0,.45)`. Checked: `background: #3b82f6; border: 1px solid color-mix(in oklab, #3b82f6 92%, white)` + white check icon 13px, stroke-width 3.
- Hidden until selection mode: `opacity: 0; transform: scale(.7); pointer-events: none` → visible `opacity: 1; transform: scale(1)`; `transition: opacity .13s, transform .13s`.
- **Contrast scrim** while an unchecked checkbox is visible (so the white circle survives light screenshots): overlay `linear-gradient(160deg, rgba(10,12,16,.42), transparent 38%)`, fades with the checkbox. Skip the scrim when the card is selected (the inset gap provides contrast).
- **Selected (inset shrink)**: the capture wrapper (absolute, fills the 16:9 frame) animates `inset: 0 → 10px 10px 4px` and `border-radius: 0 → 6px`; `transition: inset .15s cubic-bezier(.2,.7,.3,1)`. Frame background `#0e1014` shows through the gap. The frame itself never resizes — **no grid reflow**. Card border goes `#3b82f6`-ish (`shade(primary, -5%)`) + `box-shadow: 0 0 0 1px rgb(59 130 246 / .53)`.
- `prefers-reduced-motion: reduce`: snap, no inset/scale transitions.

### 4. List row — selection (previews ON)

Extends the FR-080 list row (124px leading 16:9 thumbnail):

- **Checkbox**: same as grid but 19px, `top: 6px; left: 6px`, check icon 11px — overlays the **thumbnail**, not a separate column (a leading checkbox column would shift text on mode entry; rejected).
- **Selected**: row background `rgba(59,130,246,.09)`; thumbnail border `#3b82f6` + `0 0 0 1px rgb(59 130 246 / .53)` ring; capture insets `0 → 5px`, radius `4px`.
- Row hover (any mode): `background: rgba(255,255,255,.025)`.

### 5. Previews OFF — favicon ⇄ checkbox swap (both layouts)

With previews off there is no capture frame to host a checkbox. The favicon **flips in place** into a round checkbox when selection mode is active; the checkbox inherits the favicon's exact box, so nothing reflows.

| Layout | Slot | Swap size |
|---|---|---|
| Grid card (text-only: favicon + 2-line title + URL + tags) | leading in title row | 22px circle, 12px check |
| List row (compact text row) | inline in title row, where the 16px favicon sits | 18px circle, 10px check |

- Both elements absolutely positioned in the same relative box. Resting: checkbox `opacity: 0; transform: scale(.6) rotate(30deg); pointer-events: none`. Selection mode: favicon `opacity: 0; transform: scale(.6) rotate(-30deg)`; checkbox `opacity: 1; transform: scale(1)`. `transition: opacity .13s, transform .15s`. The counter-rotation sells the flip; reduced-motion → plain crossfade.
- Unchecked circle border is **muted `#6e7480`** (not white — it sits on card background, not imagery). Checked state identical to the overlay checkbox.
- **Selected card (grid, previews off)**: no capture to inset, so selection reads as ring + tint: border primary, `box-shadow: 0 0 0 1px rgb(59 130 246 / .53)`, `background: color-mix(in oklab, #3b82f6 8%, #1e2028)`.
- **Selected row (list, previews off)**: same `rgba(59,130,246,.09)` row tint. URL line aligns under the **title** (`padding-left: 25px`), not under the checkbox.
- Do **not** render favicon + a separate checkbox simultaneously; hiding identity during transient selection is the accepted trade-off.

### 6. Move dialog

Reuse the **existing single-bookmark move dialog**, parameterized for batch:
- Title: `Move N bookmarks`; subtitle `Choose a destination folder in this collection.`
- Folder radio list scoped to the **current collection only** (BR-100). First entry: *`No folder (collection root)`* in italic at reduced opacity (BR-101, `folderId = null`).
- Selected folder row: `border: 1px solid #2f66c4; background: rgba(59,130,246,.12); color: #bcd5ff` + trailing check.
- Footer: `Cancel` (ghost) / **`Move here`** (primary `#3b82f6`, auto-focused).
- Modal: 400px, `border-radius: 12px; border: 1px solid #363d49; background: #1e2028; box-shadow: 0 24px 70px rgba(0,0,0,.6)`; overlay `rgba(8,9,12,.6)` + slight blur; entrance `scale(.96) translateY(4px) → none` over .16s.

### 7. Delete confirmation dialog

- 380px modal, same chrome. Leading icon tile: 34px, `border-radius: 9px; background: rgba(201,56,56,.14); border: 1px solid rgba(201,56,56,.4)`, trash icon `#f87171`.
- Title: **`Move N bookmarks to trashbin?`** (15px / 700)
- Body: `They can be restored from the trashbin. Nothing is permanently deleted.` (12.5px) — calm copy because this is a soft delete (BR-099), not a destructive warning.
- Footer: `Cancel` (ghost) / **`Move to trashbin`** (filled `#c93838`, white text, auto-focused).

### 8. Add tag popover

Anchored under the `Add tag` button: 186px, `border-radius: 9px`, card background, `box-shadow: 0 14px 40px rgba(0,0,0,.55)`. Header `ADD TAG` (11px / 700, letter-spacing .05em, `#7d828c`), then tag rows (30px, color dot 9px + name 12.5px, hover `#252a32`). Closes on outside click. Picking a tag applies it to the whole selection.

### 9. Toasts

Bottom-center, stacked column, `gap: 8px`, auto-dismiss **4s**. Chip: `padding: 10px 16px; border-radius: 9px; background: #22252e; border: 1px solid #363d49; box-shadow: 0 10px 34px rgba(0,0,0,.5)`; 13px text `#ededed`; entrance fade+rise .2s. Leading icon: success check `#4ade80`; error alert-triangle `#f87171` with border `rgba(201,56,56,.55)`.

Exact strings (from UC-074):
- `Moved N bookmarks to {folder}.`
- `N bookmarks moved to trashbin.`
- `Copied N URLs to clipboard.`
- `Tagged N bookmarks with "{tag}".`
- `Failed to move bookmarks. No changes were made.` (error)
- `Failed to delete bookmarks. No changes were made.` (error)

### 10. Trashbin count (sidebar)

After a successful batch delete, the sidebar Trashbin row shows/updates a count badge: pill, 11px / 700, `color: #9cc0ff; background: rgba(59,130,246,.14); border: 1px solid #2f66c4; border-radius: 9999px; padding: 0 7px`.

---

## Interactions & Behavior

### Selection model

**Entering:**
1. Toolbar `Select` button (primary path) → selection mode on, checkboxes appear everywhere.
2. **⌘/Ctrl-click** any card/row → toggles that bookmark and implicitly enters selection mode (works without the button).

**While selecting:**

| Input | Behavior |
|---|---|
| Click card/row | Toggle that bookmark (click no longer opens the link) |
| Click checkbox | Toggle (checkbox is the only toggle target *outside* selection mode) |
| Shift-click | Range select from the **anchor** to the clicked item — **adds** the range, never removes |
| ⌘/Ctrl-click | Toggle single |
| ⌘/Ctrl-A | Select all in current view (only intercepted while selecting; `preventDefault`) |
| Esc | Clear selection + exit mode (but if a dialog is open, Esc only closes the dialog) |
| `×` / `Cancel` / `Select all N` | As labeled |

**Anchor rule:** every toggle moves the anchor to that item. Shift-click extends from the current anchor and does **not** move it. Shift-click with no anchor degrades to a plain toggle.

**Leaving:** Esc/×/Cancel; or automatically after a **successful** Move / Delete / Add tag (per UC-074 steps 8–9). After a **failed** (rolled-back) action the selection is **retained** for retry. `Copy URLs` never clears the selection (non-mutating).

**Layout/preview switches:** selection (a set of bookmark IDs) **survives** grid⇄list and previews on/off toggles.

### Action flows

- **Move:** dialog → confirm → `POST` batch move (atomic) → success toast → clear selection. Failure → error toast, selection retained, dialog closed.
- **Delete:** confirm dialog → soft-delete batch (atomic, sets `deletedAt`) → rows/cards animate out (grid: fade + `scale(.92)`; list: fade + `translateX(-12px)`; 240ms) → toast → trashbin count refresh → clear selection. Cancel (A3): close dialog, **selection maintained**.
- **Copy URLs:** `navigator.clipboard.writeText(urls.join('\n'))` with full `https://` URLs → toast. No dialog.
- **Add tag:** popover pick → apply to selection → toast → clear selection.

### Edge cases

- **A1/A2 atomic failure (BR-097/098):** backend rolls back; UI shows one error toast and keeps the selection. No per-item partial states.
- **A4 — >500 items:** backend chunks in batches of 500. UI recommendation (not prototyped): keep the batch bar open, replace the action cluster with inline progress (`Moving 1,240 bookmarks… 500/1,240`), actions disabled. No modal.
- Esc precedence: open dialog catches Esc first (capture phase); the selection underneath is untouched.

## State Management

Suggested store state (Pinia or component-level in `CollectionView.vue`):

```ts
selectMode: boolean            // toolbar button toggled, may be true with 0 selected
selectedIds: Set<string>       // survives layout/preview switches; never persisted
anchorIndex: number | null     // for shift-range; index in the currently rendered order
// derived:
selecting = selectMode || selectedIds.size > 0
```

- Clear on: route/collection change, Esc, action success.
- Batch endpoints must be **atomic** (NFR-018) — single request per action, not N requests.
- After delete success: refresh trashbin count (UC-074 step 8).

## Design Tokens

| Token | Value |
|---|---|
| Background / card / secondary | `#14161a` / `#1e2028` / `#252a32` |
| Border | `#363d49` (row separators `#2a2f39`) |
| Foreground / muted | `#ededed` / `#a3a3a3` |
| Primary | `#3b82f6` (active text on tint: `#9cc0ff`) |
| Selection tint (rows/cards) | `rgba(59,130,246,.09)` / `color-mix(in oklab, #3b82f6 8%, #1e2028)` |
| Selection ring | `border: primary` + `0 0 0 1px rgb(59 130 246 / .53)` |
| Batch bar background | `color-mix(in oklab, #3b82f6 9%, #171a1f)` |
| Destructive | text `#f87171`, filled button `#c93838` |
| Success / error toast icon | `#4ade80` / `#f87171` |
| Capture frame background | `#0e1014` |
| Radii | cards 8px, buttons 7px, dialogs 12px, checkbox 50% |
| Motion | checkbox/swap .13–.15s; inset .15s `cubic-bezier(.2,.7,.3,1)`; bar .18s; removal .22–.24s |
| Type | system UI stack; 13px base, 12px meta, 14px card titles, 15px dialog titles |

## Accessibility

- Checkboxes: `role="checkbox"` + `aria-checked`; cards/rows `aria-selected` while in selection mode.
- Batch bar: `role="toolbar"`; count announced via `aria-live="polite"`.
- `Select` button: `aria-pressed`.
- Dialogs: focus trap, confirm auto-focused, Esc closes without clearing selection.
- Full keyboard operability once mode is entered via the button.
- Checkbox hit target ≥ 32px (pad transparently) even though drawn at 19–22px.

## Assets

No image assets. All icons are 24×24 stroke SVGs (stroke-width 2, round caps) — match the app's existing icon set: check, x, folder-with-arrow (move), tag, trash, copy, alert-triangle, eye, grid, list.

## Files

| File | Purpose |
|---|---|
| `UC-074 Batch Select.html` | Interactive prototype — all flows, both layouts, previews on/off. Tweaks panel exposes rejected variants (hover/always entry, ring style) and a **Simulate batch failure** toggle for the error state. |
| `UC-074-batch-select.md` | Detailed design spec (same content as this README, design-review framing) |

**Exploring the prototype:** open the HTML → click **Select** → click rows, shift-click, ⌘-click, ⌘A, Esc → switch grid⇄list and toggle **Previews** off (favicon flips to checkbox) → run **Delete** (confirm → removal animation → trashbin badge → toast) and **Move** → in Tweaks enable **Simulate batch failure** for the rollback error.
