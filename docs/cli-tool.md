# CLI Tool & API Key Architecture

**Status:** In Progress — Phases 1–4 complete (API key backend, web UI, CLI with bookmark commands); Phase 5 (polish & distribution) pending
**Date:** 2026-05-10 (updated 2026-07-15)

---

## Overview

This document describes the architecture for adding a command-line interface (CLI) to LinkWeave, enabling users to manage bookmarks from a terminal. The CLI authenticates via API keys — a new server-side credential type — and communicates exclusively through the existing HTTP API.

---

## Use Cases

| UC | Title | Spec |
|---|---|---|
| UC-077 | Manage API Keys | [UC-077-manage-api-keys.md](use_cases/UC-077-manage-api-keys.md) |
| UC-078 | Authenticate via API Key | [UC-078-authenticate-via-api-key.md](use_cases/UC-078-authenticate-via-api-key.md) |
| UC-079 | Manage Bookmarks via CLI | [UC-079-manage-bookmarks-via-cli.md](use_cases/UC-079-manage-bookmarks-via-cli.md) |
| UC-080 | Configure CLI Login | [UC-080-configure-cli-login.md](use_cases/UC-080-configure-cli-login.md) |

---

## Requirements Traceability

| Requirement | Title | UC |
|---|---|---|
| FR-083 | Manage API Keys | UC-077 |
| FR-084 | Authenticate via API Key | UC-078 |
| FR-085 | Manage Bookmarks via CLI | UC-079 |
| FR-086 | CLI Login Flow | UC-080 |
| NFR-020 | API Key Storage Security | UC-077 |
| NFR-021 | API Key Rotation Support | UC-077 |
| NFR-022 | CLI API Client Generation | UC-079 |
| NFR-023 | CLI Distribution | UC-079 |
| C-018 | API Key Auth Mechanism | UC-078 |
| C-019 | CLI Lives in Monorepo | UC-079 |
| C-020 | CLI Language is TypeScript | UC-079 |

---

## Architecture Decisions

### AD-1: API Keys (not OAuth, not password login)

**Decision:** Add a personal API key credential type for non-browser clients.

**Rationale:**
- The production auth is Google OIDC — a CLI can't do browser-based OAuth redirects without a complex device flow (which Google doesn't support for web-app type clients).
- Form-based auth (email+password) only works in dev/test; many production users have OIDC-only accounts with no password.
- API keys are simple, stateless, and sufficient for a self-hosted bookmark manager.

### AD-2: Same Repository

**Decision:** The CLI lives in `cli/` at the repo root, alongside `api/` and `frontend/`.

**Rationale:**
- The OpenAPI spec at `/q/openapi` is the shared contract. Generating the TypeScript client from the same spec ensures type-safety.
- API changes break CLI builds immediately in CI (not weeks later).
- One `git tag` covers API + frontend + CLI.

### AD-2b: CLI Reuses the Frontend's Generated Client (2026-07-15)

**Decision:** The CLI imports the checked-in typescript-fetch client from
`frontend/src/api/generated` (via `cli/src/api.ts`) and tsup bundles it into
`cli/dist/main.js`, so the published package is self-contained.

**Deliberately deferred:** extracting the client into a shared workspace
package (root pnpm workspace + `packages/api-client`). With only two
consumers and standalone-package CI, the restructuring isn't worth it yet.
Revisit when a third consumer needs the client (desktop app,
screenshot-service) or when npm publishing (Phase 5) becomes concrete.

### AD-3: TypeScript

**Decision:** The CLI is written in TypeScript, reusing the `typescript-fetch` client generated from the OpenAPI spec.

**Rationale:**
- The frontend already uses `@openapitools/openapi-generator-cli` with `typescript-fetch`. The CLI can reuse the exact same pipeline.
- The team is proficient in TypeScript.
- Distribution via `npm install -g @linkweave/cli` is straightforward.

### AD-4: Through the HTTP API

**Decision:** The CLI communicates exclusively through the existing REST API.

**Rationale:**
- `AuthorizationService` enforces access control at the Resource layer.
- `AbstractEntityListener` populates audit fields via `CurrentUserService`, which depends on `SecurityIdentity` — only available through the HTTP auth chain.
- Business logic lives in Service classes; duplicating it in the CLI would violate DRY and the project's layered architecture.

