# Use Case: Gate Builds on CRAP Metrics

## Overview

**Use Case ID:** UC-111
**Use Case Name:** Gate Builds on CRAP Metrics
**Primary Actor:** Developer
**Goal:** Compute the CRAP index (Change Risk Anti-Patterns — cyclomatic complexity combined with code coverage) per method from the coverage data the build already produces, and fail the build when a change introduces a method whose CRAP exceeds a threshold — so that "complex and barely tested" code, the code most likely to break on the next change, cannot merge silently behind a passing bundle-wide coverage gate.
**Status:** Draft

## Traceability

**Maps to:** NFR-031
**Related:** UC-035 (ArchUnit tests), UC-092 (module boundaries) — the other static-quality gates; [docs/build-and-deploy.md](../build-and-deploy.md) — the gate inventory this joins; the fallow dead-code baseline (`.fallow-baseline.json`) — the ratchet-gate precedent this reuses.

---

## Background

The api build already produces exactly the data CRAP needs, and enforces none of it:

1. **The coverage gate averages risk away.** CI runs `jacoco:check` as a hard gate at **BUNDLE level, ≥ 70 % line coverage** (`docs/build-and-deploy.md`, `api/pom.xml`). A bundle-wide line average is blind to concentration: one new 25-branch method with a single happy-path test moves the bundle number by almost nothing, even though it is precisely the change most likely to regress later.
2. **Nothing relates complexity to coverage.** `quality.yml → backend-cpd` fails on copy-paste clones ≥ 100 tokens — duplication, not complexity. No check on the api side asks "is this method complex *and* under-tested?", which is the question that predicts change risk.

