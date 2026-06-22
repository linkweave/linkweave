# ArchUnit Tests — Status & Findings

Implements **UC-035** (FR-039). Tests live under
`api/src/test/java/org/linkweave/api/shared/archunit/`.

Ported 1:1 from the `esc` reference project, adapted to linkweave's package
layout. Three esc-only suites were dropped (no analog in linkweave): `MetricsTest`
(no micrometer), `OptimisticLockingArchRulesTest` (no optimistic-locking infra),
and the `RequireTenantClaimMatcherDecision` predicate (linkweave isn't multi-tenant).

## Test status

| Test                                | Result   | Failing rules | Real findings                                                                                |
|-------------------------------------|----------|---------------|----------------------------------------------------------------------------------------------|
| `ArchConst`, `ArchUtil`             | helper   | —             | `APP_PACKAGE` widened to `org.linkweave` to cover `org.linkweave.infrastructure.*`           |
| `ConfigTest`                        | 1 fail   | 1 / 5         | 9 `@ConfigProperty` fields in favicon code bypass `ConfigService`                            |
| `ExceptionsTest`                    | green    | 0 / 4         |                                                                                              |
| `GeneralTest`                       | green    | 0 / 6         |                                                                                              |
| `IdClassTest`                       | 2 fail   | 2 / 3         | 20 violations; mostly framework-level false positives (`AbstractEntity.id`, `ExceptionId.id`) |
| `JaxResourceTest`                   | 5 fail   | 5 / 9         | Resources missing `@RolesAllowed`/`@PermitAll`/`@RateLimit`; entities returned/accepted directly |
| `JpaTest`                           | 4 fail   | 4 / 13        | FK/IDX/UC naming conventions; nullable columns missing `@Nullable`                           |
| `JsonDtoTest`                       | 1 fail   | 1 / 5         | 46 DTO fields missing `@Schema`                                                              |
| `LayeringTest`                      | 4 fail   | 4 / 9         | 18 spots where Resource layer reaches into Entity getters/Repos                              |
| `NamingTest`                        | green    | 0 / 7         |                                                                                              |
| `PreventEnvironmentAccessTest`      | green    | 0 / 1         |                                                                                              |
| `TransactionsTest`                  | 2 fail   | 2 / 5         | 6 `@Transactional` annotations on Resources/Jobs; 1 on non-public method                     |
| `UnifiedNullableAnnotationsTest`    | green    | 0 / 1         | Reduced to lombok-only check (no jetbrains-annotations dep on classpath)                     |

**Totals:** 12 test classes, ~68 rules, **19 failing** = real architectural debt or
false positives needing rule tuning.

## Findings by category

### A. Authorization & API protection (highest priority)
From `JaxResourceTest`:
- 11 `@JaxResource` classes lack class-level `@RolesAllowed`/`@PermitAll`.
- 52 public resource methods lack method-level `@RolesAllowed`/`@PermitAll`.
- 12 resources lack `@RateLimit`.

**Risk:** an unauthenticated path can ship by accident. CLAUDE.md already mandates
`AuthorizationService` checks in the Resource layer; the JAX-RS-level guards are the
second line of defense.

### B. Resource layer leaking entities
From `LayeringTest` (4 rules) and `JaxResourceTest`:
- 18 call sites where a `*Resource` calls `entity.getCollection()` etc. directly.
- 5 resource methods return entities (or collections of them) instead of `*Json` DTOs.
- 1 resource method takes an entity-shaped param.

**Risk:** breaks the Entities → Repository → Service → Resource discipline; couples
HTTP layer to schema; can leak fields not meant for the wire.

### C. Transaction boundaries
From `TransactionsTest`:
- `AuthResource.oidcLogin`, `AuthResource.register`, `TimeTravelResource.{travelTo,reset,status}`,
  `FaviconCacheCleanupJob.scheduledRun` annotated `@Transactional` directly.
- `FaviconCacheCleanupJob.scheduledRun` is non-public + `@Transactional` (CDI proxy
  doesn't apply — silently no-op transaction).

**Risk:** transaction lifetimes leak outside the Service layer; the non-public case
is a real bug (proxy doesn't fire).

### D. Configuration discipline
From `ConfigTest`:
- 9 fields in `FaviconCacheCleanupJob`, `FaviconCacheService`, `FaviconFetcherService`
  inject `@ConfigProperty` directly instead of going through `ConfigService`.

**Risk:** config gets sprinkled across the codebase, hard to discover/document/test.

### E. JPA hygiene (`JpaTest`)
- 3 FK columns don't follow `fk_<owner>_<reference>` naming.
- 1 entity table missing IDX naming, 1 missing UC naming.
- 20 nullable columns lack `@Nullable` (or type-use `@Nullable`).

**Risk:** schema review/migrations harder; nullability invisible to checkerframework.

### F. OpenAPI completeness (`JsonDtoTest`)
- 46 DTO fields lack `@Schema` documentation.

**Risk:** generated `openapi.json` is sparse; FE typings less informative.

### G. ID type safety (`IdClassTest`)
- 15 fields named `id`/`*Id` not typed `ID<T>`; 5 method params likewise.
- **Most are framework-level false positives**: `AbstractEntity.id` IS the id; same for
  `ExceptionId.id`, `AppException.id`, `User.keycloakId` (a Keycloak UUID, not a linkweave ID).
- A few may be real: `CleanupSuggestionJson.id`, `MoveToTrashJson.collectionId` —
  these are JSON DTOs and arguably *should* be `String` on the wire, so the rule
  needs to exclude DTOs or these need a carve-out.

## Configuration changes already applied

- `APP_PACKAGE` in `ArchConst` was widened from `org.linkweave.api` to
  `org.linkweave` so the rules see `org.linkweave.infrastructure.*`.
- `LayeringTest` predicates (`SHARED`, `STARTER_INFRASTRUCTURE`, `DATABASE_LAYER`)
  were rewritten to use linkweave's actual package paths
  (`org.linkweave.api.shared..`, `org.linkweave.infrastructure..`).
- `archunit-junit5` 1.4.1 added to `api/pom.xml`.