---

## Repository Structure

```
linkweave/
├── api/                                    # Existing Quarkus backend
│   └── src/main/java/org/linkweave/api/
│       ├── auth/
│       │   ├── AuthResource.java           # Existing
│       │   ├── ApiKeyResource.java         # NEW: CRUD for API keys
│       │   └── ...
│       └── ...
├── frontend/                               # Existing Vue.js frontend
│   └── ...
├── cli/                                    # NEW: CLI tool
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── main.ts                         # CLI entry point (commander)
│   │   ├── commands/
│   │   │   ├── login.ts                    # linkweave login
│   │   │   ├── logout.ts                   # linkweave logout
│   │   │   ├── bookmarks/
│   │   │   │   ├── add.ts                  # linkweave bookmarks add <url>
│   │   │   │   ├── list.ts                 # linkweave bookmarks list
│   │   │   │   ├── edit.ts                 # linkweave bookmarks edit <id>
│   │   │   │   └── rm.ts                   # linkweave bookmarks rm <id>
│   │   │   └── collections/
│   │   │       └── list.ts                 # linkweave collections list
│   │   ├── api/
│   │   │   └── generated/                  # Generated from /q/openapi
│   │   ├── config.ts                       # Read/write ~/.linkweave/config.json
│   │   └── client.ts                       # API client with X-API-Key injection
│   └── tests/
├── docs/                                   # Documentation (this file)
└── ...
```

---

## Backend Changes

### Phase 1: API Key Infrastructure

#### 1a. Entity & Repository

- **`ApiKey` entity** (`api/src/main/java/org/linkweave/api/auth/ApiKey.java`)
  - Fields: `id`, `user` (ManyToOne), `name`, `keyHash`, `keyPrefix`, `createdAt`, `lastUsedAt`, `revokedAt`
  - Extends `AbstractEntity` (gets `userErstellt`/`userMutiert` for audit)
  - `@Column` lengths from `DbConst`

- **`ApiKeyRepo`** (`api/src/main/java/org/linkweave/api/auth/ApiKeyRepo.java`)
  - `findByKeyHash(String hash)` — for auth lookup
  - `findActiveByUserId(ID<User> userId)` — for listing
  - `countActiveByUserId(ID<User> userId)` — for max-key enforcement

#### 1b. Flyway Migration

