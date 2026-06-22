# Use Case: Manage API Keys

## Overview

**Use Case ID:** UC-077
**Use Case Name:** Manage API Keys
**Primary Actor:** User (via Web UI)
**Goal:** Create, list, and revoke personal API keys so that non-browser clients (CLI tools, scripts, automation) can authenticate against the LinkWeave API without sharing session cookies or OIDC credentials.
**Status:** Done

## Traceability

**Maps to:** FR-083, FR-084, NFR-020, NFR-021, C-018

---

## Preconditions

- The user is authenticated via the web UI (OIDC or form-based login).
- The user has an active local `User` record in the database.

## Main Success Scenario — Create API Key

1. User navigates to the API Keys section in the Settings page.
2. System displays a list of the user's existing API keys (name, prefix, creation date, last used date) and a "Create API Key" button.
3. User clicks "Create API Key".
4. System prompts the user to enter a descriptive name for the key (e.g., "My Laptop CLI", "CI Pipeline") and optionally select an expiration period.
5. User enters a name, optionally selects an expiration period (30 days / 90 days / 1 year / Never), and confirms creation.
6. System validates that the user has fewer than 10 active API keys (BR-001). The UI should disable the "Create API Key" button when the user already has 10 active keys (preventing the user from filling out the form pointlessly), but the server must also enforce the limit as a safeguard.
7. System generates a cryptographically random secret: a 32-byte random value, hex-encoded, prefixed with `lw_` (e.g., `lw_a1b2c3d4...`).
8. System stores a SHA-256 hash of the full key (without prefix) in the `api_key` table alongside the key prefix (first 8 characters after `lw_`), the user-provided name, the owner's user ID, the creation timestamp, and the optional expiration timestamp.
9. System displays the full API key to the user exactly once in a dismissible dialog with a clear warning: "Copy this key now. You will not be able to see it again."
10. User copies the key and dismisses the dialog.
11. System returns to the API key list, which now includes the newly created key (showing name, prefix, creation date, expiration date, last used = "Never").

## Main Success Scenario — List API Keys

1. User navigates to the API Keys section in the Settings page.
2. System returns all of the current user's keys, including revoked ones — each carrying its `revokedAt` (null when active). The UI hides revoked keys so the user sees only active and expired ones; the raw list is returned so a future view could surface revoked-key history without an API change.
3. System displays a table with columns: Name, Prefix (e.g., `lw_a1b2…`), Created, Expires, Last Used. Expired keys are visually distinguished (e.g., greyed out or marked "Expired").
4. Each row has a "Revoke" action button.

## Main Success Scenario — Revoke API Key

1. User clicks "Revoke" on an API key in the list.
2. System prompts the user with a confirmation dialog: "Revoke key '{name}' (lw_{prefix}…)? Any tools using this key will immediately lose access."
3. User confirms revocation.
4. System soft-deletes the API key by setting `revoked_at` to the current timestamp.
5. System removes the key from the displayed list.
6. Any subsequent API request using this key is rejected with HTTP 401.

## Alternative Flows

### A1: Maximum Keys Reached

**Trigger:** User attempts to create an API key while already having 10 active keys (step 6).
**Flow:**

1. System displays an error: "You have reached the maximum of 10 active API keys. Revoke an existing key before creating a new one."
2. User returns to the key list and can revoke an existing key.
3. User retries creation.

### A2: Invalid Key Name

**Trigger:** User submits the creation form with an empty name, a whitespace-only name, or a name exceeding 100 characters (step 5).
**Flow:**

1. System displays a validation error: "Key name is required, must be 1–100 characters, and cannot be only whitespace."
2. User enters a valid name.
3. Use case continues at step 5.

### A3: Duplicate Key Name

**Trigger:** User enters a name that matches an existing active key (step 5).
**Flow:**

1. System accepts the duplicate name (names are not unique — they are descriptive labels only).
2. Use case continues normally.