The CRAP index (Bob Evans, 2007; the metric behind PHPUnit's CRAP report) answers exactly that question per method:

```
CRAP(m) = complexity(m)² × (1 − coverage(m))³ + complexity(m)
```

A fully covered method's CRAP collapses to its complexity; an untested method's CRAP explodes quadratically with complexity. The standard scrutiny threshold is CRAP > 30.

Both inputs already exist in the coverage data the build records today. The combined unit-test + `@QuarkusTest` exec file (`target/jacoco-quarkus.exec`, written with `append=true`) is produced on every test run; one `jacoco:report` invocation — in the same place CI already invokes `jacoco:check` — renders it as the per-method XML (`target/site/jacoco*/jacoco.xml`) whose `COMPLEXITY` counter carries both operands: its total is the method's cyclomatic complexity, its `covered` portion the complexity exercised by tests. **No new instrumentation, agent, or server is required** — the gate is a report goal plus a parser over data the build already owns.

## Preconditions

- The `api` module is built with tests (`./mvnw verify` locally, or the CI `build` job), so the combined JaCoCo exec data exists.
- A CRAP baseline file is committed next to the module's other quality config (see BR-111-3), possibly empty on day one.

## Main Success Scenario

1. Developer opens a pull request (or runs the api quality gate locally, the same way CI does, before pushing).
2. Build compiles, runs the test suites; JaCoCo records the combined exec data as today.
3. The CRAP step renders the per-method XML from that data and computes CRAP for every method in `org.linkweave` (after the shared exclusions, BR-111-4).
4. The step prints the worst offenders (class#method, complexity, decision-point coverage, CRAP) to the build log and writes the full ranking as a build artifact.
5. Gate: every method with CRAP > 30 must appear in the baseline (BR-111-3). A new offender fails the build with an actionable message: class#method, file and line, complexity, coverage, CRAP, and threshold (BR-111-6).
6. Developer reduces the risk — tests for the uncovered decision points (coverage ↑ collapses the cubic term), or decomposes the method (complexity ↓) — and re-runs the build; it passes.

## Alternative Flows

### A1: Legacy Hotspot

**Trigger:** A method exceeds the threshold but is listed in the baseline (step 5).

**Flow:**

1. Gate passes; the report marks the method as carried debt with its baseline CRAP.
2. Use case ends — the debt is visible, not blocking.

### A2: Debt Removed

**Trigger:** A baselined method was deleted, renamed, or improved below the threshold (step 5).

**Flow:**

1. Gate fails on the **stale baseline entry** — the ratchet only turns one way (BR-111-3).
2. Developer removes the entry from the baseline in the same change.
3. Use case continues at step 5.

### A3: No Coverage Report

**Trigger:** The build ran without producing a JaCoCo XML report (tests skipped or failed) (step 3).

**Flow:**

1. The CRAP step treats "no report" exactly as `jacoco:check` does — it does not silently pass. With tests skipped it skips with an explicit log line; with failed tests the build is already red.
2. Use case ends.

### A4: Generated Code

**Trigger:** A reported method belongs to generated code (QueryDSL `Q*`, Hibernate metamodel `*_`/`_*`).

**Flow:**

1. The exclusions are the JaCoCo configuration's, verbatim (BR-111-4) — generated code never reaches the ranking.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The change introduces no method above the CRAP threshold that is not in the baseline.
- Carried (baselined) debt is enumerated in the report; the baseline is unchanged or smaller than before the change.

### Failure Postconditions

- The build is red and names the offender(s) with their numbers — never a bare exit code, and never a green build that silently carries new risk.

## Business Rules

### BR-111-1: Compute From the Existing Coverage Data; Coverage Means Decision-Point Coverage

CRAP inputs come from the JaCoCo data the build already records: complexity = the method's total `COMPLEXITY` counter; coverage = covered ÷ total `COMPLEXITY` (the share of the method's decision points exercised by tests), **not** line coverage. Both operands of the formula therefore describe the same thing — the method's branches — which is what the formula assumes. No new instrumentation; the per-method XML is rendered by a `jacoco:report` goal next to where `jacoco:check` already runs, never by re-running tests.

### BR-111-2: Risk Is Local, Not Averaged

The gate evaluates **methods**, the smallest unit the report offers. It complements — does not replace or weaken — the bundle-level `jacoco:check` line-coverage gate; both run.

### BR-111-3: A Ratchet, Never a Wall

Pre-existing offenders are grandfathered through a committed baseline file (same pattern as the fallow dead-code baseline). The baseline may only shrink: a build fails on a stale entry whose method no longer offends (A2), and CI must fail a change that adds baseline entries. Every entry records the method's CRAP at admission so the report can show carried debt and progress.

### BR-111-4: One Exclusion List

Generated-code exclusions are read from the JaCoCo configuration that already excludes QueryDSL `Q*` and the Hibernate metamodel (`*_`, `_*`). The CRAP step maintains no second list that could drift.

### BR-111-5: The Gate Lives in the Module Build

The check is wired where `jacoco:check` already runs — the api Maven build — not as a new CI workflow. Any change to the gate (threshold, baseline policy) follows the repo rule that gates go into the module's build/check, and [docs/build-and-deploy.md](../build-and-deploy.md) is updated when the gate lands.

### BR-111-6: Failures Are Actionable

A gate failure must name the class#method, its file and line, the complexity, the decision-point coverage, the CRAP value, and the threshold — enough to reproduce the number by hand from the report.

### BR-111-7: Deterministic and Cheap

The step parses an existing XML file; it runs no tests, spawns no servers, and completes in seconds, so it can run on every build without changing what the build means.

## Acceptance Criteria

1. A seeded method with cyclomatic complexity ≥ 10 and no test coverage fails the api build's CRAP gate, and the failure message contains class#method, complexity, coverage, CRAP, and the threshold.
2. The same method fully covered at its decision points passes: CRAP collapses to its complexity (only extreme complexity, c > 30, still trips when fully tested).
3. A pre-existing offender present in the baseline passes, and the report lists it as carried debt with its baseline CRAP.
4. Deleting a baselined method (or improving it below the threshold) fails the build until its baseline entry is removed; a change that adds baseline entries fails CI.
5. Methods from generated classes (QueryDSL `Q*`, Hibernate metamodel) never appear in the report or the failures.
6. The check adds no test re-run and under ~5 s to a full `verify`.

## Notes / Future Considerations

- **Threshold 30 is a starting point.** It comes from the original metric's guidance; tune it against the real distribution once the report exists, with data rather than taste.
- **Frontend CRAP deferred.** `vitest run --coverage` already emits per-file/function coverage, but the frontend has no agreed complexity source (candidates: typhonjs-escomplex, aggregating an ESLint/oxlint `complexity` rule). Revisit once the backend gate has settled; the same ratchet pattern applies.
- **No SonarQube.** A self-hosted server to obtain one metric is heavyweight for this project; the point of this use case is a zero-infrastructure gate over data the build already owns.
