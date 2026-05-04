# Use Case: Add ArchUnit Tests

## Overview

**Use Case ID:** UC-035   
**Use Case Name:** Add ArchUnit Tests   
**Primary Actor:** Developer   
**Goal:** Ensure the project's architecture and coding standards are automatically enforced through ArchUnit tests, using the `esc` project as a reference.   
**Status:** Open   

## Traceability

**Maps to:** FR-039

---

## Preconditions

- The developer has access to the `chainlink` and `esc` codebases.
- ArchUnit dependency is available in the `api` module.

## Main Success Scenario

1. Developer identifies architectural constraints from the `AGENTS.md` and `esc` reference project.
2. Developer sets up common ArchUnit utilities (e.g., `ArchConst`, `ArchUtil`) in the test source.
3. Developer implements tests to enforce layering: **Entities → Repository → Service → Resource**.
4. Developer implements tests to enforce naming conventions for Repositories, Services, and Resources.
5. Developer implements tests to ensure only Resources and Services use `@Transactional`.
6. Developer implements tests for JPA best practices (e.g., default constructors, private fields).
7. Developer implements tests for authorization compliance (e.g., no access checks in repositories/services).
8. System reports any violations during the build process.
9. Developer resolves violations or documents exceptions.

## Alternative Flows

### A1: Existing Violation Found

**Trigger:** ArchUnit tests fail due to an existing architectural violation (step 8).
**Flow:**

1. System provides details of the violation (class, location, rule).
2. Developer evaluates if the violation is a bug or if the rule needs an exception.
3. Developer either refactors the code to comply or adds an `@ArchIgnore` (or similar) if justified.
4. Use case continues at step 8.

## Postconditions

### Success Postconditions

- ArchUnit tests are part of the `api` test suite.
- Architectural constraints are automatically checked during the build.
- Documentation of architectural rules is implicitly provided through tests.

### Failure Postconditions

- No ArchUnit tests are added.
- Architectural violations go undetected.

## Business Rules

### BR-067: Layering Integrity
Layers must strictly follow the one-way dependency: **Entities ← Repository ← Service ← Resource**. Circular dependencies are forbidden.

### BR-068: Naming Standards
- Repositories must be suffixed with `Repo`.
- Services must be suffixed with `Service`.
- Resources must be suffixed with `Resource`.
- JSON DTOs must be suffixed with `Json`.

### BR-069: Transaction Boundaries
Transactional management is exclusively limited to the Service and Resource layers. Entities and Repositories must not have `@Transactional` annotations.

### BR-070: Authorization Encapsulation
Business logic and persistence layers must never perform manual authorization checks. All such logic must reside in the Resource layer using `AuthorizationService`.

### BR-071: JPA Compliance
All JPA entities must extend `AbstractEntity`, have a default (no-args) constructor, and private fields accessed via Lombok getters/setters.
