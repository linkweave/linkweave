# Use Case: Authenticate via API Key

## Overview

**Use Case ID:** UC-078
**Use Case Name:** Authenticate via API Key
**Primary Actor:** CLI User / Script / Automation
**Goal:** Authenticate an API request by sending an `X-API-Key` header so that non-browser clients can access the full Chainlink API without browser-based session cookies or OIDC redirects.
**Status:** Draft

## Traceability

**Maps to:** FR-084, NFR-020, C-018

---

## Preconditions

- The user has created at least one active API key via UC-077.
- The client has the raw API key (e.g., stored in `~/.chainlink/config.json` or set via `CHAINLINK_API_KEY` environment variable).

## Main Success Scenario

1. Client sends an HTTP request to any Chainlink API endpoint with an `X-API-Key: cl_<64 hex chars>` header.
2. The Quarkus custom `HttpAuthenticationMechanism` (C-018) detects the `X-API-Key` header.
3. System strips the `cl_` prefix from the header value.
4. System computes SHA-256 hash of the remaining value.
5. System looks up an active `api_key` record where `key_hash` matches AND `revoked_at IS NULL`.
6. A matching key is found.
7. System loads the owning `User` record.
8. System builds a `SecurityIdentity` with the user's principal (email), roles (`BOOKMARK_READ`, `BOOKMARK_WRITE`, etc.), and a custom credential indicating API-key-based authentication.
9. System updates the `last_used_at` timestamp on the `api_key` record (BR-006 in UC-077).
10. System allows the request to proceed to the target Resource with the fully populated `SecurityIdentity`.
11. The target Resource (e.g., `BookmarkResource`) performs authorization checks using `AuthorizationService` as usual — no change in behavior compared to browser-based requests.
12. System returns the HTTP response to the client.

## Alternative Flows

### A1: No API Key Header

**Trigger:** The request does not include an `X-API-Key` header (step 2).
**Flow:**

1. The API key authentication mechanism returns `NOT_ATTEMPTED`.
2. Quarkus proceeds to the next authentication mechanism (OIDC cookie / form cookie).
3. If another mechanism succeeds, the request is processed normally.
4. If no mechanism succeeds, the response is HTTP 401.

### A2: Malformed API Key

**Trigger:** The `X-API-Key` header value does not match the expected format `cl_` + 64 hex chars (step 3).
**Flow:**

1. The authentication mechanism returns `NOT_VALID`.
2. The request is rejected with HTTP 401.
3. Response body includes: `{"error": "Invalid API key format."}`

### A3: Key Not Found or Revoked

**Trigger:** The SHA-256 hash lookup returns no matching active key (step 5).
**Flow:**

1. The authentication mechanism returns `NOT_VALID`.
2. The request is rejected with HTTP 401.
3. Response body includes: `{"error": "Invalid or revoked API key."}`
4. This is indistinguishable from a key-not-found response to prevent key enumeration.

### A4: User Account Deleted but Key Remains

**Trigger:** The `api_key` record references a `user_id` that no longer exists (step 7).
**Flow:**

1. This scenario should not occur because of `ON DELETE CASCADE` (UC-077 data model).
2. If it occurs due to a race condition, the authentication mechanism returns `NOT_VALID`.
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
- `CurrentUserService.currentUserID()` returns the owner's user ID.
- `AuthorizationService` enforces the same collection access checks as for browser-based sessions.
- `AbstractEntityListener` populates `userErstellt`/`userMutiert` with the API key owner's identity.
- The `api_key.last_used_at` timestamp is updated.

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

Authentication failure messages must be generic: "Invalid or revoked API key." The response must not indicate whether the key exists, was revoked, or belongs to a deleted user.

### BR-014: `last_used_at` Update is Best-Effort

The `last_used_at` timestamp update (BR-006 in UC-077) must not cause the request to fail if the update itself fails (e.g., database constraint error). The update should be wrapped in a try-catch and logged on failure.

---

## Technical Design

### Quarkus `HttpAuthenticationMechanism`

The mechanism must implement `io.quarkus.security.spi.runtime.HttpAuthenticationMechanism` and be registered with `@Alternative` priority higher than the default OIDC mechanism.

**Priority**: The mechanism should be ordered to run **before** OIDC/form auth so that if an `X-API-Key` header is present, it is processed first (per BR-012).

**Flow diagram:**

```
Incoming Request
    │
    ├─ Has X-API-Key header?
    │   ├─ Yes → Strip "cl_" prefix → SHA-256 hash → Lookup active key
    │   │   ├─ Found → Build SecurityIdentity → Proceed ✓
    │   │   └─ Not found → Return 401
    │   └─ No → Fall through to OIDC/Form auth
    │       ├─ Valid session cookie → Proceed ✓
    │       └─ No valid cookie → Return 401
```

### `SecurityIdentity` Construction

When an API key authenticates successfully:

- **Principal**: `QuarkusPrincipal` with the user's email as the name (same as OIDC/form).
- **Roles**: All roles associated with the user (e.g., `BOOKMARK_READ`, `BOOKMARK_WRITE`).
- **Attributes**: Include an `auth-method` attribute set to `api-key` for audit/logging purposes.
- **Credential**: A custom `ApiKeyCredential` that holds the `api_key.id` for traceability.

This ensures that `CurrentUserService`, `AuthorizationService`, and `AbstractEntityListener` all work identically regardless of whether the request came from a browser session or an API key.

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
