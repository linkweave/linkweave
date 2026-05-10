# ArchUnit Findings — Remediation Plan

Companion to `archunit-findings.md`. Order is roughly **highest risk first, smallest
blast radius first within a tier**, so each step keeps the suite trending greener.

## Tier 0 — Real bugs (fix immediately)

### 0.1 `FaviconCacheCleanupJob.scheduledRun` is `@Transactional` but non-public
- CDI proxy can't intercept non-public methods → the `@Transactional` is silently a no-op.
- **Fix:** make the method `public` (preferred) or move the transactional unit into
  a collaborator service that gets called from the scheduled method.
- Verifies: `TransactionsTest.transactional_only_public`.

## Tier 1 — Authorization gaps (security)

### 1.1 Add class-level `@RolesAllowed` / `@PermitAll` to all `@JaxResource` classes
- 11 resources affected (`AuthResource`, `AutoTagRuleResource`, `BookmarkResource`,
  `TagResource`, `ExportResource`, `FolderResource`, `ImportResource`,
  `CleanupSuggestionResource`, `CollectionResource`, `FaviconResource`,
  `TrashbinResource`).
- Pick the right default per resource (most should be `@RolesAllowed("user")`;
  `AuthResource` partly `@PermitAll`).
- Verifies: `JaxResourceTest.ensure_permission_are_defined_on_endpoints`.

### 1.2 Add method-level guards on the 52 unguarded public methods
- After 1.1 lands, re-run the suite — many of these inherit class-level coverage.
- For the genuine outliers, decide per-method.
- Verifies: `JaxResourceTest.ensure_permission_are_defined_on_endpoint_methods`.

### 1.3 Add `@RateLimit` to `@JaxResource` classes (12)
- Pick a sensible default (e.g. per-IP, generous), tune per-resource later.
- Smoke test: hit endpoints in dev and confirm no regression.
- Verifies: `JaxResourceTest.enforce_rate_limit_on_all_methods`.

## Tier 2 — Layering violations (architecture integrity)

### 2.1 Eliminate `@Transactional` on Resources & Jobs
- Move the transactional unit into a Service. Resources stay thin.
- Affected: `AuthResource.oidcLogin`, `AuthResource.register`,
  `TimeTravelResource.{travelTo,reset,status}`, `FaviconCacheCleanupJob.scheduledRun`
  (already touched in 0.1 — just relocate the `@Transactional`).
- Verifies: `TransactionsTest.transactional_boundary_on_methods`.

### 2.2 Stop returning entities from Resources (5 sites)
- Map to `*Json` DTOs in the resource method; introduce a mapper if needed.
- Verifies: `JaxResourceTest.dtos_returned_by_resource_methods_must_be_in_json_DTOs`.

### 2.3 Fix `TimeTravelResource.travelTo` param type
- Currently takes the raw entity-shaped `TimeTravelRequest` inner class; convert
  to a proper `*Json` DTO.
- Verifies: `JaxResourceTest.params_taken_by_resource_methods_must_be_json_DTOs_or_builtin_types`.

### 2.4 Stop calling Entity getters / Repos from Resources (18 sites)
- Push the access into the relevant Service. Most call sites are
  `entity.getCollection()` for an authorization check that AuthorizationService
  should already cover — confirm coverage and drop the call, or pull the value out
  through the service.
- Verifies: `LayeringTest.test_layer_boundaries_entities_*`.

## Tier 3 — Configuration discipline

### 3.1 Move favicon `@ConfigProperty` fields into `ConfigService`
- 9 fields across `FaviconCacheCleanupJob`, `FaviconCacheService`,
  `FaviconFetcherService`. Add typed accessors to `ConfigService` and inject
  the service instead.
- Verifies: `ConfigTest.inject_configs_should_be_tyhrough_configservice`.

## Tier 4 — JPA hygiene

### 4.1 Rename FKs/IDX/UC to follow naming convention (5 violations total)
- Requires Flyway migration + entity annotation updates. Write the migration with
  the `flyway-migration` skill.
- Verifies: `JpaTest.entity_foreign_keys_must_follow_naming_convention`,
  `entity_tables_must_follow_idx_naming_convention`,
  `entity_tables_must_follow_uc_naming_convention`.

### 4.2 Annotate 20 nullable columns with `@Nullable`
- Pure annotation pass — no schema change. Use checker-framework's
  `org.checkerframework.checker.nullness.qual.Nullable`.
- Verifies: `JpaTest.nullable_fields_must_have_column_annotation_mirror_same_behaviour`.

## Tier 5 — OpenAPI completeness

### 5.1 Add `@Schema` to 46 DTO fields
- Mechanical pass; helpful to do per-DTO so descriptions are meaningful, not just
  required/optional. Improves generated `openapi.json` and frontend typings.
- Verifies: `JsonDtoTest.all_fields_of_dtos_should_have_schema_annotation`.

## Tier 6 — Refine `IdClassTest` (mostly false positives)

### 6.1 Tune the rule, then evaluate genuine remainders
- Exclude:
  - The `id` field of `AbstractEntity` itself (it IS the ID, by definition).
  - Classes annotated `@Entity` whose `id` field type is `ID<Self>` already (false
    positive when the rule sees it as a UUID-typed field via the wrapper).
  - JSON DTOs (`*Json`) — wire format is `String`/`UUID`, not `ID<T>`.
  - `User.keycloakId` (external opaque identifier).
  - Exception infrastructure (`ExceptionId`, `AppException.id`, `AppFailureMessage.id`,
    `AppFailureErrorJson.id`).
- After exclusions, fix remaining real cases (likely 0–3).
- Reintroducing the `@IgnoreForIdClassTest` marker annotation is one option if
  carve-outs become unwieldy.
- Verifies: `IdClassTest.use_only_ID_class_as_id_*`.

## Working agreement

- Land each tier as its own PR/commit, run the targeted ArchUnit tests, confirm the
  count of violations drops to zero for that rule before moving on.
- After each tier, re-run the full suite — sometimes one fix reveals or hides
  another (esp. tiers 1.1 → 1.2 and 0.1 → 2.1).
- If a rule produces persistent false positives that aren't worth carve-outs,
  reconsider the rule itself — per UC-035 alt-flow A1, removing/changing a rule is
  acceptable if it doesn't make sense for chainlink.
