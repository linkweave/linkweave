# Use Case: Authenticate via API Key

## Overview

**Use Case ID:** UC-078
**Use Case Name:** Authenticate via API Key
**Primary Actor:** CLI User / Script / Automation
**Goal:** Authenticate an API request by sending an `X-API-Key` header so that non-browser clients can access the full LinkWeave API without browser-based session cookies or OIDC redirects.
**Status:** Done

## Traceability

**Maps to:** FR-084, NFR-020, C-018

---

## Preconditions

- The user has created at least one active API key via UC-077.
- The client has the raw API key (e.g., stored in `~/.linkweave/config.json` or set via `LINKWEAVE_API_KEY` environment variable).

## Main Success Scenario

1. Client sends an HTTP request to any LinkWeave API endpoint with an `X-API-Key: lw_<64 hex chars>` header.
2. The Quarkus custom `HttpAuthenticationMechanism` (C-018) detects the `X-API-Key` header.
3. System strips the `lw_` prefix from the header value.
4. System computes SHA-256 hash of the remaining value.
5. System looks up an `api_key` record where `key_hash` matches AND `revoked_at IS NULL` AND (`expires_at IS NULL` OR `expires_at > NOW()`).
6. A matching active, non-expired key is found.
7. System loads the owning `User` record.
8. System builds a `SecurityIdentity` using `QuarkusSecurityIdentity.Builder` with the user's email as the principal name, the user's roles from `FachRolle.getPermissions()`, and a custom `ApiKeyCredential` holding the API key ID. An `auth-method` attribute of `"api-key"` is added for audit purposes.
9. System updates the `last_used_at` timestamp on the `api_key` record (BR-006 in UC-077).
10. System allows the request to proceed to the target Resource with the fully populated `SecurityIdentity`.
11. The target Resource (e.g., `BookmarkResource`) performs authorization checks using `AuthorizationService` as usual — no change in behavior compared to browser-based requests.
12. System returns the HTTP response to the client.

## Alternative Flows

### A1: No API Key Header

**Trigger:** The request does not include an `X-API-Key` header (step 2).
**Flow:**

1. The API key authentication mechanism returns `Uni.createFrom().nullItem()` — Quarkus interprets `null` as "this mechanism has nothing to contribute" and proceeds to the next one.
2. Quarkus proceeds to the next authentication mechanism (OIDC cookie / form cookie).
3. If another mechanism succeeds, the request is processed normally.
4. If no mechanism succeeds, the response is HTTP 401.

### A2: Malformed API Key

**Trigger:** The `X-API-Key` header value does not match the expected format `lw_` + 64 hex chars (step 3).
**Flow:**

1. The authentication mechanism returns `Uni.createFrom().failure(new AuthenticationFailedException("Invalid or revoked API key"))`.
2. Quarkus rejects the request with HTTP 401.
3. Response body includes: `{"error": "Invalid or revoked API key."}`
4. The same generic message is used as in A3 to prevent the caller from distinguishing format errors from invalid keys (per BR-013).

### A3: Key Not Found, Revoked, or Expired

**Trigger:** The SHA-256 hash lookup returns no matching key that is both non-revoked and non-expired (step 5). This covers three cases: key does not exist, key was revoked, or key has passed its `expires_at` timestamp.
**Flow:**

1. The authentication mechanism returns `Uni.createFrom().failure(new AuthenticationFailedException("Invalid or revoked API key"))`.
2. Quarkus rejects the request with HTTP 401.
3. Response body includes: `{"error": "Invalid or revoked API key."}`
4. The response is identical regardless of whether the key was not found, was revoked, or has expired — the caller cannot distinguish these cases (per BR-013).

### A4: User Account Deleted but Key Remains

**Trigger:** The `api_key` record references a `user_id` that no longer exists (step 7).
**Flow:**

1. This scenario should not occur because of `ON DELETE CASCADE` (UC-077 data model).
2. If it occurs due to a race condition, the authentication mechanism returns a failed Uni with `AuthenticationFailedException`.
3. The request is rejected with HTTP 401.

### A5: Concurrent Revocation

**Trigger:** The API key is revoked between the lookup (step 5) and the identity build (step 8).
**Flow:**

1. The key was active at lookup time, so authentication succeeds.
2. This is an accepted race condition — the revocation takes effect on the next request.
3. No special handling needed.

### A6: API Key Used on Non-API Path

**Trigger:** A request to a non-API path (e.g., `/q/health`) includes an `X-API-Key` header.
**Flow:**

1. The authentication mechanism runs but the path is not protected by `@Authenticated`.
2. The request is processed normally (the `SecurityIdentity` is available but not required).

## Postconditions

### Success Postconditions

- The request is authenticated as the user who owns the API key.
- `CurrentUserService.currentUserID()` returns the owner's user ID (resolves via `SecurityIdentity.getPrincipal().getName()` → email → User lookup).
- `AuthorizationService` enforces the same collection access checks as for browser-based sessions.
- `AbstractEntityListener` populates `userErstellt`/`userMutiert` with the API key owner's identity (works because the `SecurityIdentity` is fully populated before any JPA entity operations).
- The `api_key.last_used_at` timestamp is updated (best-effort, debounced).