### A4: Revoke Last Key While CLI Is Active

**Trigger:** User revokes the API key that a CLI session is actively using (step 4 of revoke flow).
**Flow:**

1. System revokes the key immediately.
2. The next CLI request fails with HTTP 401.
3. CLI displays: "API key revoked. Run `linkweave login` to configure a new key."
4. No data is lost; the user can create a new key and reconfigure the CLI.

### A5: Key Not Found During Authentication

**Trigger:** An incoming request carries an `X-API-Key` header that does not match any active key (lookup during FR-084).
**Flow:**

1. The Quarkus `HttpAuthenticationMechanism` returns a failed `Uni` with `AuthenticationFailedException`.
2. The request is rejected with HTTP 401. The error message is generic: "Invalid or revoked API key."

### A6: User Account Deleted

**Trigger:** The owning user account is deleted (via `DELETE /api/auth/me`).
**Flow:**

1. System cascade-deletes all API keys owned by the deleted user (database-level `ON DELETE CASCADE`).
2. All subsequent requests with those keys are rejected with HTTP 401.

### A7: Rate Limit Exceeded

**Trigger:** The user (or an automated script) sends more than 5 creation requests per minute (BR-010).
**Flow:**

1. Quarkus `@RateLimit` rejects the request with HTTP 429 Too Many Requests.
2. Response includes a `Retry-After` header indicating when the user may retry.
3. No API key is created.

### A8: Expired Key Used for Authentication

**Trigger:** An incoming request carries an `X-API-Key` header that matches a key whose `expires_at` timestamp is in the past.
**Flow:**

1. The authentication mechanism treats the key the same as a revoked key — returns `AuthenticationFailedException` with the generic message "Invalid or revoked API key" (per BR-013 in UC-078).
2. The caller cannot distinguish an expired key from a revoked or nonexistent one.
3. The key remains in the user's key list, displayed as "Expired." The user can revoke it to clean up the list.

## Postconditions

### Success Postconditions (Create)

- A new `api_key` record exists in the database with: `id` (UUID), `user_id` (FK → `user`), `name`, `key_hash` (SHA-256), `key_prefix` (8 chars), `created_at`, `expires_at` (nullable, user-chosen or null for no expiration), `revoked_at = null`, `last_used_at = null`.
- The full raw key was displayed to the user exactly once and is no longer retrievable.
- The user can use the key to authenticate API requests (FR-084).

### Success Postconditions (Revoke)

- The `api_key` record has `revoked_at` set to the current timestamp.
- All subsequent requests with this key are rejected with HTTP 401.

### Failure Postconditions

- No API key is created or revoked.
- System displays an error message explaining the failure.

## Business Rules

### BR-001: Maximum Active Keys

A user may have at most 10 non-revoked API keys at any time (including expired keys that have not yet been revoked). This prevents unbounded key proliferation while allowing key rotation with overlap. Expired keys still count against the limit, which encourages users to revoke expired keys they no longer need.

### BR-002: Key Format

API keys follow the format `lw_` + 64 hex characters (32 bytes of randomness). The `lw_` prefix makes keys visually identifiable in logs and config files. Total key length: 67 characters.

### BR-003: Key Hashing

Only the SHA-256 hash of the raw key (the part after `lw_`) is stored in the database. The raw key is never written to any log, database, or persistent storage after the initial creation response. Key lookup on incoming requests hashes the provided key and compares against stored hashes.

### BR-004: Key Prefix for Identification

The first 8 hex characters after `lw_` are stored in plaintext as `key_prefix`. This allows the UI to display a partial key (e.g., `lw_a1b2c3d4…`) so the user can identify which key is which without exposing the full secret.

### BR-005: Constant-Time Comparison

When validating an incoming API key, the system must compare hashes using a constant-time algorithm (e.g., `MessageDigest.isEqual`) to prevent timing side-channel attacks.

