# Use Case: Investigate Form Label-Input Spacing

## Overview

**Use Case ID:** UC-058
**Use Case Name:** Investigate Form Label-Input Spacing
**Primary Actor:** Developer
**Goal:** Investigate and adjust the vertical spacing between form field labels and their associated inputs across the application, as the current gap appears too tight.
**Status:** Open

---

## Preconditions

- The application is running in development mode.
- At least one form is accessible (e.g. Create/Edit Bookmark dialog, Settings dialog, Login form).

## Main Success Scenario

1. Developer inspects all form components that render `<FormFieldCl>` or similar label+input pairs.
2. Developer identifies the current spacing value (gap, margin, or padding) between labels and inputs.
3. Developer compares the current spacing against design best practices (recommended minimum: 4–8px gap between label and input) and against the rest of the UI's spacing rhythm.
4. Developer adjusts the spacing in the shared `FormFieldCl` component (or at the layout level) so that the gap feels consistent and readable.
5. Developer verifies the change across all forms: bookmark create/edit, login, register, settings, share dialog, collection edit.
6. Developer verifies the change does not negatively affect mobile layouts or dialog overflow.

## Alternative Flows

### A1: Spacing Is Already Correct in Some Forms

**Trigger:** Some forms use different spacing than others.
**Flow:

1. Developer identifies the source of inconsistency (e.g. inline overrides vs. shared component defaults).
2. Developer consolidates all forms to use the shared component's spacing.
3. Use case continues at step 5.

### A2: Fix Causes Dialog Overflow on Mobile

**Trigger:** Increased spacing makes tall dialogs overflow on small screens.
**Flow:

1. Developer ensures dialogs have `max-h-[90dvh] overflow-y-auto` (see UC-056 §2.2).
2. Use case continues at step 5.

## Postconditions

### Success Postconditions

- The vertical gap between form labels and inputs is visually comfortable and consistent across the application.
- No form layout regressions on desktop or mobile.

### Failure Postconditions

- Spacing remains unchanged; developer documents findings for future review.

## Business Rules

### BR-058-1: Single Source of Truth

The label-to-input spacing MUST be defined in the shared `FormFieldCl` component. Individual forms MUST NOT override this spacing with ad-hoc margins.