### Failure Postconditions

- The request is rejected with HTTP 401.
- No `SecurityIdentity` is established.
- No `last_used_at` update occurs.

## Business Rules

### BR-011: API Key Header Name

The header name is `X-API-Key`. The mechanism must not accept the key via query parameter, request body, or any other header name (e.g., `Authorization: Bearer`).

### BR-012: API Key Exclusive with Session Auth

If a request carries both an `X-API-Key` header AND a valid session cookie, the API key takes precedence. This simplifies the auth chain and avoids ambiguity.

### BR-013: Error Messages Do Not Leak Information

All authentication failure messages — regardless of cause (malformed key, key not found, key revoked, key expired, user deleted) — must return the same generic response: `{"error": "Invalid or revoked API key."}` with HTTP 401. The response must not indicate the specific reason for failure. Format validation errors, expired keys, and revoked keys are all treated identically to prevent the caller from learning anything about the server's key state.

### BR-014: `last_used_at` Update is Best-Effort

The `last_used_at` timestamp update (BR-006 in UC-077) must not cause the request to fail if the update itself fails (e.g., database constraint error). The update should be wrapped in a try-catch and logged on failure. Additionally, the update should only occur if the current `last_used_at` value is older than 5 minutes to avoid unnecessary DB writes on every request.

### BR-015: No Timing Side-Channel on Key Status

When the `X-API-Key` header is present but the key is not found, revoked, or expired, the mechanism must return the same `AuthenticationFailedException` with the same response time characteristics regardless of the specific failure reason. The error message must be identical in all cases: "Invalid or revoked API key." The single query `WHERE key_hash = ? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())` ensures no timing difference between "not found," "revoked," and "expired."

### BR-016: Mechanism Coexistence with OIDC and Form Auth

The API key mechanism must not replace or disable the existing OIDC and form-based authentication mechanisms. All three must coexist. When no `X-API-Key` header is present, the mechanism must return `null` immediately to allow OIDC and form auth to proceed.

---

## Technical Design

### Quarkus `HttpAuthenticationMechanism`

The mechanism must implement `io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism` (the interface, **not** the annotation of the same name in `io.quarkus.vertx.http.runtime.security.annotation`).

#### Registration

The mechanism is registered as a regular `@ApplicationScoped` CDI bean (not `@Alternative`). This allows it to coexist alongside the existing OIDC and form-based mechanisms — Quarkus will run all three in priority order. The API key mechanism declares `getPriority()` returning `2100` (above Basic's 2000 and OIDC's 1001) so it runs first when an `X-API-Key` header is present.

This approach is preferred over a combining `@Alternative` mechanism because:
- It does not couple the API key mechanism to the internals of Quarkus OIDC or form auth.
- Future changes to OIDC or form auth configuration require no changes to the API key mechanism.
- Quarkus' built-in mechanism ordering handles delegation naturally — if the API key mechanism returns `null`, the next mechanism in priority order takes over.

#### Quarkus Mechanism Return Semantics

Quarkus `HttpAuthenticationMechanism.authenticate()` returns `Uni<SecurityIdentity>`. There is **no** `NOT_ATTEMPTED` or `NOT_VALID` enum. The three outcomes are:

| Intent | Return value | Quarkus behavior |
|---|---|---|
| Skip (no relevant credential) | `Uni.createFrom().nullItem()` | Moves to the next mechanism |
| Authentication failed | `Uni.createFrom().failure(new AuthenticationFailedException(...))` | Stops the chain, triggers challenge |
| Authentication succeeded | `Uni.createFrom().item(securityIdentity)` | Request is authenticated |

#### Priority and Ordering

Quarkus orders mechanisms by descending priority (higher = first chance). Built-in priorities:

| Mechanism | Default Priority |
|---|---|
| Basic | 2000 |
| OIDC Authorization Code | 1001 |
| Form-based | 1000 |
| mTLS | 1000 |

The API key mechanism should have priority **2100** (higher than Basic) so it runs before any other mechanism when an `X-API-Key` header is present.

#### Proactive Authentication

The LinkWeave application has `quarkus.http.auth.proactive=false` (see `application.properties`). This means authentication only runs when a endpoint requires it (`@Authenticated`, `@RolesAllowed`, etc.). The API key mechanism respects this — it will only be invoked for protected endpoints.

This also means the `@HttpAuthenticationMechanism("api-key")` annotation **can** be used on specific endpoints to select the mechanism, if desired. However, since API key auth should work on all endpoints (not just specific ones), no endpoint-level selection is needed — the standalone mechanism at priority 2100 handles it globally.

**Flow diagram:**

