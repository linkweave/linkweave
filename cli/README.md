# @linkweave/cli

Manage LinkWeave bookmarks from the command line. The CLI talks exclusively to
the REST API of a LinkWeave server and authenticates with a personal API key
(created in the web UI under *Settings → API Keys*).

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

```bash
# Interactive: prompts for server URL and API key, validates the key,
# stores it in ~/.linkweave/config.json (chmod 0600)
linkweave login

# Non-interactive
linkweave login --server https://linkweave.dev --api-key lw_...

# Or skip login entirely (useful for CI): environment variables always win
export LINKWEAVE_API_KEY=lw_...
export LINKWEAVE_SERVER=https://linkweave.dev
```

## Commands

```
linkweave login [--server <url>] [--api-key <key>]
linkweave logout
linkweave bookmarks add <url> [--title <t>] [--collection <c>] [--folder <path>] [--tags <t1,t2>] [--description <d>]
linkweave bookmarks list [--collection <c>] [--folder <path>] [--tag <t>] [--format <fmt>]
linkweave bookmarks edit <id> [--title <t>] [--url <u>] [--description <d>] [--tags <t1,t2>]
linkweave bookmarks rm <id>
linkweave collections list [--format <fmt>]
```

Global flags: `--server/-s`, `--api-key/-k`, `--insecure` (disable TLS
verification — local development only). The list commands additionally take
`--format/-f` (`table`, `json`, `ids`).

Behavior worth knowing:

- `--collection` accepts an ID or a name (matched case-insensitively).
  Without it, your default collection is used.
- `--tags` takes comma-separated tag *names*; unknown tags are created.
- `--folder` takes a path like `Dev/TypeScript/Articles`; missing segments are
  created by `bookmarks add` (but not by `bookmarks list`).
- `rm` soft-deletes: the bookmark moves to the trashbin.
- `edit` replaces the whole bookmark (fetch → merge → save); a concurrent
  change made elsewhere between fetch and save is overwritten.
- Data goes to stdout, errors and warnings to stderr. Exit codes: `0` success,
  `1` error (auth/network/validation), `2` usage error. `--format=json` pipes
  cleanly into `jq`.

Against a local dev server with a self-signed certificate:

```bash
linkweave -s https://localhost:8443 --insecure bookmarks list
```

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
