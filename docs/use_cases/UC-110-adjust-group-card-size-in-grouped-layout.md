# Use Case: Adjust Group Card Size in the Grouped Layout

## Overview

**Use Case ID:** UC-110
**Use Case Name:** Adjust Group Card Size in the Grouped Layout
**Primary Actor:** User
**Secondary Actors:** Designer / Developer (produce the specification this use case demands)
**Goal:** Let a user change how large the folder cards ("boxes") are in the grouped bookmark layout, so the view can be tuned between "see many folders at once" and "see many bookmarks per folder" instead of being fixed at three columns of equally short cards.
**Status:** Draft — **specification required before implementation**

## Traceability

**Maps to:** FR-104
**Related:** UC-006 (View Bookmarks); UC-105 (View Preview on Compact Grouped View — previews anchored inside the card's content pane); UC-103 (Reorder Bookmarks Within Folder — drag auto-scroll inside the card body); UC-057 (Improve Mobile Responsiveness); C-008 (UI Layout / design system)

---

## Background — why this needs a spec first

The grouped layout's geometry is currently hard-coded in one component and adjustable by nobody:

- Card grid: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 items-start` — one column on small screens, two from `md`, three from `lg`, always (`frontend/src/components/bookmark/BookmarkGroupedLayout.vue:163`).
- Card body: `p-2 overflow-y-auto max-h-96` — every card's scrolling body is capped at the same 24 rem, whether it holds three bookmarks or three hundred (`frontend/src/components/bookmark/BookmarkGroupedLayout.vue:195`).

So "the boxes are the wrong size" can mean at least four different changes — fewer/more columns, taller/shorter cards, denser rows inside a card, or cards that size themselves to their content — and they have different persistence, responsive, and interaction consequences. Committing to one in code before deciding which problem is being solved would be guesswork. The deliverable of this use case is therefore **a specification** (a design handoff in `docs/design/`, in the style of the existing handoffs, plus the business rules folded back into this file), followed by implementation as a separate step.

## Preconditions

- The user is viewing a collection with the grouped layout active.
- The collection contains at least two root folders, so more than one card is visible.

## Main Success Scenario (post-specification, target behaviour)

1. User switches the collection to the grouped layout.
2. User opens the sizing control from the bookmark toolbar (alongside the layout toggle, sort menu, and previews toggle).
3. User picks a size — the axis and the value set are what the specification must define.
4. System re-lays out the group cards immediately, without reloading or losing scroll position within cards.
5. System remembers the choice for the next visit, at the scope the specification defines.

## Specification Scope — questions the spec must answer

The specification is not complete until each of these has a decided, written answer:

### S1: Which dimension is adjustable

Column count, card height (the `max-h` cap), row density inside a card, or content-sized cards? Is it one control or two (e.g. columns × height)? A single axis with a clear name is preferred over a matrix of knobs.

### S2: Discrete presets or continuous sizing

Named presets ("compact / comfortable / large") that stay inside the design system's spacing scale, or a free-form control (slider, drag-resize handle on the card edge)? Presets keep C-008's rhythm intact and are far cheaper to make responsive and accessible; drag-resize is more direct but needs its own persistence, keyboard equivalent, and touch story.

### S3: Where the control lives

Toolbar control next to the existing layout toggle (discoverable, per-view), collection settings (durable, owner-level), or the app settings dialog (global default)? Note that the layout choice itself already has both a per-user default and a per-collection override — the sizing control should not invent a third pattern.

### S4: Scope and persistence

Per user, per collection, or per device? The existing precedents are the per-user layout default persisted in local storage (`stores/ui.ts`) and the per-collection layout setting (`stores/collection.ts`). Is the size a *view preference* (device-local, like a zoom level) or a *collection property* (shared with everyone who opens that collection)? A shared collection makes this a real decision, not a detail.

### S5: Interaction with responsive breakpoints

If the user picks four columns, what happens at `sm`? The spec must define the relationship between the chosen value and the breakpoint ladder — a hard override, a maximum that breakpoints may reduce, or a minimum card width that determines the column count implicitly (`auto-fill` / `minmax`). It must also state the minimum usable card width below which a column is dropped regardless of the setting.

### S6: Interaction with the card body's behaviours

Each card body scrolls independently, auto-scrolls during drag-and-drop (UC-103), and anchors preview popups inside its content pane (UC-105). The spec must state what happens to each when the card grows or shrinks: whether a taller card still caps its body or grows with content, whether "no cap" is an allowed value, and how a preview popup behaves against a card that is now shorter than the popup.

### S7: Accessibility and zoom

The control must be keyboard-operable and labelled; the sizing must survive browser zoom and OS text scaling without clipping folder names or row actions. The spec must state the minimum touch-target and row height that any size setting must still honour.

### S8: Grid layout, and whether it is in scope

The grid layout has the same hard-coded `grid-cols-1 sm:2 lg:3` ladder for bookmark cards. The spec must state explicitly whether the sizing concept is shared with the grid layout later or deliberately grouped-only, so the control's naming and storage key do not have to change when that question is answered.

### S9: Verification

Which E2E and component tests prove the setting applies, persists, and survives a reload — and which existing grouped-layout tests (drag-and-drop, previews) must keep passing at every size.

## Alternative Flows

### A1: Narrow Viewport

**Trigger:** Viewport is too narrow for the chosen size (step 4).
**Flow:**

1. System falls back to the largest size the viewport supports; the stored preference is unchanged and re-applies on a wider viewport.

### A2: Single Folder

**Trigger:** The collection has one root folder, so there is one card (step 4).
**Flow:**

1. Sizing still applies (the card's height/density), but the control indicates that column count has no effect here — or is hidden, per the spec's decision in S1.

### A3: Layout Switched Away and Back

**Trigger:** User switches to list or grid and returns to grouped.
**Flow:**

1. The stored size is re-applied; it is not reset by the round trip.

## Postconditions

### Success Postconditions

- A written specification exists that answers S1–S9, reviewed before any implementation work starts.
- Once implemented: the user can change group-card size from the UI, the choice persists at the specified scope, and drag-and-drop, per-card scrolling, and preview popups behave identically at every size.

### Failure Postconditions

- The specification records why a dimension was rejected, so the question is not re-opened from scratch later.

## Business Rules

### BR-110-1: No Implementation Before the Specification

The sizing behaviour is specified — S1 through S9 answered and reviewed — before code is written. This use case exists because the desired change is not yet known; guessing in CSS and iterating in review is the outcome it is meant to prevent.

### BR-110-2: One Sizing Concept, Reusing Existing Preference Patterns

Whatever scope is chosen, it reuses the established layout-preference mechanism (per-user default plus per-collection override) rather than adding a parallel storage path. One key, one place.

### BR-110-3: Geometry Belongs to the Layout Component

Card geometry stays in the grouped-layout component and the design tokens it uses. Sizing must not leak into `GroupedBookmarkRow` or the preview popup as ad-hoc overrides — the same mistake as inline form-spacing overrides (UC-058 BR-058-1).

### BR-110-4: Sizing Never Breaks the Card's Behaviours

At every permitted size, per-card scrolling, drag-and-drop auto-scroll (UC-103), row actions, and preview anchoring (UC-105) keep working, and the sticky toolbar is never obscured. Any size that cannot satisfy this is not a permitted size.

### BR-110-5: Responsive Floors Are Non-Negotiable

The setting is bounded by a minimum usable card width and row height. Mobile viewports stay single-column regardless of the stored preference (UC-057).

## Notes / Future Considerations

- The natural first probe is whether the fixed `max-h-96` body cap or the fixed three-column ladder is the actual irritant — they produce the same complaint ("the boxes are wrong") from opposite causes. Answering S1 with a real collection open, rather than in the abstract, is the cheapest way to close most of this list.
