# Handoff: Page Screenshot Previews
## FR-080 — Capture treatments, states & controls

**Prototype:** `Screenshot Previews.html` — open in a browser to compare all treatments, states, and controls side by side. Design reference only; recreate in the existing codebase.

> Plan: `docs/plans/screenshot-previews.md`. This document covers the **recommended** path only — rejected variants are noted inline so they aren't re-explored.

---

## Overview

Each bookmark can show a screenshot of its page. Default posture is **Balanced**:

| Layout | Treatment |
|---|---|
| Grid | 16:9 **cover image** at the top of the card |
| List | 124px **leading thumbnail** at the left of the row |
| Grouped | inherits the list thumbnail |

Captures are produced server-side and are **async**; the worker **follows redirects and captures whatever the page renders** — including a login screen or a paywalled article. We deliberately do **not** detect or special-case login walls / paywalls; the honest screenshot is more useful than a guessed state, and it sheds a pile of fragile detection logic. The only non-happy terminal state is a **genuine capture failure** (timeout, X-Frame deny, worker error) → the fallback tile.

---

## Recommended treatments

### Grid — cover + favicon overlay  ✓

The 16:9 capture fills the card top. The favicon sits in a **floating chip overlapping the bottom-left of the cover**, so site identity survives even when the capture is generic, stale, or a fallback tile.

- Cover: `aspect-ratio: 16 / 9`, `background: #0e1014` (placeholder color before image paint)
- Favicon chip: 22×22 favicon in a 4px `--card` pad, `border-radius: 8px`, `box-shadow: 0 2px 8px rgba(0,0,0,.4)`, positioned `left: 10px; bottom: -12px`
- Card body padding-top accounts for the −12px overhang (`18px`)

> Rejected: **B · inline favicon** (favicon in the title row) and **C · browser-chrome frame** (mac-style title bar over the capture). Chrome framing wastes ~28px vertical per card and competes with the real toolbar. Overlay chip is the only implemented grid variant.

### List — 124px leading thumbnail  ✓

Thumbnail on the left edge of the row, text to the right. Keeps list density high; the image is a recognition aid, not the hero.

- Thumb: `width: 124px; aspect-ratio: 16/9`, `border-radius: 6px`, `1px solid --border`, `overflow: hidden`
- Favicon stays inline in the title row (16px) — the thumbnail is recognition, the favicon is identity

> Rejected: **160px** (too heavy, pushes description to one line) and **trailing thumbnail** (right-aligned image fights the ⋯ menu and ragged text). 124px leading is the implemented list variant.

---

## Controls

Two controls govern visibility. They compose: the per-collection setting **overrides** the global toggle.

