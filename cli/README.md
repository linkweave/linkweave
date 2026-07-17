# @linkweave/cli

Manage LinkWeave bookmarks from the command line. The CLI talks exclusively to
the REST API of a LinkWeave server and authenticates with a personal API key
(created in the web UI under *Settings → API Keys*).

```bash
linkweave bookmarks add https://example.com --tags reading --folder Inbox
```

## Installation

```bash
npm install -g @linkweave/cli
```

From the monorepo (development):

```bash
cd cli
pnpm install
pnpm run build        # bundles to dist/main.js
node dist/main.js --help
```

## Getting started

Interactive — prompts for the server URL and API key (the key is not echoed),
validates the key against the server, and stores both in
`~/.linkweave/config.json` with owner-only permissions:

```bash
linkweave login
```

Non-interactive:

```bash
linkweave login --server https://linkweave.dev --api-key lw_...
```

Or skip `login` entirely — useful in CI and scripts:

```bash
export LINKWEAVE_API_KEY=lw_...
export LINKWEAVE_SERVER=https://linkweave.dev
linkweave bookmarks list
```

## Commands

### `linkweave bookmarks add <url>`

```bash
linkweave bookmarks add https://vuejs.org \
  --title "Vue docs" \
  --collection Work \
  --folder Dev/Frontend \
  --tags vue,docs \
  --description "Official Vue.js documentation"
```

Every flag is optional; a bare `add <url>` bookmarks the URL (title defaults
to the URL) into your default collection.

- `--collection` accepts an ID or a name, matched case-insensitively.
- `--folder` is a path like `Dev/Frontend`; missing segments are created.
- `--tags` is comma-separated tag *names*; unknown tags are created.

### `linkweave bookmarks list`

```bash
linkweave bookmarks list                          # table of your default collection
linkweave bookmarks list --collection Work        # other collection (ID or name)
linkweave bookmarks list --folder Dev --tag vue   # filters
linkweave bookmarks list --format json            # full JSON for scripting
linkweave bookmarks list --format ids             # one ID per line
```

Unlike `add`, `list` never creates folders — an unknown `--folder` path is an
error.

### `linkweave bookmarks edit <id>`

```bash
linkweave bookmarks edit 3f8a... --title "New title" --tags vue,reference
```

At least one flag is required. Only the given fields change, but note the
save is a whole-bookmark replace (fetch → merge → save): a concurrent change
made elsewhere between fetch and save is overwritten. `--tags` replaces the
complete tag list.

### `linkweave bookmarks rm <id>`

Soft-deletes: the bookmark moves to the trashbin and can be restored in the
web UI.

### `linkweave collections list`

```bash
linkweave collections list
linkweave collections list --format json
```

Shows ID, name, whether it is your default collection, your role, and whether
it is shared.

### `linkweave login` / `linkweave logout`

`login` stores the configuration (see below); `logout` deletes it. Logging in
again overwrites the stored identity after a warning.

### `linkweave completion <bash|zsh|fish>`

Prints a tab-completion script for your shell — see
[Shell completion](#shell-completion).

## Scripting

Data goes to stdout, errors and warnings to stderr, so output pipes cleanly:

```bash
linkweave bookmarks list --format json | jq -r '.[].url'
linkweave bookmarks list --format ids | while read -r id; do ...; done
```

Exit codes:

| Code | Meaning                                              |
| ---- | ---------------------------------------------------- |
| 0    | success                                              |
| 1    | error (authentication, network, not found, API)      |
| 2    | usage error (unknown flag, missing argument, bad format) |

## Configuration

`login` writes `~/.linkweave/config.json` (created with `0600`, written
atomically): server URL, API key, your email, and your default collection ID.

Precedence for the server and key: **flags** (`--server`/`--api-key`) >
**environment** (`LINKWEAVE_SERVER`/`LINKWEAVE_API_KEY`) > **config file**.
When the effective key or server differs from the stored one, the stored
default collection is not used (it may belong to a different account).

A corrupt config file is ignored with a warning — run `linkweave login` to
recreate it.

## Shell completion

Completions cover subcommands, flags, and `--format` values.

**bash** — add to `~/.bashrc`:

```bash
eval "$(linkweave completion bash)"
```

**zsh** — add to `~/.zshrc` *after* `compinit` runs:

```zsh
eval "$(linkweave completion zsh)"
```

**fish** — persist once:

```fish
linkweave completion fish > ~/.config/fish/completions/linkweave.fish
```

## TLS and local development

Against a local dev server with a self-signed certificate:

```bash
linkweave -s https://localhost:8443 --insecure bookmarks list
```

`--insecure` disables TLS certificate verification for the whole invocation —
only use it with servers you control.

## Development

The API client is not hand-written: it is the typescript-fetch client
generated from the server's OpenAPI spec, shared with the frontend
(`frontend/src/api/generated`, regenerate with `pnpm run generate-api` there).
`tsup` bundles it into `dist/main.js`, so the published package is
self-contained.

```bash
pnpm run check        # type-check + unit tests
pnpm run test         # vitest unit tests
pnpm run dev -- bookmarks list   # run from source via tsx
```

End-to-end tests live in `frontend/e2e/cli.spec.ts` and run as part of the
Playwright suite against a real server:

```bash
cd ../frontend && pnpm exec playwright test e2e/cli.spec.ts --project=chromium
```

Related docs: `docs/cli-tool.md`, use cases UC-079 (manage bookmarks via CLI)
and UC-080 (configure CLI login).
