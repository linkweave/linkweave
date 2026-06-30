# Handoff: AI Suggested Tags (Add / Edit Bookmark)

## Overview

This adds an **AI tag-suggestion source** to the Add/Edit Bookmark dialog, alongside the existing rule-based auto-tagging. An on-device model (gemma 2B) reads the bookmark's title + URL and proposes tags **drawn only from the collection's existing tag vocabulary**. Suggestions from both sources appear together in a single **Suggested tags** section, visually grouped and labelled by source, pre-selected, and applied only when the user accepts.

The same change also **fleshes out tag selection** in the dialog: the free-text tag input is replaced with the search + create + checklist combobox from the Batch Tag Editor (UC-074a).

## About the Design Files

- `Auto-Tag Suggestions.html` — the current, full prototype: the complete Add Bookmark dialog with the new Suggested tags section, the combobox tag picker, and a bottom-left **Preview** panel (design scaffolding, *not* product chrome) to step through AI states.
- `Auto-Tag Suggestions v1 (section only).html` — earlier exploration of the section in isolation, with toggles for the three "AI vs rule" treatments and three loading animations. Kept for reference; the decisions below are already baked into the main file.

These are HTML/React prototypes — design reference, not production code. Recreate using the existing Vue codebase, components, and libraries.

## Fidelity

**High-fidelity** for the Suggested tags section and the tag combobox — match layout, grouping, the shimmer loader, chip states, and the accept/fold-in animation. The surrounding dialog fields (Title, URL, Description, Folder, Properties) are shown for context; keep the real dialog's existing implementation of those.

## Decisions locked in (from review)

- **AI vs rule distinction:** *Grouped.* Two labelled sub-groups inside the section — **"From your rules"** (⚡ bolt icon) and **"AI suggestions"** (✦ sparkle icon, violet accent `--ai: #a78bfa`).
- **Loading animation:** *Shimmering outline.* Outlined skeleton pills with a sweeping shimmer occupy the AI group while the model runs.
- **Accept model:** Reuse the existing auto-tag mechanism — chips are **toggleable and pre-selected**; nothing is written until the user clicks **Accept**.

---

## Layout

The section sits **directly below the Tags field** in the dialog body.

```
Tags  [ favorites ✕ ] [ + Find or add tags… ]
┌─ ✦ Suggested tags  (4)              🛡 On-device · gemma 2B ─┐
│  FROM YOUR RULES                                            │
│   ⚡ docs ✓                                                  │
│                                                             │
│  AI SUGGESTIONS                              ↻ Regenerate   │
│   ✦ rust ✓   ✦ async ✓   ✦ networking ✓                     │
│                                                             │
│  Only existing collection tags are suggested — nothing      │
│  new is created. Your manual tags are kept.                 │
│                                          [Dismiss] [Accept (4)]│
└─────────────────────────────────────────────────────────────┘
```

- **Header:** sparkle glyph + "Suggested tags" + a count badge (total selectable across both groups). Right side: an on-device privacy chip (shield icon).
- **From your rules:** rule-based chips. Render **instantly** (client-side, free).
- **AI suggestions:** the async group. Shows the shimmer while running; chips replace it on return. A **Regenerate** link appears once results are shown.
- **Footer:** **Dismiss** (collapse, keep nothing) and **Accept (N)** (apply selected, fold into Tags). Accept is disabled when nothing is selected.

### Chips

- Toggleable. **Selected** = filled checkbox + accent border/tint + full opacity; **deselected** = empty checkbox + dimmed (~0.6 opacity).
- Each chip carries its tag's **color dot** and a small **source icon** (⚡ rule / ✦ AI).
- Accent color differs by source: rule = primary blue `--color-primary`; AI = violet `--ai-strong`.

### De-duplication

If the **same tag is suggested by both** a rule and the model, show it **once, under "From your rules"** (rule attribution wins). The AI group lists only tags not already covered by rules. Don't show a tag that's already an applied tag on the bookmark.

### Suggestion constraint

The model must only return tags that **already exist in the collection's vocabulary** — it never invents new tags. (New tags are created exclusively through the manual combobox below.)

---

## States

| State | What shows |
|---|---|
| **idle** | Rule chips visible; AI group shows a **"✨ Suggest tags with AI"** button + one-line hint. (See trigger note — with auto-fire on, the dialog usually moves straight to *loading*.) |
| **loading** | Rule chips visible; AI group shows the shimmer skeleton pills. Accept is disabled. |
| **ok** | Both groups populated; Regenerate link present. |
| **empty** | AI group shows "No confident matches. Try again." Rule chips still show if any. |
| **collapsed** | Entire section replaced by a slim **"✨ Suggest tags"** pill under the Tags field (the retrieve affordance — see below). |

