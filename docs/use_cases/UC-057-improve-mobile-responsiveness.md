# Use Case: Improve Mobile Responsiveness

## Overview

**Use Case ID:** UC-057
**Use Case Name:** Improve Mobile Responsiveness
**Primary Actor:** Developer
**Goal:** Fix all critical and important mobile responsiveness issues identified in the mobile viability audit so that Chainlink is usable on 375px-wide screens (standard mobile).
**Status:** In Progress

## Traceability

**Design document:** [Mobile Viability Report](../mobile-viability/MOBILE-VIABILITY-REPORT.md)

---

## Preconditions

- The mobile viability audit has been completed and documented with screenshots.
- A 375×812 viewport (iPhone SE / standard mobile) is used as the reference breakpoint.

## Main Success Scenario

1. Developer reviews the [Mobile Viability Report](../mobile-viability/MOBILE-VIABILITY-REPORT.md) which catalogs all known mobile issues with screenshots and proposed fixes.
2. Developer implements fixes in the priority order defined in the report's implementation plan (§4):
   - Dialog overflow fix (`DialogCl.vue`)
   - Main layout padding (`MainLayout.vue`)
   - Header spacing (`HeaderCl.vue`, `UserMenuCl.vue`)
   - Trashbin responsive buttons (`TrashbinView.vue`)
   - Collection manage overflow (`CollectionManageView.vue`)
   - Share dialog stacking (`ShareCollectionDialog.vue`)
   - Touch target sizes (4 files)
   - Drag-and-drop mobile handling (3 files)
3. Developer captures new screenshots at 375×812 for each fixed area.
4. Developer updates the Mobile Viability Report with "after" screenshots and marks issues as resolved.
5. All existing desktop tests and layouts remain unchanged.

## Alternative Flows

### A1: Fix Introduces Desktop Regression

**Trigger:** A responsive change breaks the desktop layout.
**Flow:

1. Developer adjusts the fix to use responsive prefixes (`sm:`, `lg:`) so that the change applies only on mobile viewports.
2. Use case continues at step 3.

### A2: Issue Requires Design Decision

**Trigger:** A proposed fix in the report is ambiguous or has multiple approaches (e.g. drag-and-drop on touch).
**Flow:

1. Developer documents the trade-offs and chooses an approach.
2. Use case continues at step 3.

## Postconditions

### Success Postconditions

- All critical issues from the Mobile Viability Report are resolved.
- The app is fully usable on a 375px-wide viewport.
- Desktop layout is unaffected.
- The Mobile Viability Report contains both "before" and "after" screenshots.

### Failure Postconditions

- Some issues remain open; the report is updated to reflect partial progress.

## Business Rules

### BR-057-1: Mobile-First Responsive Prefixes

All responsive fixes MUST use Tailwind's `sm:` / `lg:` prefixes rather than JavaScript-based viewport detection where possible, to keep the approach consistent with the existing codebase.

### BR-057-2: No Feature Removal

Mobile fixes MUST NOT remove functionality. If a feature cannot be displayed on mobile (e.g. drag-and-drop), an alternative interaction must be provided.