### BR-006: Last Used Tracking

Each successful authentication via an API key must update the `last_used_at` timestamp on the `api_key` record. This helps users identify unused keys for cleanup. The update must not cause a perceptible performance impact on the request.

### BR-007: Key Revocation Is Immediate

Revoking a key takes effect immediately for all subsequent requests. There is no grace period. Revoked keys cannot be un-revoked — the user must create a new key.

### BR-008: API Key Inherits User Roles

When an API key authenticates successfully, the resulting `SecurityIdentity` must have exactly the same roles and collection access grants as if the user had logged in via the browser. API keys do not have their own permissions — they are a credential, not a role.

### BR-009: API Keys Are User-Scoped

API keys are personal. A user can only create, list, and revoke their own keys. The `ApiKeyResource` must filter all queries by the current user's ID (via `CurrentUserService`). An API key can never be used to manage another user's keys.

### BR-010: Rate Limiting on Key Creation Endpoint

API key creation is primarily limited by BR-001 (max 10 active keys per user). Additionally, the `POST /api/auth/api-keys` endpoint must use Quarkus `@RateLimit` to prevent automated abuse (e.g., 5 requests per minute). When the rate limit is exceeded, Quarkus returns HTTP 429 Too Many Requests automatically (see A7).

### BR-011: Optional Key Expiration

API keys may optionally have an expiration timestamp (`expires_at`). The user chooses an expiration period at creation time from a set of predefined options: **30 days**, **90 days**, **1 year**, or **Never** (the default). The expiration is stored as an absolute timestamp computed from creation time. When a key expires, authentication requests using it are rejected with the same generic 401 response as a revoked key (per BR-013 in UC-078) — the caller cannot distinguish expiration from revocation.

Expired keys remain in the user's key list (displayed as "Expired") until the user revokes them. The key list should visually separate expired keys from active ones to encourage cleanup.

A configuration property `quarkus.linkweave.api-key.default-expiration` may be set by administrators to override the default from "Never" to a specific period (e.g., `90d`) for environments with stricter compliance requirements.

---

## Data Model

### `api_key` Table

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, auto-generated | Unique key identifier |
| `user_id` | UUID | FK → `user.id`, NOT NULL, ON DELETE CASCADE | Owning user |
| `name` | VARCHAR(100) | NOT NULL | User-provided descriptive label |
| `key_hash` | VARCHAR(64) | NOT NULL | SHA-256 hex digest of the raw key (without `lw_` prefix). Indexed for O(1) lookup. The hash space (2^256) makes collisions infeasible, so no UNIQUE constraint is needed. |
| `key_prefix` | VARCHAR(8) | NOT NULL | First 8 hex chars of raw key, for UI identification |
| `created_at` | TIMESTAMP | NOT NULL, default NOW() | Key creation time |
| `expires_at` | TIMESTAMP | NULLABLE | When the key expires; NULL = never expires. Checked during authentication alongside `revoked_at`. |
| `last_used_at` | TIMESTAMP | NULLABLE | Timestamp of most recent successful auth |
| `revoked_at` | TIMESTAMP | NULLABLE | When the key was revoked; NULL = active |

### Indexes

- `idx_api_key_user_id` on `user_id` — for listing a user's keys.
- `idx_api_key_key_hash` on `key_hash` — for O(1) lookup during authentication.
- `idx_api_key_active` on `key_hash` WHERE `revoked_at IS NULL` — for authentication lookups that only need active (non-revoked, non-expired) keys. The `expires_at` check is applied in the query predicate, not as a separate index.

---

## API Endpoints

### `POST /api/auth/api-keys` — Create API Key

**Request:**
```json
{
  "name": "My Laptop CLI",
  "expiresIn": "90d"
}
```