---

## Trigger & retrieval

### Initial trigger — auto-fire (with a caveat to settle in implementation)

- The AI call **auto-fires once** when **both Title and URL are present**, after a short **debounce (~700 ms)** of the last edit.
- It **re-arms when the URL changes** (editing the URL and settling triggers a fresh suggestion run).
- It must **not** re-fire for a URL the user has already **accepted or dismissed** (track the last-handled URL).
- The model call is **non-blocking** (BR-077): the dialog stays fully usable — save, manual tagging, everything — while it runs. Never block Save on suggestions.
- On dialog open, kick off a **silent model warm-up** so the first real run isn't paying cold-start latency in the foreground.

> ⚠️ **To square with the team during implementation:** confirm this auto-fire behavior is consistent with how **rule-based auto-tagging triggers today** (see next section), and confirm the cost/latency of auto-running gemma 2B on every new bookmark is acceptable (model load is ~1.6 GB / several seconds cold). If auto-fire proves too aggressive or expensive, the fallback is the **manual "Suggest tags with AI" button** (already designed as the `idle` state) — i.e. ship idle-by-default and make auto-fire a setting. The prototype's Preview panel includes an **"Auto-fire on title + URL"** toggle so both behaviors can be demoed side by side.

### How rule-based auto-tagging triggers today — **VERIFY**

This needs confirmation against the current implementation / UC-045 spec (the source doc was not available in the design project):

- The prototype assumes rules are **client-side and computed on demand** from the title/URL, surfaced as **suggestions** (chips) that are applied only on accept — i.e. the same accept model the AI suggestions use.
- **If that is correct:** the two sources coexist cleanly as designed — rules instant, AI async, both in the grouped section.
- **If instead** rule-based tags are **auto-applied silently today** (written without a review step), then the "From your rules" group should reflect **already-applied** tags (or be dropped from the suggestion UI), and only the AI group remains a true suggestion list. Decide which model is correct before building, and align the AI auto-fire to match it.

### Retrieving suggestions again

- After **Accept** or **Dismiss**, the section collapses to a slim **"✨ Suggest tags · on-device gemma 2B"** pill beneath the Tags field. Clicking it re-runs suggestions and re-expands the section. This is the primary "get them back" path.
- While results are shown, **Regenerate** (↻) re-runs the model for the current title/URL without collapsing.

---

## Accept behavior

- On Accept, every **selected** chip across both groups is added to the bookmark's applied tags.
- **Manual tags already on the bookmark are preserved** (BR-079) — Accept only *adds*, never replaces.
- Newly added chips **animate folding up** into the Tags field (the fold-in transition in the prototype).
- A brief **"Suggested tags applied"** confirmation appears in the dialog footer.
- The section then collapses to the retrieve pill.

---

## Tag selection (combobox) — fleshed out

Replaces the old free-text tag input. Reuses the **Batch Tag Editor (UC-074a)** picker pattern:

- The Tags field is a click target showing applied tags as removable color chips plus a **"+ Find or add tags…"** affordance.
- Clicking opens a dropdown with:
  - a **"Find or create a tag…"** search box (autofocused),
  - a **checklist** of the collection's existing tags (checkbox + color dot + name; checked = applied),
  - an inline **"Create '<query>'"** row when the typed text doesn't match an existing tag. **Enter** also creates. New tags get a round-robin color from the UC-074a palette: `['#2563b0','#0e7490','#7c3aed','#b45309','#be123c','#15803d','#9333ea','#475569','#c2410c','#0f766e']`.
- Applied tags sort to the top of the list. Clicking a row toggles membership. Esc / outside-click closes.

**Simplification vs the batch editor:** the batch editor uses **tri-state** checkboxes (some/all/none of the selected bookmarks have the tag). Editing a **single** bookmark has no partial state, so this is a plain **two-state** check/uncheck list. If exact parity with the batch editor is desired, swap in the tri-state control — but two-state is the correct reduction for one target.

---

## Edit-bookmark specifics

**No special behavior is required for the Edit case.** It works identically to Add:

