# CLI Tool — Implementation Plan

**Status:** Ready for implementation
**Date:** 2026-06-20
**Audience:** Junior developer. Follow top-to-bottom; each phase has concrete files, commands, and acceptance checks.
**Implements:** UC-079 (Manage Bookmarks via CLI), UC-080 (Configure CLI Login). Relies on UC-078 (already shipped).

---

## 0. Context — read this first

The docs in `docs/cli-tool.md` describe 5 phases. **Phases 1 and 2 are already done** and you must NOT redo them:

- **Backend API-key auth (UC-077/UC-078) — DONE.** See `api/src/main/java/org/chainlink/api/auth/apikey/`:
  `ApiKeyResource` (`GET/POST/DELETE /api/auth/api-keys`), `ApiKeyAuthMechanism`, `ApiKeyService`, `ApiKeyIdentityProvider`. The server already authenticates the `X-API-Key` header.
- **Web UI for managing keys — DONE.** `frontend/src/components/apikey/`, `stores/apiKey.ts`. Users create/copy/revoke keys in the browser.

**Your job is only the CLI** (docs' Phases 3–5). The `cli/` directory does not exist yet.

### Toolchain decision (settled)

**TypeScript + `commander`, reusing the existing `typescript-fetch` OpenAPI client.** Distribute via npm **and** a compiled single binary (`bun build --compile`). This reuses the exact generator pipeline the frontend already runs (`frontend/scripts/generate-api.ts`, `@openapitools/openapi-generator-cli` 7.21) and keeps the monorepo on one client-generation toolchain. Go was considered and rejected: it would add a third language and a second client to maintain for a small CLI; single-binary distribution is achieved via `bun --compile` instead. This overrides nothing — it matches constraint C-020.

### The API contract you will call (verified against current code)

| Operation | HTTP | Body / params | Returns | Notes |
|---|---|---|---|---|
| Validate key / whoami | `GET /api/auth/me` | — | `UserInfoJson` | Used by `login` to verify the key. **Verify which field holds the default collection id** — if `UserInfoJson` lacks it, fall back to `GET /api/collections` (see §6.3). |
| List bookmarks | `GET /api/bookmarks?collectionId={id}` | `collectionId` **required** | `BookmarkListJson` | No server-side default collection — CLI must resolve it. |
| Create bookmark | `POST /api/bookmarks` | `BookmarkSaveJson` | `BookmarkJson` | — |
| Update bookmark | `PUT /api/bookmarks/{id}` | `BookmarkSaveJson` | `BookmarkJson` | Full replace — fetch current, merge changed fields, send back. |
| Delete bookmark | `DELETE /api/bookmarks/{id}` | — | — | Soft-delete (trashbin). |
| List collections | `GET /api/collections` | — | collection list | For name→id resolution and `collections list`. |
| List tags | `GET /api/tags?collectionId={id}` | — | tag list | For tag name→id resolution. |
| Create tag | `POST /api/tags` | tag create DTO | tag | Auto-create unknown tags (BR-019). |
| List folders | `GET /api/folders?collectionId={id}` | — | folder list | For folder name→id resolution. |
| Create folder | `POST /api/folders` | folder create DTO | folder | Auto-create unknown folders (BR-020). |

`BookmarkSaveJson` fields (confirmed): `collectionId`, `folderId`, `title`, `url`, `description`, `tagIds` (a **Set of tag IDs**, not names). Auth header is `X-API-Key`. The generated client exposes `Configuration`, `BookmarkResourceApi`, `AuthResourceApi`, `CollectionResourceApi`, `TagResourceApi`, `FolderResourceApi`.

> Before writing resolver code, run `pnpm run generate-api` (§4) and open the generated `apis/` and `models/` to confirm exact method names and field names. The names above are stable, but the generator decides the exact TypeScript casing.

---

## 1. Workspace setup

### 1.1 Create the `cli/` package

Create `cli/` at the repo root (sibling of `api/` and `frontend/`). If the repo uses a pnpm workspace, add `cli` to `pnpm-workspace.yaml` (check for that file first; if `frontend` is standalone, make `cli` standalone too with its own lockfile).

`cli/package.json`:
```json
{
  "name": "@chainlink/cli",
  "version": "0.1.0",
  "description": "Chainlink command-line interface",
  "type": "module",
  "bin": { "chainlink": "./dist/main.js" },
  "files": ["dist"],
  "engines": { "node": ">=20" },
  "scripts": {
    "generate-api": "tsx scripts/generate-api.ts",
    "build": "tsup src/main.ts --format esm --target node20 --clean",
    "build:binary": "bun build src/main.ts --compile --outfile dist/chainlink",
    "dev": "tsx src/main.ts",
    "type-check": "tsc --noEmit",
    "lint": "eslint .",
    "test": "vitest run"
  },
  "dependencies": {
    "commander": "^12.0.0"
  },
  "devDependencies": {
    "@openapitools/openapi-generator-cli": "^2.32.0",
    "tsup": "^8.0.0",
    "tsx": "^4.0.0",
    "typescript": "^5.4.0",
    "vitest": "^1.0.0"
  }
}
```

> Keep dependencies minimal. `commander` for arg parsing. No table library — write a tiny formatter (§7). No `chalk` — use ANSI codes guarded by `process.stdout.isTTY`. Fewer deps = smaller binary and faster startup.

`cli/tsconfig.json`: extend the frontend's compiler settings where reasonable; `target` ES2022, `module` NodeNext, `moduleResolution` NodeNext, `strict: true`, `outDir: dist`.

### 1.2 Acceptance for Phase 1
- `cd cli && pnpm install` succeeds.
- `pnpm exec tsx src/main.ts --version` prints a version once `main.ts` exists (§5).

---

## 2. CLI skeleton (`src/main.ts`)

Wire up commander with global flags and subcommands. Keep `main.ts` thin — it only registers commands and runs them.

```ts
#!/usr/bin/env node
import { Command } from 'commander'
import { registerLogin } from './commands/login.js'
import { registerLogout } from './commands/logout.js'
import { registerBookmarks } from './commands/bookmarks/index.js'
import { registerCollections } from './commands/collections/index.js'
import { CliError } from './errors.js'

const program = new Command()
program
  .name('chainlink')
  .description('Manage Chainlink bookmarks from the command line')
  .version(process.env.npm_package_version ?? '0.1.0', '-v, --version')
  .option('-s, --server <url>', 'Chainlink API server URL')
  .option('-k, --api-key <key>', 'API key (overrides config file)')
  .option('--insecure', 'Disable TLS verification (dev only)', false)
  .option('-f, --format <fmt>', 'Output format: table | json | ids', 'table')

registerLogin(program)
registerLogout(program)
registerBookmarks(program)
registerCollections(program)

program.parseAsync(process.argv).catch((err) => {
  if (err instanceof CliError) {
    process.stderr.write(err.message + '\n')
    process.exit(err.exitCode)
  }
  process.stderr.write(`Error: ${err.message ?? err}\n`)
  process.exit(1)
})
```

Global flags are read inside commands via `command.optsWithGlobals()`.

### Exit codes & streams (BR-017, BR-018) — enforce everywhere
- **0** success, **1** general error (auth/network/validation), **2** usage error (commander emits 2 automatically on bad flags).
- Data + success → **stdout**. Errors + warnings → **stderr**.
- Create `src/errors.ts` with `class CliError extends Error { constructor(message: string, public exitCode = 1) }`. Throw `CliError` with friendly messages; the top-level handler prints to stderr and sets the exit code.

---

## 3. Config management (`src/config.ts`) — UC-080

Responsibilities: locate, read, write, and delete `~/.chainlink/config.json`, and resolve effective credentials with env-var precedence.

```ts
export interface CliConfig {
  server: string
  apiKey: string
  userEmail?: string
  defaultCollectionId?: string
}
```

Rules (from UC-080 business rules):
- **Location (BR-021):** `path.join(os.homedir(), '.chainlink', 'config.json')`. Create the dir if missing (`fs.mkdirSync(dir, { recursive: true })`). `os.homedir()` handles Windows `%USERPROFILE%`.
- **Permissions (BR-022):** after writing, `fs.chmodSync(file, 0o600)` on non-Windows (`process.platform !== 'win32'`).
- **Env override (BR-023):** effective `apiKey = process.env.CHAINLINK_API_KEY ?? config.apiKey`; effective `server = flag --server ?? process.env.CHAINLINK_SERVER ?? config.server ?? DEFAULT_SERVER`. Precedence order: **flag > env > config file > built-in default**.
- **Default server:** `https://chainlink.markushofstetter.com`.

Functions to implement:
- `readConfig(): CliConfig | null` — returns null if file absent or unreadable.
- `writeConfig(cfg: CliConfig): void` — mkdir, write pretty JSON, chmod 600. On `EACCES`/`EPERM` throw `CliError('Error: Cannot write to ~/.chainlink/config.json. Check directory permissions.')` (A5).
- `deleteConfig(): void` — used by `logout` (BR-025).
- `resolveCredentials(globalOpts): { server: string; apiKey: string | null; insecure: boolean }` — applies the precedence rules above. Used by every command except `login`.

---

## 4. Generate the API client (reuse the frontend pipeline)

Copy `frontend/scripts/generate-api.ts` to `cli/scripts/generate-api.ts` and change only the output dir and (if needed) the spec URL. Keep all the same generator flags so the generated types match the frontend exactly:

- `-g typescript-fetch`
- `--global-property models,apis,supportingFiles`
- `-p supportsES6=true -p prefixParameterInterfaces=true -p fileNaming=kebab-case -p stringEnums=false`
- `--type-mappings entity-id=string,email=string,semantic-version=string,url=string`
- `--openapi-normalizer KEEP_ONLY_FIRST_TAG_IN_OPERATION=true`

Set `OUTPUT_DIR = 'src/api/generated'` and `OPENAPI_URL = 'https://local-chainlink.localhost:8443/q/openapi'` (the dev server; `rejectUnauthorized: false` is already in the script for the self-signed dev cert).

Run it with the Quarkus dev server up:
```bash
cd cli && pnpm run generate-api
```

**Commit the generated `src/api/generated/` directory** (the frontend does the same). Regenerate whenever the API changes. The generated client is fetch-based, so it runs unchanged on Node 20+ (global `fetch`) and in the bun binary.

> The generated client should NOT be hand-edited. If you need behavior changes, change the wrapper (§5) or regeneration flags.

---

## 5. API client wrapper (`src/client.ts`)

One factory that builds the generated `Configuration` with auth, base path, TLS handling, and error mapping. Every command uses this — never instantiate the generated APIs directly.

```ts
import { Configuration, BookmarkResourceApi, AuthResourceApi,
         CollectionResourceApi, TagResourceApi, FolderResourceApi } from './api/generated/index.js'
import { CliError } from './errors.js'

export interface ApiContext {
  bookmarks: BookmarkResourceApi
  auth: AuthResourceApi
  collections: CollectionResourceApi
  tags: TagResourceApi
  folders: FolderResourceApi
}

export function createApi(server: string, apiKey: string, insecure: boolean): ApiContext {
  if (insecure) {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'           // A7 / UC-080 A6
    process.stderr.write('⚠ TLS verification disabled. Only use this with trusted servers.\n')
  }
  const config = new Configuration({
    basePath: server.replace(/\/$/, '') + '/api',             // confirm /api root against generated paths
    headers: { 'X-API-Key': apiKey },                         // BR-011
  })
  return {
    bookmarks: new BookmarkResourceApi(config),
    auth: new AuthResourceApi(config),
    collections: new CollectionResourceApi(config),
    tags: new TagResourceApi(config),
    folders: new FolderResourceApi(config),
  }
}
```

### Error mapping helper (use in every command)
Wrap API calls in a helper that converts low-level failures into the exact UC error messages and exit codes:

```ts
export async function call<T>(fn: () => Promise<T>, ctx: { server: string }): Promise<T> {
  try {
    return await fn()
  } catch (e: any) {
    // typescript-fetch throws ResponseError with a `.response` (Response) on non-2xx
    const status = e?.response?.status
    if (status === 401) throw new CliError("Error: Authentication failed. Your API key may have been revoked. Run 'chainlink login' to reconfigure.")
    if (status === 403) throw new CliError("Error: Collection not found or access denied. Use 'chainlink collections list' to see your collections.")
    if (status === 404) throw new CliError('Error: Bookmark not found.')   // refine per-command with the id
    // network / TLS errors have no response
    const msg = String(e?.cause?.message ?? e?.message ?? '')
    if (/certificate|self-signed|TLS/i.test(msg)) throw new CliError('Error: TLS certificate verification failed. Use --insecure flag for local development only.')
    throw new CliError(`Error: Cannot reach Chainlink server at ${ctx.server}. Check your network connection and server URL.`)
  }
}
```

Map (UC-079 alt flows): A2→401, A3→network, A4→403, A5→404, A7→TLS. For per-command specificity (e.g. include the bookmark id in the 404 message), catch and rethrow with the id in the command.

---

## 6. Commands

Each command file exports a `register*(program)` that adds itself. Pattern per command: read global opts → resolve credentials → guard auth (A1) → build API → do work via `call()` → format output.

### 6.0 Auth guard (shared, UC-079 A1)
```ts
function requireAuth(globalOpts): { server; apiKey; insecure } {
  const c = resolveCredentials(globalOpts)
  if (!c.apiKey) throw new CliError("Error: Not authenticated. Run 'chainlink login' to configure your API key.")
  return c as { server: string; apiKey: string; insecure: boolean }
}
```

### 6.1 `login` (`src/commands/login.ts`) — UC-080
Supports three modes from UC-080:
1. **Non-interactive:** `chainlink login --api-key <k> --server <url>` — both provided as flags.
2. **Interactive:** prompt for server (default shown) then API key. Use Node's `readline/promises`; for the key prompt, do **not** echo if feasible (acceptable to echo for v1 — note it).
3. **Env-var:** if `CHAINLINK_API_KEY` is set, `login` is optional; other commands work directly.

Flow:
1. Determine server (flag → prompt/default) and key (flag → prompt).
2. **Validate format (A1/UC-080 step 6):** regex `^cl_[0-9a-f]{64}$`. On mismatch → interactive: reprompt; non-interactive: `CliError('Error: Invalid API key format. Expected: cl_ followed by 64 hex characters.', 2)`.
3. **Validate against server (BR-024):** `await call(() => auth.me(), {server})`. On 401 → `CliError('Error: API key rejected by server. The key may be invalid or revoked. Create a new key at ' + server + '/settings/api-keys')`; do not save.
4. **Save (A4):** if a config already exists, print `⚠ Overwriting existing configuration for {oldEmail}.` to stderr, then overwrite. Persist `server`, `apiKey`, `userEmail` (and `defaultCollectionId` if `/me` returns it — §6.3).
5. Print `✓ Logged in as {email}. Configuration saved to ~/.chainlink/config.json` to stdout.

### 6.2 `logout` (`src/commands/logout.ts`) — BR-025
Delete the config file, print `✓ Configuration removed. Run 'chainlink login' to authenticate again.`. If no file exists, still succeed (idempotent).

### 6.3 Resolvers (`src/commands/resolve.ts`) — shared by bookmark commands
- **`resolveCollectionId(api, opt): string`** (A8, BR for default collection):
  - No `--collection` → use `config.defaultCollectionId`. If not stored, call `GET /api/auth/me`; if that carries the default, use it; otherwise call `GET /api/collections` and if the user has exactly one collection use it, else `CliError` telling them to pass `--collection`.
  - Looks like a UUID → use as-is.
  - Otherwise treat as a **name**: `GET /api/collections`, case-insensitive match. 0 matches → `Error: No collection found with name '{name}'. Use 'chainlink collections list' to see your collections.` >1 → `Error: Multiple collections match '{name}'. Use the collection ID instead.`
- **`resolveTagIds(api, collectionId, names: string[]): string[]`** (BR-019, A6): `GET /api/tags?collectionId`, match by name (case-insensitive). For each unknown name, `POST /api/tags` to create it, collect the new id. Return all ids.
- **`resolveFolderId(api, collectionId, path: string): string`** (BR-020): split on `/`. Walk/create each segment under its parent via `GET/POST /api/folders`. Return the leaf id.

> Confirm the exact tag/folder create DTO shape from the generated `models/` before writing these. If folder creation needs a parent id, thread it through the walk.

### 6.4 `bookmarks add <url>` (`src/commands/bookmarks/add.ts`) — UC-079 Add
Flags: `--title --collection --folder --tags --description`.
1. `requireAuth`; build API.
2. `collectionId = resolveCollectionId(...)`.
3. `folderId = --folder ? resolveFolderId(...) : undefined`.
4. `tagIds = --tags ? resolveTagIds(..., split(',')) : []`.
5. `title = --title ?? <url>` (step 5: server does not auto-fetch titles).
6. `await call(() => api.bookmarks.create({ bookmarkSaveJson: { collectionId, folderId, title, url, description, tagIds } }), {server})`.
7. Print `✓ Bookmark created: {title} ({url})`.

### 6.5 `bookmarks list` (`src/commands/bookmarks/list.ts`) — UC-079 List
Flags: `--collection --folder --tag --format`.
1. Resolve collection id, `GET /api/bookmarks?collectionId`.
2. If `--folder`/`--tag` given, filter client-side (the list endpoint only takes `collectionId`).
3. Output per `--format` (§7): `table` (ID, Title, URL, Tags), `json` (raw array to stdout for `jq`), `ids` (one id per line).

### 6.6 `bookmarks edit <id>` (`src/commands/bookmarks/edit.ts`) — UC-079 Edit
Flags: `--title --url --description --tags`. `PUT` is a full replace, so:
1. There is no single-bookmark GET in the table above — confirm whether one exists; if not, fetch the collection list and find by id, or add a `GET /api/bookmarks/{id}` only if the backend already exposes it (**do not** add backend endpoints in this task; if missing, fetch via list and filter).
2. Merge: start from the existing bookmark's fields, override the ones passed as flags, resolve `--tags` to ids.
3. `await call(() => api.bookmarks.update({ bookmarkId: id, bookmarkSaveJson: merged }))`. On 404 → `Error: Bookmark not found: {id}` (A5).
4. Print `✓ Bookmark updated: {title}`.

### 6.7 `bookmarks rm <id>` (`src/commands/bookmarks/rm.ts`) — UC-079 Remove
`DELETE /api/bookmarks/{id}`; 404 → `Error: Bookmark not found: {id}`. Print `✓ Bookmark removed: {id}`.

### 6.8 `collections list` (`src/commands/collections/index.ts`)
`GET /api/collections`, format per `--format`. Used both standalone and as the discovery hint in several error messages.

---

## 7. Output formatting (`src/output.ts`)

- `printTable(rows: Record<string,string>[], columns: string[])` — compute max width per column, pad, write to stdout. Truncate long URLs/titles to keep rows readable (e.g. 60 chars + `…`). Only colorize headers when `process.stdout.isTTY`.
- `printJson(data)` — `process.stdout.write(JSON.stringify(data, null, 2) + '\n')`. No log lines mixed in (BR-018) so `| jq` works.
- `printIds(ids: string[])` — one per line to stdout.
- Success/`✓`/`⚠` messages → use a `notify()` helper that writes to **stderr** for warnings and **stdout** for success lines, per BR-018. (Success confirmation lines like `✓ Bookmark created` go to stdout; the `⚠ TLS disabled` and `⚠ Overwriting` warnings go to stderr.)

---

## 8. Testing

Add tests as you go (project rule: tests for every feature). Use `vitest`.

### 8.1 Unit tests (no network)
- `config.spec.ts`: env-var precedence (BR-023), 0600 perms set on write (BR-022, skip assertion on win32), path resolution, `readConfig` returns null when absent.
- `login` format validation (A1): valid/invalid key regex.
- `resolve.spec.ts`: collection name match (0/1/many → A8 messages), tag resolution creating unknown tags (BR-019), folder path walking (BR-020). Mock the API context.
- `output.spec.ts`: table alignment, `ids` format, json is pure JSON (no stray log lines).
- Error mapping: `call()` maps 401/403/404/network/TLS to the exact UC strings and exit codes.

### 8.2 Integration / E2E (against a real or mocked server)
- Stand up the Quarkus dev server, create an API key (via the existing web flow or a test fixture), then drive the built CLI as a child process and assert on stdout/stderr/exit code for the happy paths of add/list/edit/rm and login. Use `--insecure` against the local self-signed cert.
- Assert exit codes: success=0, bad flag=2, auth failure=1.

### 8.3 Commands to run before every commit
```bash
cd cli && pnpm run type-check && pnpm run lint && pnpm test
```

---

## 9. Build & distribution (UC distribution / NFR-023)

1. **npm:** `pnpm run build` (tsup → `dist/main.js`), publish `@chainlink/cli`. `bin.chainlink` makes `chainlink` available after `npm i -g`.
2. **Single binary:** `pnpm run build:binary` (`bun build --compile`) produces a standalone `chainlink` executable with no Node runtime dependency. Cross-compile per platform with `bun build --compile --target=bun-<os>-<arch>`. Attach binaries to GitHub Releases; a Homebrew formula / Scoop manifest can wrap them.
3. **README** in `cli/`: install instructions (npm + binary), `chainlink login`, examples, the `--insecure` dev note, and the env-var auth path for CI.

> The fetch-based generated client and the `commander`/Node-stdlib code are bun-compatible. If a dependency ever isn't, prefer replacing it over abandoning the binary path.

---

## 10. Phase checklist (track with TaskCreate if you like)

**Phase A — scaffold**
- [ ] `cli/` package, tsconfig, lint config, workspace wiring
- [ ] `main.ts` skeleton + `errors.ts` + global flags + exit-code handler

**Phase B — plumbing**
- [ ] `config.ts` (read/write/delete, perms, env precedence) + tests
- [ ] `scripts/generate-api.ts` + commit generated client
- [ ] `client.ts` factory + `call()` error mapping + tests

**Phase C — auth commands**
- [ ] `login` (3 modes, format + server validation, overwrite warning) + tests
- [ ] `logout` + test

**Phase D — bookmark commands**
- [ ] resolvers (collection/tag/folder) + tests
- [ ] `bookmarks add` / `list` / `edit` / `rm`
- [ ] `collections list`
- [ ] output formatters + tests

**Phase E — ship**
- [ ] E2E against dev server
- [ ] `build` + `build:binary` verified
- [ ] README + (optional) shell completions
- [ ] Update statuses: set UC-079 and UC-080 to Done; refresh `docs/cli-tool.md` Phase 3–5 checkboxes

---

## 11. Acceptance criteria (definition of done)

- All Main Success Scenarios of UC-079 and UC-080 work end-to-end against a live server using a real API key.
- Every alternative flow produces the exact error message and exit code specified in the UCs (A1–A8 for UC-079; A1–A6 for UC-080).
- BR-015 holds: the CLI calls only the HTTP API (no DB access).
- BR-016 holds: every command runs non-interactively given flags/args (`login` may prompt only when run with no flags).
- BR-017/BR-018 hold: correct exit codes; data on stdout, errors/warnings on stderr; `chainlink bookmarks list --format=json | jq '.[0].url'` works.
- `pnpm run type-check`, `pnpm run lint`, and `pnpm test` all pass in `cli/`.
- Both distribution artifacts build: npm package and single binary.

---

## 12. Open questions to confirm during implementation (don't guess — check the code/generated client)

1. Does `UserInfoJson` include the default collection id? If not, `login` and `resolveCollectionId` use `GET /api/collections` instead. (§6.1, §6.3)
2. Exact base path: is it `{server}/api/...`? Confirm against generated operation paths and `quarkus.http.root-path`/`@ApplicationPath`. (§5)
3. Is there a single-bookmark `GET /api/bookmarks/{id}`? If not, `edit` reconstructs the current bookmark from the collection list. (§6.6)
4. Exact create DTO shapes for tags and folders (and whether folder create needs a parent id). (§6.3)
5. Does the generated `typescript-fetch` `ResponseError` expose `.response.status` as assumed in `call()`? Adjust the mapping to the real shape. (§5)