```
Incoming Request → Protected endpoint? (@Authenticated)
    │ No → Serve without auth
    │ Yes → HttpAuthenticator runs mechanisms in priority order
    │
    ├─ Priority 2100: ApiKeyAuthMechanism
    │   ├─ Has X-API-Key header?
    │   │   ├─ Yes → Strip "lw_" prefix → SHA-256 hash → Lookup active key
    │   │   │   │  (WHERE key_hash = ? AND revoked_at IS NULL
    │   │   │   │   AND (expires_at IS NULL OR expires_at > NOW()))
    │   │   │   ├─ Found → Build SecurityIdentity → Update last_used_at → Proceed ✓
    │   │   │   └─ Not found / expired / revoked → AuthenticationFailedException → 401
    │   │   └─ No → return null (skip)
    │
    ├─ Priority 1001: OIDC Code Flow (session cookie)
    │   ├─ Valid session cookie → Build SecurityIdentity → Proceed ✓
    │   └─ No cookie → return null (skip)
    │
    └─ Priority 1000: Form Auth (form cookie)
        ├─ Valid form cookie → Build SecurityIdentity → Proceed ✓
        └─ No cookie → return null (skip)

No mechanism succeeded → Challenge (401 or redirect depending on lowest mechanism)
```

### `SecurityIdentity` Construction

When an API key authenticates successfully, the mechanism must build a `SecurityIdentity` that is **indistinguishable** from one created by OIDC or form auth. This is critical because `CurrentUserService` resolves the user by calling `association.getIdentity().getPrincipal().getName()` and looking up the email in the database.

Construction using `QuarkusSecurityIdentity.Builder`:

```java
// Load the User entity from the database to get email and roles
User user = userRepo.findBenutzerIdFromBenutzername(email)
    .orElseThrow(() -> new AuthenticationFailedException("User not found"));

// getSecurityRoles() returns comma-separated role names; split into a Set.
// Guard against null/empty — a user with no roles gets an empty set, not {""}
String securityRoles = user.getSecurityRoles();
Set<String> roles = (securityRoles == null || securityRoles.isBlank())
    ? Set.of()
    : Set.of(securityRoles.split(","));

QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
    .setPrincipal(new QuarkusPrincipal(user.getEmail().toString()))
    .addRoles(roles)
    .addCredential(new ApiKeyCredential(apiKey.getId()))
    .addAttribute("auth-method", "api-key")
    .build();
```

Key fields:

- **Principal**: `QuarkusPrincipal` with the user's **email** as the name — this must match what OIDC/form auth sets (Quarkus OIDC uses `quarkus.oidc.token.principal-claim=email` in the app config), so `CurrentUserService` works identically.
- **Roles**: All roles from `User.getSecurityRoles()` (which returns a comma-separated string from `fachRollen` → `FachRolle.getPermissions()` → role names). Split into `Set<String>` and passed to `addRoles()`. This is the same data that `@Roles` / `@UserDefinition` reads for form auth.
- **Attributes**: `auth-method` = `api-key` for audit/logging purposes.
- **Credential**: A custom `ApiKeyCredential` implementing `io.quarkus.security.credential.Credential`, holding the `ApiKey` entity ID for traceability.

This ensures that `CurrentUserService`, `AuthorizationService`, and `AbstractEntityListener` all work identically regardless of whether the request came from a browser session or an API key.

### `last_used_at` Update Strategy

The `last_used_at` update on the `api_key` table must not block or fail the authentication flow. Implementation:

1. Build the `SecurityIdentity` first.
2. After the identity is successfully built, update `last_used_at` in a **separate transaction** (`@Transactional(TxType.REQUIRES_NEW)`) via a fire-and-forget call.
3. Wrap in try-catch; log on failure but never propagate the exception.
4. **Debounce**: Only update if the current `last_used_at` is older than 5 minutes, to avoid a DB write on every single request.

### Blocking vs. Reactive

The `HttpAuthenticationMechanism.authenticate()` method runs on the Vert.x event loop. Database access (looking up the API key hash, loading the User) is blocking and must not be performed directly on the event loop thread.

**Solution**: Use `context.runBlocking()` from the `IdentityProviderManager`, or delegate the blocking work to an `IdentityProvider`. The recommended approach for LinkWeave:

1. The `HttpAuthenticationMechanism` extracts the `X-API-Key` header, creates a custom `ApiKeyAuthenticationRequest` (extends `BaseAuthenticationRequest`), and delegates to `identityProviderManager.authenticate(request)`.
2. A custom `ApiKeyIdentityProvider` implements `IdentityProvider<ApiKeyAuthenticationRequest>`, performs the database lookup (this runs on a worker thread), and builds the `SecurityIdentity`.

This cleanly separates the non-blocking credential extraction (mechanism) from the blocking verification (identity provider), following Quarkus best practices.

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-084 | Authenticate via API Key | This use case implements FR-084 |
| C-018 | API Key Auth Mechanism | Defines the Quarkus integration |
| NFR-020 | API Key Storage Security | Hashing and constant-time comparison |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-077 | Manage API Keys | Produces the keys consumed here |
| UC-079 | Manage Bookmarks via CLI | CLI relies on this authentication |
| UC-080 | Configure CLI Login | CLI stores the key for use here |