> **Roles (don't collapse these into one):** the toolbar toggle is a *reading-mode* control — a cheap, in-context "show previews right now," same instinct as the grid/list switch next to it. The per-collection setting is an *optional lock* for collections where previews should always (or never) show regardless of the reader's mode — e.g. a large archive where they'd just be noise. Default for every collection is `inherit`, so the toolbar toggle is the only control most users ever touch.

### 1 — Toolbar toggle (global)

A **Previews** toggle button sits in the list toolbar, immediately left of the existing layout (grid/list) segmented control.

Appearance:
- 30px tall, `border-radius: 7px`, icon (eye / eye-off) + "Previews" label, `font-size: 12px; font-weight: 600`
- **On:** `border: 1px solid color-mix(in oklab, var(--primary) 90%, black)`, `background: color-mix(in oklab, var(--primary) 13%, transparent)`, text `#9cc0ff`
- **Off:** `border: 1px solid var(--border)`, transparent background, text `--muted-foreground`

```css
.preview-toggle {
  display: inline-flex; align-items: center; gap: 6px;
  height: 30px; padding: 0 10px; border-radius: 7px;
  font-size: 12px; font-weight: 600; cursor: pointer;
  border: 1px solid var(--border);
  background: transparent; color: var(--muted-foreground);
}
.preview-toggle.on {
  border-color: color-mix(in oklab, var(--primary) 90%, black);
  background: color-mix(in oklab, var(--primary) 13%, transparent);
  color: #9cc0ff;
}
```

Behavior:
- Toggles capture visibility for the current view
- **Off** → cards/rows fall back to the existing favicon-only layout (current shipped behavior — no regression)
- Persisted per view, remembered across sessions

```
<user-prefix>:previewsEnabled   →  "true" / "false"   (default: "true")
```

### 2 — Per-collection setting (override)

In collection settings (alongside default sort / default layout), a **Page previews** radio group:

| Option | `value` | Behavior |
|---|---|---|
| Use global default | `inherit` | Follows the toolbar toggle (default) |
| Always show | `on` | Force previews on for this collection |
| Never show | `off` | Compact text rows only, ignores global toggle |

```css
.preview-opt {
  display: flex; align-items: flex-start; gap: 9px;
  padding: 9px 10px; border-radius: 7px; cursor: pointer; text-align: left;
  border: 1px solid var(--border); background: var(--secondary);
}
.preview-opt[aria-checked="true"] {
  border-color: color-mix(in oklab, var(--primary) 90%, black);
  background: color-mix(in oklab, var(--primary) 10%, transparent);
}
```

```
<user-prefix>:collection:<id>:previews   →  "inherit" | "on" | "off"   (default: "inherit")
```

### Resolution logic

```
function previewsVisible(collection, globalToggle) {
  const c = collection.previews ?? "inherit";
  if (c === "on")  return true;
  if (c === "off") return false;
  return globalToggle;          // "inherit"
}
```

---

## States

A capture occupies one of these states. The state is per-bookmark and drives what renders inside the 16:9 frame. Favicon chip overlay is present in **every** state.

| State | Trigger | Render |
|---|---|---|
| **Capturing** | Queued / in flight (new bookmark, or manual refresh) | Shimmer fill + spinner + "Capturing preview…" |
| **OK** | Capture returned an image (incl. a login page or paywall gate — shown as-is) | The image |
| **Stale** | Capture older than refresh threshold | The cached capture + a `3 mo ago` badge; hover reveals a centered **Refresh preview** button |
| **Fallback tile** | Genuine failure — timeout, X-Frame deny, worker error | Radial gradient from favicon color + large 38px favicon + domain in monospace |

### Badges

Floating top-right pill over the cover, shared style:

```css
.preview-badge {
  position: absolute; top: 10px; right: 10px;
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 8px; border-radius: 9999px;
  font-size: 10.5px; font-weight: 600;
  background: rgba(20,22,26,.78);
  border: 1px solid var(--border);
  backdrop-filter: blur(4px);
}
```

Only the **Stale** badge is in scope (`3 mo ago`, tone `#9aa3b2`). No login / paywall badges.

### Never render

- A broken-image icon. Any failure resolves to the **Fallback tile** — never a browser default.
- An empty/blank frame. Capturing always shows the shimmer.

### Capture lifecycle

```
bookmark added / refresh requested
        │
        ▼
   [Capturing]  ──image returned──▶  [OK]  ──(age > threshold)──▶  [Stale]
        │              (incl. login / paywall pages, shown as-is)     │
        │                                                  hover → Refresh
        └── timeout / X-Frame deny / worker error ─▶ [Fallback tile]   │
                                              ◀───────────────────────┘
```

- The worker **follows redirects** and captures the final rendered page. No content-gating heuristics.
- **Refresh** is available from (a) the ⋯ menu on any card, and (b) the hover button on a Stale capture. It re-queues → returns to `Capturing`.
- Stale threshold is a backend constant — recommend **90 days**. Surface the relative age, not the absolute date.

---

## Hover-to-enlarge

In **list** view, hovering a row floats a 300px full-size capture above the leading thumbnail.

- Anchored `top: 0; left: 14px`, `transform-origin: top left`
- Enter: `opacity 0→1`, `transform: scale(.96) translateY(6px) → scale(1) translateY(0)`, `transition: .16s`
- `pointer-events: none` — the zoom never intercepts the row click; clicking the row still opens the link
- Grid view does not need this (the cover is already large)

```css
.zoom-pop {
  position: absolute; top: 0; left: 14px; width: 300px; z-index: 5;
  opacity: 0; transform: scale(.96) translateY(6px); transform-origin: top left;
  transition: opacity .16s, transform .16s; pointer-events: none;
  border-radius: 10px; overflow: hidden;
  border: 1px solid var(--border); box-shadow: 0 18px 50px rgba(0,0,0,.55);
}
.row:hover .zoom-pop { opacity: 1; transform: scale(1) translateY(0); }
```

---

## Color & sizing reference

| Token | Value | Usage |
|---|---|---|
| Capture placeholder | `#0e1014` | Frame background before image paint |
| `--ss` (teal) | `oklch(64% 0.10 188)` dark | Only if previews ever surface in a saved-search context — not used by previews themselves |
| Favicon chip overlap | `bottom: -12px` | Card body padding-top must be `18px` to clear it |
| Cover / thumb ratio | `16 / 9` | All captures, every layout |
| List thumb width | `124px` | Leading |
| Grid card width | `270px` | Reference; real grid is responsive |

---

## Accessibility

- Toggle and radio options are real buttons with `aria-pressed` / `aria-checked`
- Every capture `<img>` needs `alt` = page title (the visible title is decorative-adjacent, but the image is the page)
- The fallback-tile frame is decorative → `alt=""` / `aria-hidden`; the favicon + domain carry identity
- Refresh button hit target ≥ 44px even though visually smaller

---

## Files

| File | Purpose |
|---|---|
| `Screenshot Previews.html` | Comparison canvas — treatments, states, controls |

### Exploring the prototype
1. Open `Screenshot Previews.html`
2. **Grid** section — compare A (overlay ✓) vs B / C; "In context" shows a 2-up grid
3. **List** section — compare 124px ✓ vs 160px vs trailing
4. **States** section — capturing, the generated fallback tile; hover the **Stale** card to reveal Refresh
5. **Controls** section — click **Previews** to toggle on/off; pick a per-collection option; hover the list row to see the enlarge pop
