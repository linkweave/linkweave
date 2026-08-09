# CLI Tool & API Key Architecture

**Status:** Done — all six phases complete. `@linkweave/cli@0.1.0` is published; releases run from a `cli-v*` tag (AD-5).
**Date:** 2026-05-10 (updated 2026-08-09)

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

> **Amended by AD-5 (2026-08-03):** the last point no longer holds. Sharing a
> repository still gives the first two benefits, but the CLI is released on its
> own `cli-v*` tag rather than the application's `v*`.

### AD-2b: CLI Reuses the Frontend's Generated Client (2026-07-15)

**Decision:** The CLI imports the checked-in typescript-fetch client from
`frontend/src/api/generated` (via `cli/src/api.ts`) and tsup bundles it into
`cli/dist/main.js`, so the published package carries the client itself and
refers to nothing in the monorepo. Note this does not make it dependency-free:
tsup treats `dependencies` as external, so `commander` is still resolved at
install time (NFR-023).

**Deliberately deferred:** extracting the client into a shared workspace
package (root pnpm workspace + `packages/api-client`). With only two
consumers and standalone-package CI, the restructuring isn't worth it yet.

**Revisited at first publish (2026-08-03):** bundling held up — the published
package is four files and needs no workspace resolution at install time. The
remaining trigger is a third consumer (desktop app, screenshot-service).

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

### AD-5: CLI Versions and Releases Independently (2026-08-03)

**Decision:** The CLI has its own version line and its own tag prefix,
`cli-v<semver>` (e.g. `cli-v0.1.0`), published by
`.gitea/workflows/publish-cli.yml`. The application keeps `v<semver>`.

**Rationale:**
- The CLI is at `0.1.0` while the application is at `1.5.0`. Publishing the
  CLI's first release as `1.5.0` would claim a maturity it does not have and
  burn every `0.x` number that signals "the flags may still move".
- The two ship on different clocks. A CLI bug fix should not wait for an
  application release, and an application release should not push a new CLI
  version at users for no reason.
- npm versions are immutable. Coupling to a tag that is cut for unrelated
  reasons spends version numbers that cannot be reclaimed.

**Consequences:**
- `cli-v*` is deliberately *not* a trigger in `build.yml`, so a CLI release
  never rebuilds the container images, the frontend or the extension, and
  cannot reach the `deploy` job (which gates on `refs/tags/v`).
- The publish workflow refuses to run when the tag and `cli/package.json`
  disagree, and no-ops when that version is already on the registry.
- The compatibility guarantee is unchanged: the CLI is built against the
  checked-in generated client (AD-2b), so CI still breaks on an API change
  regardless of which tag ships when.

### AD-6: The CLI Package Is MIT, the Rest Stays BUSL (2026-08-04)

**Decision:** `cli/` is licensed MIT, covering its sources and the generated
API client bundled into `dist/main.js`. The server and web UI keep BUSL-1.1.

**Rationale:**
- BUSL grants non-production use only until its Change Date, and its Licensed
  Work is "LinkWeave" — so as written it did not permit someone to run
  `npm install -g @linkweave/cli` and use it for work. That is not what the
  licence is for. BUSL protects against a competitor reselling the *service*;
  a client people install to talk to their own instance is not that.
- Corporate licence scanners flag BUSL as restricted, which would have blocked
  the CLI in exactly the environments a bookmark manager is used in, while
  protecting nothing.

**Consequences:**
- `0.1.0` was published under BUSL-1.1 and cannot be changed; the MIT grant
  starts at `0.2.0`.
- The bundled API client ships under MIT as a result. It is generated from our
  own OpenAPI spec and authored solely by the Licensor, so this is ours to
  grant — but it is a deliberate grant, not a side effect of bundling.
- The repository root LICENSE remains BUSL-1.1; `cli/LICENSE` is the narrower
  grant for that subtree.

---

## Repository Structure