- Existing tags on the bookmark are simply the **pre-populated applied tags**; suggestions still de-dupe against them (a tag already applied is not re-suggested).
- Auto-fire still applies — but since an edited bookmark already has Title + URL, treat the **initial open as already "handled"** for that URL (don't auto-fire on open for an unchanged URL; the user can use the **"✨ Suggest tags"** pill to ask explicitly). Auto-fire **does** re-arm if the user edits the URL.
- Accept/preserve/fold-in, retrieval, and Regenerate are all identical.

---

## Visual tokens (from the prototype)

- AI accent: `--ai: #a78bfa`, `--ai-strong: #8b5cf6` (deliberately distinct from `--color-primary: #3b82f6` and from tag-dot colors).
- Section background: a faint AI-tinted wash over the dialog background; AI-tinted border.
- Shimmer pills: transparent fill, AI-tinted outline, sweeping AI-tinted highlight (~1.15s loop).
- Respect `prefers-reduced-motion`: drop the shimmer sweep and fold-in animation to simple fades.

## Accessibility

- Suggestion chips are real toggle buttons (`aria-pressed`), keyboard-focusable, with visible focus rings.
- The shimmer group should expose an `aria-busy` / live-region "Generating suggestions…" announcement so screen-reader users aren't left in silence during the wait.
- Privacy chip text ("On-device · gemma 2B") should be readable, not icon-only.

---

## State management

State needed for the section (component-level or store, per existing conventions):

| State | Type | Notes |
|---|---|---|
| `appliedTags` | `string[]` | Tags on the bookmark. Pre-populated in Edit. Combobox and Accept mutate this. |
| `vocab` | `Record<string,color>` | Collection's existing tag → color map. Combobox "Create" adds to it. |
| `aiState` | `'idle' \| 'loading' \| 'ok' \| 'empty' \| 'collapsed'` | Drives which UI the section renders. |
| `selected` | `Set<string>` | Which suggestion chips are toggled on (pre-filled with all suggestions). |
| `ruleSuggestions` | `string[]` | Computed client-side from title/URL. Instant. |
| `aiSuggestions` | `string[]` | Returned by the model. Filtered to existing vocab + de-duped against rules + against `appliedTags`. |
| `lastHandledUrl` | `string \| null` (ref) | URL we've already auto-fired/accepted/dismissed for — gates auto-fire. |

**Key transitions:**
- title/URL settle (debounced) + both present + `aiState==='idle'` + `url !== lastHandledUrl` → `runAi()` → `loading` → `ok`/`empty`.
- Accept → add selected to `appliedTags` (additive), `lastHandledUrl = url`, `aiState='collapsed'`.
- Dismiss → `lastHandledUrl = url`, `aiState='collapsed'`.
- Retrieve pill / Regenerate → `runAi()`.
- URL edited to a new value → re-arm (clear gate) → auto-fire eligible again.

**Data fetching:** `runAi()` calls the on-device model with `{title, url, allowedTags: Object.keys(vocab)}`. Must be cancellable (debounce/abort on rapid edits) and must never block dialog Save.

---

## Design tokens

```
/* AI source accents (new) */
--ai:        #a78bfa;   /* sparkle icon, group label, borders, washes */
--ai-strong: #8b5cf6;   /* selected AI chip accent (checkbox fill / border) */

/* existing Chainlink dark theme (reference) */
--color-background:        #14161a;
--color-foreground:        #ededed;
--color-card:              #1e2028;
--color-primary:           #3b82f6;   /* selected RULE chip accent */
--color-secondary:         #252a32;   /* chip / hover fill */
--color-muted-foreground:  #a3a3a3;
--color-popover:           #22262f;   /* picker dropdown bg */
--color-border:            #363d49;
--color-input:             #252a32;
--color-ring:              #3b82f6;   /* focus ring */

/* new-tag color palette (round-robin, from UC-074a) */
#2563b0 #0e7490 #7c3aed #b45309 #be123c #15803d #9333ea #475569 #c2410c #0f766e
```

Sizing/shape used in the prototype: chip height 28px, radius 9999px (pill); section radius 12px; checkbox 16px / radius 5px; tag color dot 7px / radius 2px; shimmer loop ~1.15s; fold-in ~0.34s. Body font is the system UI stack; URL/mono fields use a monospace stack.

## Assets

No image assets. All icons are inline SVG (sparkle, bolt, check, x, shield, tag, search, plus, chevron, refresh, cube). Map these to the codebase's existing icon set (e.g. Lucide: `sparkles`, `zap`, `check`, `x`, `shield-check`, `tag`, `search`, `plus`, `chevron-down`, `refresh-cw`, `box`).

## Files

- `Auto-Tag Suggestions.html` — full Add Bookmark dialog with the new section + combobox (primary reference).
- `Auto-Tag Suggestions v1 (section only).html` — isolated section with toggles for the alternative "AI vs rule" treatments and loading animations (decisions already chosen; reference only).
- In the main project, the established dialog patterns live in `UC-067 Properties.html` (Edit dialog / Properties) and `UC-074 Batch Select.html` (the tag combobox this reuses).