- **`V{next}__create_api_key_table.sql`**
  - Creates `api_key` table with columns as defined in [UC-077 data model](use_cases/UC-077-manage-api-keys.md#data-model)
  - Foreign key to `user` table with `ON DELETE CASCADE`
  - Indexes on `key_hash` and `user_id`

#### 1c. Service Layer

- **`ApiKeyService`** (`api/src/main/java/org/linkweave/api/auth/ApiKeyService.java`)
  - `createApiKey(ID<User> userId, String name)` — generates key, hashes, stores, returns raw key once
  - `listActiveKeys(ID<User> userId)` — returns all non-revoked keys for the user
  - `revokeKey(ID<User> userId, ID<ApiKey> keyId)` — sets `revokedAt`
  - `findActiveKeyByHash(String hash)` — used by the auth mechanism (returns key + user)
  - `updateLastUsed(ID<ApiKey> keyId)` — best-effort timestamp update

#### 1d. Resource Layer

- **`ApiKeyResource`** (`api/src/main/java/org/linkweave/api/auth/ApiKeyResource.java`)
  - `@Authenticated` — requires web session (API keys cannot manage other API keys)
  - `POST /api/auth/api-keys` — create key
  - `GET /api/auth/api-keys` — list keys
  - `DELETE /api/auth/api-keys/{id}` — revoke key
  - Uses `CurrentUserService` to scope operations to the logged-in user
  - Returns `ApiKeyJson` DTOs (never exposes the hash)

#### 1e. Authentication Mechanism

- **`ApiKeyAuthenticationMechanism`** (`api/src/main/java/org/linkweave/infrastructure/auth/ApiKeyAuthenticationMechanism.java`)
  - Implements Quarkus `HttpAuthenticationMechanism`
  - Checks for `X-API-Key` header on every request
  - Strips `lw_` prefix, computes SHA-256, looks up via `ApiKeyService`
  - Builds `SecurityIdentity` with user's principal and roles
  - Falls through to OIDC/form if no `X-API-Key` header present
  - Registered with `@Alternative` + `@Priority` to run before OIDC

---

## Frontend Changes

### Settings Page: API Keys Section

- Add an "API Keys" tab/section to the existing Settings page
- Display table of keys (name, prefix, created, last used) with revoke button
- "Create API Key" dialog with name input
- One-time key display dialog with copy button and warning
- API key count indicator: "3/10 keys used"

---

## Implementation Phases

### Phase 1: API Key Backend (Prerequisite) — ✅ Done

Implemented in `api/src/main/java/org/linkweave/api/auth/apikey/`.

- [x] Create `ApiKey` entity
- [x] Create Flyway migration
- [x] Create `ApiKeyRepo`
- [x] Create `ApiKeyService`
- [x] Create `ApiKeyResource` with JSON DTOs
- [x] Create `ApiKeyAuthenticationMechanism` (`ApiKeyAuthMechanism` + `ApiKeyIdentityProvider`)
- [x] Register mechanism in Quarkus
- [x] Integration tests (`ApiKeyResourceITest`)

### Phase 2: Frontend — API Key Management — ✅ Done

Implemented in `frontend/src/components/apikey/` and `frontend/src/stores/apiKey.ts`.

- [x] Add API Keys section to Settings page (`ApiKeySection.vue`)
- [x] Create key dialog (`ApiKeyCreateDialog.vue`)
- [x] Key list table
- [x] Revoke key confirmation
- [x] One-time key display with copy (`ApiKeyRevealDialog.vue`)

### Phase 3: CLI Scaffolding — ✅ Done

Implemented in `cli/` (tsup bundle, `dist/main.js` bin entry).

- [x] Create `cli/` directory with `package.json`
- [x] Set up TypeScript + commander
- [x] Reuse the generated `typescript-fetch` client (shared with the frontend
      at `frontend/src/api/generated`, bundled into the CLI at build time)
- [x] Implement config management (`~/.linkweave/config.json`, 0600, env
      overrides `LINKWEAVE_API_KEY`/`LINKWEAVE_SERVER`)
- [x] Implement `linkweave login` / `linkweave logout`

### Phase 4: CLI Bookmark Commands — ✅ Done

Vitest unit tests live next to the sources (`cli/src/*.spec.ts`); e2e tests
run in the Playwright suite (`frontend/e2e/cli.spec.ts`) against a real
server and are wired into the e2e CI workflow.

- [x] `linkweave bookmarks add <url>` (tag-name resolution BR-019, folder-path
      resolution BR-020, collection name/ID resolution A8)
- [x] `linkweave bookmarks list` (`--format` table/json/ids, `--folder`/`--tag` filters)
- [x] `linkweave bookmarks edit <id>` (via `GET /api/bookmarks/{id}`, added for
      the CLI so edit needs no collection scan and missing IDs are a clean 404)
- [x] `linkweave bookmarks rm <id>`
- [x] `linkweave collections list`

### Phase 5: Polish & Distribution

- [ ] Shell completions (bash/zsh/fish)
- [ ] `npm install -g @linkweave/cli` publishing setup
- [x] README with installation instructions (`cli/README.md`)
- [x] `--insecure` flag for self-signed certs

---

## Security Summary

| Asset | Threat | Mitigation |
|---|---|---|
| Raw API key | Stored in DB | Never stored; only SHA-256 hash is persisted |
| Raw API key | Logged | Never logged; only prefix appears in logs |
| `~/.linkweave/config.json` | Read by other users | File permissions set to `0600` |
| API key in transit | Intercepted | TLS required; no HTTP fallback |
| Key brute-force | Guessed by attacker | 32 bytes of entropy (2^256); infeasible |
| Key enumeration | Attacker probes keys | Constant-time hash comparison; generic error messages |
| Revoked key | Used after revocation | `revoked_at` checked on every request; immediate effect |
| CSRF via API key | Browser auto-sends key | API keys sent via header, not cookie; no CSRF risk |