```
linkweave/
├── api/                                     # Quarkus backend
│   └── src/main/java/org/linkweave/api/auth/
│       ├── AuthResource.java                # Existing
│       └── apikey/                          # API key credential type
│           ├── ApiKeyResource.java          # CRUD for API keys
│           ├── ApiKeyService.java
│           ├── ApiKeyRepo.java
│           ├── ApiKeyAuthMechanism.java     # X-API-Key -> SecurityIdentity
│           ├── ApiKeyIdentityProvider.java
│           └── ...
├── frontend/                                # Vue.js frontend
│   └── src/api/generated/                   # typescript-fetch client, shared
│                                            # with the CLI (see AD-2b)
├── cli/                                     # CLI tool
│   ├── package.json                         # @linkweave/cli, bin -> dist/main.js
│   ├── tsup.config.ts                       # bundles src + generated client
│   ├── vitest.config.ts
│   ├── README.md                            # user-facing CLI docs
│   └── src/
│       ├── main.ts                          # bin entry: sets process.exitCode
│       ├── run.ts                           # parse argv, map failures -> exit code
│       ├── program.ts                       # commander command tree
│       ├── api.ts                           # re-exports the frontend's generated client
│       ├── client.ts                        # API clients with X-API-Key injection
│       ├── config.ts                        # XDG config path (0600, atomic)
│       ├── cache.ts                         # 60s completion-candidate cache
│       ├── errors.ts                        # HTTP/TLS failures -> user-facing messages
│       ├── output.ts                        # --format table/json/ids rendering
│       ├── resolve.ts                       # collection/tag/folder name -> ID
│       ├── *.spec.ts                        # vitest unit tests, beside their subject
│       └── commands/
│           ├── loginCmd.ts                  # linkweave login
│           ├── logoutCmd.ts                 # linkweave logout
│           ├── bookmarksCmd.ts              # bookmarks add|list|show|edit|rm|
│           │                                #           export|import
│           ├── collectionsCmd.ts            # collections list|create|rename|
│           │                                #             default|rm
│           ├── tagsCmd.ts                   # tags list|rename|rm
│           ├── foldersCmd.ts                # folders list|create|rename|mv|rm
│           ├── trashCmd.ts                  # trash list|restore|purge|empty
│           ├── completeCmd.ts               # hidden __complete value callback
│           ├── commandHelpers.ts            # config + error-handling plumbing
│           └── completionScriptGenerator.ts # emits the bash/zsh/fish script
│                                            # (the last two carry no Cmd suffix:
│                                            #  they are helpers, not commands)
├── frontend/e2e/cli.spec.ts                 # CLI e2e, drives the built binary
├── docs/                                    # Documentation (this file)
└── ...
```

Notes on the layout as built:

- **Commands are one file per group, not a directory per group.** `bookmarks
  add|list|show|edit|rm` are five exported `runBookmarks*` functions in
  `bookmarksCmd.ts`; they share collection/tag/folder resolution, and splitting
  them across five files bought nothing. `program.ts` owns the whole commander
  tree, so the flag surface is readable in one place.
- **`main.ts` is only the bin entry.** Argument parsing and the
  `CommanderError`/`CliError`/unknown → exit-code mapping live in `run.ts`, so
  they can be unit-tested without spawning a process; `main.ts` just assigns
  `process.exitCode` (never `process.exit()`, which would truncate a piped
  stdout).
- **No `cli/src/api/generated/`.** The CLI imports the frontend's checked-in
  client through `cli/src/api.ts` and tsup inlines it at build time (AD-2b).
- **No `cli/tests/`.** Unit tests sit next to their subject as `*.spec.ts`;
  end-to-end tests live in the Playwright suite because they need a running
  server.

---

## Backend Changes

### Phase 1: API Key Infrastructure

#### 1a. Entity & Repository

- **`ApiKey` entity** (`api/src/main/java/org/linkweave/api/auth/apikey/ApiKey.java`)
  - Fields: `id`, `user` (ManyToOne), `name`, `keyHash`, `keyPrefix`, `createdAt`, `lastUsedAt`, `revokedAt`
  - Extends `AbstractEntity` (gets `userErstellt`/`userMutiert` for audit)
  - `@Column` lengths from `DbConst`

