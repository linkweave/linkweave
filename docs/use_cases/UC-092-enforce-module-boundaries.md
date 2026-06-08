# Use Case: Enforce Module Boundaries and Prevent Circular-Dependency Regressions

## Overview

**Use Case ID:** UC-092
**Use Case Name:** Enforce Module Boundaries and Prevent Circular-Dependency Regressions
**Primary Actor:** Developer (with the CI pipeline as supporting actor)
**Goal:** Keep the frontend free of *new* circular dependencies and architecture-boundary violations, so the existing 20-cycle backlog can be paid down without silently growing.
**Status:** Draft

## Traceability

**Maps to:** NFR — Maintainability / Architecture integrity
**Related:** UC (fallow quality checks in `.gitea/workflows/quality.yml`)

---

## Background

A `fallow health` analysis currently reports **20 circular dependencies**, which fall into three buckets:

1. **Barrel-file cycles** — components import from `components/ui/index.ts`, which re-exports them (e.g. `ConfirmDialog.vue`, `SettingsDialog.vue`, `UserMenuCl.vue`, `LanguageSwitcherCl.vue`).
2. **Router ↔ store cycles** — `router/index.ts` imports views/stores that import `stores/auth.ts` / `stores/collection.ts`, which in turn reach back to the router.
3. **Store ↔ store cycles** — `stores/bookmark.ts` ↔ `stores/folder.ts`, `stores/bookmark.ts` ↔ `stores/tag.ts`, `stores/auth.ts` ↔ `stores/collection.ts`.

These are **accepted as a baseline** (`frontend/.fallow-baseline.json`) so they do not fail CI today, but nothing yet prevents a 21st cycle from being introduced. This use case closes that gap.

## Preconditions

- The frontend builds and the fallow quality job runs in CI.
- A committed fallow baseline exists at `frontend/.fallow-baseline.json`.

## Main Success Scenario

1. Developer opens a pull request that changes frontend module imports.
2. CI runs the boundary/cycle check as part of the quality job.
3. The check compares the current dependency graph against the accepted baseline.
4. No new circular dependency or boundary violation is found beyond the baseline.
5. CI reports the boundary check as passing.
6. Developer merges the pull request.

## Alternative Flows

### A1: A New Cycle Is Introduced

**Trigger:** The pull request adds an import that creates a circular dependency not present in the baseline (step 4).
**Flow:**

1. The boundary check reports the new cycle, naming the files involved.
2. CI marks the boundary check as failed (once promoted from report-only to enforced).
3. Developer removes the offending import or restructures the dependency.
4. Use case continues at step 4.

### A2: A Backlog Cycle Is Fixed

**Trigger:** The pull request removes one of the baselined cycles (step 4).
**Flow:**

1. Developer regenerates the baseline so the ratchet tightens to the new, lower floor.
2. Developer commits the updated `frontend/.fallow-baseline.json`.
3. Use case continues at step 5.

### A3: Adopt a Dedicated Boundary Tool

**Trigger:** The team decides fallow's circular-dependency detection is insufficient and wants explicit, declared architecture boundaries (e.g. "stores may not import the router", "components may not import their own barrel").
**Flow:**

1. Developer evaluates a dedicated tool — **Sheriff** (`@softarc/sheriff`) for declared module boundaries and `forbidden`/cyclic rules, or **dependency-cruiser** / **madge** for graph rules and cycle detection.
2. Developer adds the tool's config encoding the desired boundaries and a `no-circular` rule.
3. Developer wires the tool into the quality job alongside (or instead of) the fallow cycle check.
4. Use case continues at step 2.

## Postconditions

### Success Postconditions

- The merged change introduces no circular dependency or boundary violation beyond the accepted baseline.
- The dependency-cycle count is non-increasing over time (ratchet holds or tightens).

### Failure Postconditions

- A regression is surfaced in CI and the offending import is identified before merge.
- No new cycle reaches `main` once the check is enforced.

## Business Rules

### BR-092-1: Non-Increasing Cycle Count

The number of circular dependencies must never increase relative to the committed baseline. New cycles are treated as regressions.

### BR-092-2: Baseline Tightens, Never Loosens

The baseline may only be regenerated when cycles are *removed*. It must not be regenerated to absorb a newly introduced cycle.

### BR-092-3: Tooling Choice

Enforcement may be implemented with fallow (its `boundaries` config plus the regression baseline) or a dedicated tool (Sheriff, dependency-cruiser, or madge). Whichever is chosen must run in CI and fail on new violations once promoted from report-only to enforced.

---

## Notes / Next Steps

- **Quick wins first:** the barrel-file cycles (bucket 1) are the cheapest to remove — have components import siblings directly instead of through `components/ui/index.ts`.
- **Then the architectural tangle:** break the store → router edge (bucket 2), e.g. by moving navigation out of stores or injecting the router lazily.
- Once cycles are reduced, **promote the boundary check from report-only to enforced** (drop `continue-on-error` in `quality.yml`) and re-baseline.
