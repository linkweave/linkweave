# CLI Tool & API Key Architecture

**Status:** In Progress — Phases 1–2 complete (API key backend + web UI); CLI (Phases 3–5) pending
**Date:** 2026-05-10

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

### AD-3: TypeScript

**Decision:** The CLI is written in TypeScript, reusing the `typescript-fetch` client generated from the OpenAPI spec.

**Rationale:**
- The frontend already uses `@openapitools/openapi-generator-cli` with `typescript-fetch`. The CLI can reuse the exact same pipeline.
- The team is proficient in TypeScript.
- Distribution via `npm install -g @chainlink/cli` is straightforward.

### AD-4: Through the HTTP API

**Decision:** The CLI communicates exclusively through the existing REST API.

**Rationale:**
- `AuthorizationService` enforces access control at the Resource layer.
- `AbstractEntityListener` populates audit fields via `CurrentUserService`, which depends on `SecurityIdentity` — only available through the HTTP auth chain.
- Business logic lives in Service classes; duplicating it in the CLI would violate DRY and the project's layered architecture.

---

## Repository Structure

```
chainlink/
├── api/                                    # Existing Quarkus backend
│   └── src/main/java/org/chainlink/api/
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
│   │   │   ├── login.ts                    # chainlink login
│   │   │   ├── logout.ts                   # chainlink logout
│   │   │   ├── bookmarks/
│   │   │   │   ├── add.ts                  # chainlink bookmarks add <url>
│   │   │   │   ├── list.ts                 # chainlink bookmarks list
│   │   │   │   ├── edit.ts                 # chainlink bookmarks edit <id>
│   │   │   │   └── rm.ts                   # chainlink bookmarks rm <id>
│   │   │   └── collections/
│   │   │       └── list.ts                 # chainlink collections list
│   │   ├── api/
│   │   │   └── generated/                  # Generated from /q/openapi
│   │   ├── config.ts                       # Read/write ~/.chainlink/config.json
│   │   └── client.ts                       # API client with X-API-Key injection
│   └── tests/
├── docs/                                   # Documentation (this file)
└── ...
```

---

## Backend Changes

### Phase 1: API Key Infrastructure

#### 1a. Entity & Repository

- **`ApiKey` entity** (`api/src/main/java/org/chainlink/api/auth/ApiKey.java`)
  - Fields: `id`, `user` (ManyToOne), `name`, `keyHash`, `keyPrefix`, `createdAt`, `lastUsedAt`, `revokedAt`
  - Extends `AbstractEntity` (gets `userErstellt`/`userMutiert` for audit)
  - `@Column` lengths from `DbConst`

- **`ApiKeyRepo`** (`api/src/main/java/org/chainlink/api/auth/ApiKeyRepo.java`)
  - `findByKeyHash(String hash)` — for auth lookup
  - `findActiveByUserId(ID<User> userId)` — for listing
  - `countActiveByUserId(ID<User> userId)` — for max-key enforcement

#### 1b. Flyway Migration

- **`V{next}__create_api_key_table.sql`**
  - Creates `api_key` table with columns as defined in [UC-077 data model](use_cases/UC-077-manage-api-keys.md#data-model)
  - Foreign key to `user` table with `ON DELETE CASCADE`
  - Indexes on `key_hash` and `user_id`

#### 1c. Service Layer

- **`ApiKeyService`** (`api/src/main/java/org/chainlink/api/auth/ApiKeyService.java`)
  - `createApiKey(ID<User> userId, String name)` — generates key, hashes, stores, returns raw key once
  - `listActiveKeys(ID<User> userId)` — returns all non-revoked keys for the user
  - `revokeKey(ID<User> userId, ID<ApiKey> keyId)` — sets `revokedAt`
  - `findActiveKeyByHash(String hash)` — used by the auth mechanism (returns key + user)
  - `updateLastUsed(ID<ApiKey> keyId)` — best-effort timestamp update

#### 1d. Resource Layer

- **`ApiKeyResource`** (`api/src/main/java/org/chainlink/api/auth/ApiKeyResource.java`)
  - `@Authenticated` — requires web session (API keys cannot manage other API keys)
  - `POST /api/auth/api-keys` — create key
  - `GET /api/auth/api-keys` — list keys
  - `DELETE /api/auth/api-keys/{id}` — revoke key
  - Uses `CurrentUserService` to scope operations to the logged-in user
  - Returns `ApiKeyJson` DTOs (never exposes the hash)

#### 1e. Authentication Mechanism

- **`ApiKeyAuthenticationMechanism`** (`api/src/main/java/org/chainlink/infrastructure/auth/ApiKeyAuthenticationMechanism.java`)
  - Implements Quarkus `HttpAuthenticationMechanism`
  - Checks for `X-API-Key` header on every request
  - Strips `cl_` prefix, computes SHA-256, looks up via `ApiKeyService`
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

Implemented in `api/src/main/java/org/chainlink/api/auth/apikey/`.

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

### Phase 3: CLI Scaffolding

- [ ] Create `cli/` directory with `package.json`
- [ ] Set up TypeScript + commander
- [ ] Configure OpenAPI client generation (reuse `typescript-fetch` pipeline)
- [ ] Implement config management (`~/.chainlink/config.json`)
- [ ] Implement `chainlink login` / `chainlink logout`

### Phase 4: CLI Bookmark Commands

- [ ] `chainlink bookmarks add <url>`
- [ ] `chainlink bookmarks list`
- [ ] `chainlink bookmarks edit <id>`
- [ ] `chainlink bookmarks rm <id>`
- [ ] `chainlink collections list`

### Phase 5: Polish & Distribution

- [ ] Shell completions (bash/zsh/fish)
- [ ] `npm install -g @chainlink/cli` publishing setup
- [ ] README with installation instructions
- [ ] `--insecure` flag for self-signed certs

---

## Security Summary

| Asset | Threat | Mitigation |
|---|---|---|
| Raw API key | Stored in DB | Never stored; only SHA-256 hash is persisted |
| Raw API key | Logged | Never logged; only prefix appears in logs |
| `~/.chainlink/config.json` | Read by other users | File permissions set to `0600` |
| API key in transit | Intercepted | TLS required; no HTTP fallback |
| Key brute-force | Guessed by attacker | 32 bytes of entropy (2^256); infeasible |
| Key enumeration | Attacker probes keys | Constant-time hash comparison; generic error messages |
| Revoked key | Used after revocation | `revoked_at` checked on every request; immediate effect |
| CSRF via API key | Browser auto-sends key | API keys sent via header, not cookie; no CSRF risk |