- **`ApiKeyRepo`** (`api/src/main/java/org/linkweave/api/auth/apikey/ApiKeyRepo.java`)
  - `findByKeyHash(String hash)` — for auth lookup
  - `findActiveByUserId(ID<User> userId)` — for listing
  - `countActiveByUserId(ID<User> userId)` — for max-key enforcement

#### 1b. Flyway Migration

- **`V{next}__create_api_key_table.sql`**
  - Creates `api_key` table with columns as defined in [UC-077 data model](use_cases/UC-077-manage-api-keys.md#data-model)
  - Foreign key to `user` table with `ON DELETE CASCADE`
  - Indexes on `key_hash` and `user_id`

#### 1c. Service Layer

- **`ApiKeyService`** (`api/src/main/java/org/linkweave/api/auth/apikey/ApiKeyService.java`)
  - `createApiKey(ID<User> userId, String name)` — generates key, hashes, stores, returns raw key once
  - `listActiveKeys(ID<User> userId)` — returns all non-revoked keys for the user
  - `revokeKey(ID<User> userId, ID<ApiKey> keyId)` — sets `revokedAt`
  - `findActiveKeyByHash(String hash)` — used by the auth mechanism (returns key + user)
  - `updateLastUsed(ID<ApiKey> keyId)` — best-effort timestamp update

#### 1d. Resource Layer

- **`ApiKeyResource`** (`api/src/main/java/org/linkweave/api/auth/apikey/ApiKeyResource.java`)
  - `@Authenticated` — requires web session (API keys cannot manage other API keys)
  - `POST /api/auth/api-keys` — create key
  - `GET /api/auth/api-keys` — list keys
  - `DELETE /api/auth/api-keys/{id}` — revoke key
  - Uses `CurrentUserService` to scope operations to the logged-in user
  - Returns `ApiKeyJson` DTOs (never exposes the hash)

#### 1e. Authentication Mechanism

- **`ApiKeyAuthMechanism`** + **`ApiKeyIdentityProvider`** (`api/src/main/java/org/linkweave/api/auth/apikey/`)
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
- [x] Implement config management (XDG config dir, 0600, env
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

- [x] Shell completions (bash/zsh/fish): `linkweave completion <shell>` prints
      a script generated at runtime from the commander command tree, so new
      commands/flags are picked up automatically (`cli/src/commands/completionScriptGenerator.ts`)
- [x] Value completion for `--collection`/`--tag`/`--folder` via a hidden
      `linkweave __complete` callback (`cli/src/commands/completeCmd.ts`), cached
      60s in `$XDG_CACHE_HOME/linkweave/completion-cache.json`. Fails silently:
      an error printed during completion corrupts the user's command line.
      Generated bash targets 3.2 (macOS), so no `mapfile`.
- [x] Packaging verified: `npm pack` produces a 25 kB tarball (LICENSE, README,
      package.json and the single bundled `dist/main.js`), and installing it
      globally yields a working `linkweave` binary with the right shebang and
      bin symlink. Guarded on every PR by the `test-cli` job, which packs the
      package and installs it into a throwaway prefix.
- [x] Release automation: `.gitea/workflows/publish-cli.yml`, triggered by a
      `cli-v*` tag (AD-5). Verifies the tag against `cli/package.json`, no-ops
      when that version is already on the registry, and publishes with
      `pnpm publish --no-git-checks --access public`.
- [x] Published: `@linkweave/cli@0.1.0` went to npm on 2026-08-03, public,
      4 files / 140 kB unpacked. Verified by installing it anonymously from the
      registry into a throwaway prefix — `--version`, `--help`, completion and
      the error paths all work off the published artefact.
      - The `@linkweave` scope was free. An earlier reading of
        `npmjs.com/org/linkweave` as "taken" was wrong: that page answers `403`
        for every org, existing or not.
      - A brand-new package is briefly invisible to unauthenticated registry
        reads. Do not read a 404 in the first minutes as a failed publish.
- [x] Licence settled: the CLI package is MIT from `0.2.0` (AD-6). `0.1.0`
      remains BUSL-1.1 on the registry — npm versions are immutable.
- [x] README with installation instructions (`cli/README.md`)
- [x] `--insecure` flag for self-signed certs

### Phase 6: CLI Management Commands — ✅ Done

The read/add surface above left the CLI unable to organise anything: folders
only appeared as a side effect of `bookmarks add --folder`, and collections and
tags could only be listed.

- [x] `linkweave bookmarks show <id>` — every field of one bookmark; the table
      resolves tag IDs to names and the folder ID to its path, `--format json`
      skips both lookups
- [x] `linkweave collections create|rename|default|rm`. `rename` reads the
      collection back first: the update payload also carries the screenshot
      toggle and fetch allowlist, which the CLI does not expose and would
      otherwise reset. It then checks the *response* — renaming is owner-only
      and `CollectionService` enforces that by keeping the existing name rather
      than refusing, so an admin's rename is a 200 that changed nothing and
      would otherwise have been reported as success. `default` also rewrites
      the `defaultCollectionId` stored at login — commands prefer that copy, so
      changing it server-side alone would look like a no-op from the next
      invocation on
- [x] `linkweave tags rename|rm` — `rename` preserves the tag colour for the
      same reason
- [x] `linkweave folders create|rename|mv|rm`. `rename` sends the current
      `parentId` back: `FolderService.updateFolder` reads an absent parent as
      "move to the root", so omitting it silently re-homes the folder *and its
      whole subtree* to the top level (confirmed against a running server, and
      pinned by an e2e test)
- [x] Confirmation prompts follow what is recoverable, not how the verb sounds:
      `collections rm` and `tags rm` delete outright and prompt; `folders rm`
      cascades to sub-folders and contained bookmarks but is a soft delete that
      `trash restore` undoes, so it does not — the same bargain `bookmarks rm`
      makes
- [x] Positional-argument completion for the commands above: the generated
      scripts work out which argument slot the cursor is in (command words
      typed, less the command's own path) and call the same `__complete`
      callback the option values use. A `<new-name>` slot completes nothing.
      Bookmark and trashbin IDs are excluded — a list of bare UUIDs is no use
      without a title, which the one-value-per-line callback cannot carry
- [x] `linkweave bookmarks export|import` — the Netscape bookmark file every
      browser reads, so an export restores into Firefox or Chrome unchanged.
      Export uses the generated client's `*Raw` variant: the endpoint produces
      `text/html`, which the generator maps to a `void` response, so the typed
      call would discard the body. Import sends a `File` rather than a `Blob`
      because the generated client appends the form field without a filename
      argument, and the server rejects an upload whose name does not end
      `.html`/`.htm`. Extension, emptiness and the 5 MB cap are all checked
      before the upload
- [x] The generated scripts are exercised in real bash, zsh and fish
      (`cli/src/commands/completionShell.spec.ts`), which is the only way to
      test the slot arithmetic; the older generator tests only inspect strings

---

## Security Summary

| Asset | Threat | Mitigation |
|---|---|---|
| Raw API key | Stored in DB | Never stored; only SHA-256 hash is persisted |
| Raw API key | Logged | Never logged; only prefix appears in logs |
| `$XDG_CONFIG_HOME/linkweave/config.json` | Read by other users | File permissions set to `0600` |
| API key in transit | Intercepted | TLS required; no HTTP fallback |
| Key brute-force | Guessed by attacker | 32 bytes of entropy (2^256); infeasible |
| Key enumeration | Attacker probes keys | Constant-time hash comparison; generic error messages |
| Revoked key | Used after revocation | `revoked_at` checked on every request; immediate effect |
| CSRF via API key | Browser auto-sends key | API keys sent via header, not cookie; no CSRF risk |