The `expiresIn` field is optional. Accepted values: `"30d"`, `"90d"`, `"1y"`, `"never"` (or omit for default "never"). The server computes the absolute `expires_at` timestamp from this value.

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Laptop CLI",
  "prefix": "a1b2c3d4",
  "createdAt": "2026-05-10T12:00:00Z",
  "expiresAt": "2026-08-08T12:00:00Z",
  "lastUsedAt": null,
  "key": "lw_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1"
}
```

> **Note:** The `key` field is present only in the creation response. It is never returned by any other endpoint. `expiresAt` is `null` when no expiration is set.

### `GET /api/auth/api-keys` — List API Keys

**Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "My Laptop CLI",
    "prefix": "a1b2c3d4",
    "createdAt": "2026-05-10T12:00:00Z",
    "expiresAt": "2026-08-08T12:00:00Z",
    "lastUsedAt": "2026-05-10T14:30:00Z"
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "name": "CI Pipeline",
    "prefix": "f0e1d2c3",
    "createdAt": "2026-05-10T12:00:00Z",
    "expiresAt": null,
    "lastUsedAt": null
  }
]
```

> The full key is never included. Only the prefix is shown for identification. `expiresAt` is `null` for keys with no expiration. Each object also carries `revokedAt` (`null` when active); **the endpoint returns revoked keys too**, and the frontend hides them while displaying expired keys distinctly from active ones.

### `DELETE /api/auth/api-keys/{id}` — Revoke API Key

**Response:** `204 No Content` on success, `404 Not Found` if the key does not exist or belongs to another user.

---

## Security Considerations

| Concern | Mitigation |
|---|---|
| Key leaked in logs | Never log the raw key. Only the prefix may appear in logs. |
| Key stolen from config file | User can revoke immediately; key is scoped to a single user. |
| Timing attack on hash comparison | Use `MessageDigest.isEqual()` for constant-time comparison (BR-005). |
| Brute-force key generation | 32 bytes of entropy = 2^256 possibilities. Infeasible to guess. |
| Key in URL query string | Only accept the key via `X-API-Key` header, never from query parameters. |
| Stale keys after user deletion | `ON DELETE CASCADE` on `user_id` FK ensures cleanup (A6). |
| CSRF with API key | API keys are not sent automatically by browsers (unlike cookies). No CSRF risk. |
| Key ID enumeration via DELETE | The `DELETE` endpoint returns 404 both when the key does not exist and when it belongs to another user. While 403 would also be acceptable, 404 is used because API key IDs are UUIDs (128 bits of entropy) — guessing a valid ID is infeasible, so enumeration is not a realistic attack. This follows the same pattern as GitHub and Stripe for personal access tokens. |
| Zombie expired keys | Expired keys are visually distinguished in the UI and can be revoked for cleanup. Authentication rejects them identically to revoked keys, so no security risk exists even if the user does not revoke them. |
| API key managing API keys | **By design.** Keys are unscoped and inherit the owner's full role set (BR-008), so an API-key-authenticated request can also list, create, and revoke the same user's keys via `/auth/api-keys`. This is within a single user's own trust boundary (no cross-user access — BR-009) and matches how unscoped personal access tokens behave elsewhere (e.g. GitHub). Fine-grained per-key scopes are out of scope for this use case. |

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-083 | Manage API Keys | This use case implements FR-083 |
| FR-084 | Authenticate via API Key | Depends on the keys created here |
| NFR-020 | API Key Storage Security | Key hashing and constant-time comparison |
| NFR-021 | API Key Rotation Support | 10-key limit enables rotation |
| C-018 | API Key Auth Mechanism | Quarkus `HttpAuthenticationMechanism` |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-078 | Authenticate via API Key | Consumes the keys created by this use case |
| UC-079 | Manage Bookmarks via CLI | CLI uses API keys for authentication |
| UC-080 | Configure CLI Login | CLI stores API key locally |

### Architecture Reference

See [docs/cli-tool.md](../cli-tool.md) for the full architecture blueprint.
