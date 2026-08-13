# @linkweave/cli

Manage LinkWeave bookmarks from the command line. The CLI talks exclusively to
the REST API of a LinkWeave server and authenticates with a personal API key
(created in the web UI under *Settings → API Keys*).

```bash
linkweave bookmarks add https://example.com --tags reading --folder Inbox
linkweave search rust async     # find it again
linkweave open rust async       # open it in your browser
```

## Installation

Requires **Node.js 22 or newer** — the CLI ships as a JavaScript bundle run by
your own `node`, not as a standalone binary. Both 22 and 24 are exercised in
CI.

```bash
npm install -g @linkweave/cli
```

From a checkout instead — this builds the same bundle that gets published, and
puts `linkweave` on your PATH:

```bash
cd cli
pnpm install
pnpm run build        # bundles to dist/main.js
npm install -g .
```

Or skip the install and run it in place:

```bash
node dist/main.js --help
pnpm run dev -- bookmarks list   # from source, via tsx
```

## Getting started

Interactive — prompts for the server URL and API key (the key is not echoed),
validates the key against the server, and stores both in
`$XDG_CONFIG_HOME/linkweave/config.json` with owner-only permissions:

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

> **Prefer the interactive prompt or `LINKWEAVE_API_KEY` over `--api-key`.**
> A key passed as a flag is recorded in your shell history and is visible to
> any other user on the machine via `ps`. The interactive prompt does not echo
> the key, and `login` stores it with owner-only permissions.

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
complete tag list; `--tags ""` removes every tag.

### `linkweave bookmarks show <id>`

```bash
linkweave bookmarks show 3f8a...
linkweave bookmarks show 3f8a... --format json
```

Prints one bookmark as field/value rows: title, URL, description, collection,
folder path, tags, click count and timestamps. The table resolves tag IDs to
names and the folder ID to its path; `--format json` is the raw payload and
skips those lookups.

### `linkweave bookmarks export` / `linkweave bookmarks import <file>`

```bash
linkweave bookmarks export > backup.html          # or --output backup.html
linkweave bookmarks export --collection Work -o work.html
linkweave bookmarks import ~/Downloads/bookmarks.html
linkweave bookmarks import chrome.html --collection Archive
```

The format is the Netscape bookmarks HTML that every browser reads and writes,
so an export restores into Firefox or Chrome unchanged, and anything they
export imports here. Folder structure survives the round trip.

`export` writes to stdout unless `--output`/`-o` is given, so it redirects and
pipes like any other command. `import` adds to the target collection rather
than replacing it, and the server skips bookmarks whose URL is already there —
the summary line reports how many were created and how many were skipped.

The file must be `.html` or `.htm` and at most 5 MB; both are checked before
anything is uploaded.

### `linkweave bookmarks rm <id>`

Soft-deletes: the bookmark moves to the trashbin and can be restored in the
web UI or with `linkweave trash restore`.

### `linkweave collections`

```bash
linkweave collections list                       # ID, name, default, role, shared
linkweave collections create Archive
linkweave collections rename Archive "Cold storage"
linkweave collections default Work
linkweave collections rm Archive                 # permanent, asks first
```

`create`, `rename` and `rm` take a collection ID or a name, matched
case-insensitively. `rename` preserves the screenshot and fetch-allowlist
settings that the CLI does not otherwise expose.

Renaming is restricted to the collection's owner, and the server enforces that
by keeping the existing name rather than returning an error — so on a shared
collection where you are an admin rather than the owner, `rename` fails with
`Collection not renamed: '<name>' is unchanged` instead of reporting a success
that did not happen.

`default` changes your default collection server-side **and** updates the copy
stored on this machine, so the next `bookmarks add` targets it. It only
rewrites the stored config when the credentials in use are the stored ones — a
key passed via `--api-key` or `LINKWEAVE_API_KEY` may belong to someone else.

`rm` deletes the collection and every bookmark, folder, tag, auto-tag rule and
saved search in it. None of that reaches the trashbin, so it prompts; `--yes`
skips the prompt and without a terminal it refuses rather than assume consent.
The server will not let you delete your last remaining collection.

### `linkweave tags`

```bash
linkweave tags list                      # ID + name, alphabetical
linkweave tags rename java jvm
linkweave tags rm jvm --yes              # permanent, asks first
```

Tags are addressed by name or ID and scoped with `--collection`. `rename`
preserves the tag's colour. `rm` removes the tag from every bookmark that
carried it and cannot be undone, so it prompts.

### `linkweave folders`

```bash
linkweave folders list --format ids
linkweave folders create Dev/Rust/Async  # missing parents are created
linkweave folders rename Dev/Rust/Async Tokio
linkweave folders mv Dev/Rust/Tokio Dev  # '/' as the destination means the top level
linkweave folders rm Dev/Rust
```

Folders are shown and addressed as paths (`Dev/TypeScript`) — the same syntax
`--folder` accepts, so output can be piped straight back into `bookmarks add`.
A folder ID works anywhere a path does.

`create` behaves like `mkdir -p` for missing parents, but like plain `mkdir` it
refuses a path that already exists; `bookmarks add --folder` is the idempotent
way to create one in passing. `rename` changes the last segment only and leaves
the folder where it is — use `mv` to reparent it. `rm` soft-deletes the folder,
its subfolders and the bookmarks inside; all of it lands in the trashbin, so
there is no prompt and `trash restore` undoes it.

### `linkweave trash`

`bookmarks rm` soft-deletes; this is how you look at and undo that.

```bash
linkweave trash list                  # bookmarks and folders, newest first
linkweave trash restore <id>          # works for either; no need to say which
linkweave trash purge <id>            # permanent, asks first
linkweave trash empty                 # permanent, asks first
```

`purge` and `empty` cannot be undone, so they prompt. `--yes` skips the
prompt for scripts; without a terminal they refuse rather than assume consent.

### `linkweave search <query...>`

Finds bookmarks whose title, URL or tag names contain **every** word given —
the "I half-remember this link" command.

```bash
linkweave search rust                     # anywhere in title, URL or tags
linkweave search async book               # both words must match, in any field
linkweave search tokio --format ids       # for piping
```

Plain case-insensitive substrings, not the web UI's query language: `#tag`,
`under:folder` and negation are app-side syntax and are treated as literal text
here. Use `bookmarks list --tag/--folder` when you want to filter precisely.
Matching nothing prints a note on stderr and exits 0, like `grep`.

### `linkweave open <bookmark...>`

Opens a bookmark in your browser.

```bash
linkweave open 3f8a...                    # by ID
linkweave open vue docs                   # by words, if they match exactly one
linkweave open tokio --print              # print the URL instead of opening it
```

The words are matched the same way `search` matches them. Anything but exactly
one match is an error: nothing found says so, and several found lists the
candidates with their IDs rather than guessing — opening the wrong page is a
mistake you notice too late.

Opening records the click, exactly as clicking it in the web UI does, so click
counts and "never opened" stay meaningful when you work from a terminal.
`--print` does not: the URL may be going somewhere that never visits it.

Only `http` and `https` URLs are opened. In a shared collection the URL is
somebody else's input, and the platform handler opens far more than web pages —
`--print` still shows anything, since printing hands nothing to the system.

### `linkweave watch`

Follows a collection and prints changes as they happen — yours from another
device, a collaborator's, or a screenshot the server finished capturing after
the save returned.

```bash
linkweave watch                                  # your default collection
linkweave watch --collection Work                # by name or ID
linkweave watch --format json | jq -r .kind      # one JSON object per line
linkweave watch --retries 0                      # exit on the first drop
```

```
Watching collection 3f0e…. Press Ctrl-C to stop.
bookmark added (ebaf4e39-…) by Ada Lovelace
folder removed by Ada Lovelace
```

The connection is held open until you stop it. Keep-alive traffic is not
printed, and the "Watching…" line goes to stderr, so `--format json` pipes
cleanly into `jq` or a `while read` loop with nothing else mixed in.

If the connection drops it reconnects with an increasing, jittered delay,
giving up after `--retries` *consecutive* failures (default 6) — a connection
that runs and ends cleanly resets the count, so a watch left running for days
survives the odd blip. A stream that fails part-way through counts as a failure
even if it delivered something first, or a server that accepts and drops in a
loop would be retried forever. Refused connections, DNS hiccups, a reset mid-stream and
a restarting server (502/503/504) all count as blips and are retried.

Three things stop it immediately instead, because waiting will not fix them: a
revoked key, lost access to the collection, and a certificate the client will
not accept (use `--insecure`, or fix the certificate).

`--format` is `table` or `json` here — there is no `ids`, since folder and
collection events name no bookmark.

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

`watch` streams instead of returning, which makes it a source for a pipeline
that reacts to changes rather than polling for them:

```bash
linkweave watch --format json | while read -r event; do
  [ "$(jq -r .kind <<<"$event")" = 'BOOKMARK_ADDED' ] && notify-send 'New bookmark'
done
```

Exit codes:

| Code | Meaning                                              |
| ---- | ---------------------------------------------------- |
| 0    | success                                              |
| 1    | error (authentication, network, not found, API)      |
| 2    | usage error (unknown flag, missing argument, bad format) |

## Configuration

`login` writes `$XDG_CONFIG_HOME/linkweave/config.json` (default
`~/.config/linkweave/config.json`, created with `0600`, written atomically):
server URL, API key, your email, and your default collection ID.

The CLI follows the XDG Base Directory specification. `XDG_CONFIG_HOME` and
`XDG_CACHE_HOME` are honoured when set to an absolute path; otherwise
`~/.config` and `~/.cache` are used, on macOS as well as Linux.

Precedence for the server and key: **flags** (`--server`/`--api-key`) >
**environment** (`LINKWEAVE_SERVER`/`LINKWEAVE_API_KEY`) > **config file**.
When the effective key or server differs from the stored one, the stored
default collection is not used (it may belong to a different account).

A corrupt config file is ignored with a warning — run `linkweave login` to
recreate it.

## Shell completion

Completions cover subcommands, flags, and `--format` values. They also
complete **your own data**: `--collection`, `--tag`, and `--folder` are
completed from the server, and a `--collection` already on the command line
scopes the tag and folder suggestions to it.

```bash
linkweave bookmarks list --collection <TAB>   # My Links  Work
linkweave bookmarks list --tag <TAB>          # tags in your default collection
linkweave bookmarks add https://x --folder <TAB>   # Dev  Dev/TypeScript
```

The management commands complete their *arguments* from the server too, so the
thing you are about to rename, move or delete can be picked rather than typed:

```bash
linkweave folders mv <TAB>                    # Dev  Dev/TypeScript  Ops
linkweave folders mv Dev/TypeScript <TAB>     # the destination, likewise
linkweave tags rm <TAB>                       # dev  java
linkweave collections rename <TAB>            # My Links  Work
```

Only the argument naming an *existing* thing is completed. The `<new-name>` of
a `rename` is left alone — it is a name you are inventing, and offering the
names already in use would be worse than offering nothing. `--collection`
scopes these the same way it scopes option values.

Bookmark and trashbin IDs (`bookmarks show`, `trash restore`, …) are not
completed: a list of bare UUIDs is no use without the title beside it, which
the one-value-per-line callback has nowhere to put.

Those suggestions come from a hidden `linkweave __complete` callback, cached
for 60 seconds in `$XDG_CACHE_HOME/linkweave/completion-cache.json`
(owner-only, like the config) so a keypress never waits on the network twice.
If you are not logged in, offline, or the server is slow, completion silently
offers nothing rather than printing an error into your command line.

`--tags` (the comma-separated list on `add`/`edit`) is not value-completed;
use `--tag` filtering to discover names.

### When completion offers nothing

That silence also hides real faults — a server whose responses no longer match
the bundled API client looks identical to "you have no tags". To tell the two
apart, run the callback yourself. The completion scripts discard its stderr,
so it has to be invoked directly:

```bash
LINKWEAVE_DEBUG=1 linkweave __complete tags
```

Note that the same fault is not silent elsewhere: `linkweave collections list`
would fail loudly with the underlying error.

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

`linkweave login --insecure` records the opt-out for that server, so later
commands need no flag. That is also the only way tab completion can reach a
server with a self-signed certificate: the generated scripts invoke
`linkweave __complete` with no flags, so without the stored setting every
completion fails TLS and silently offers nothing.

The opt-out is keyed on the server, not on your identity — switching to
another server with `-s` verifies certificates normally. Every command that
runs without verification says so on stderr.

## Development

The API client is not hand-written: it is the typescript-fetch client
generated from the server's OpenAPI spec, shared with the frontend
(`frontend/src/api/generated`, regenerate with `pnpm run generate-api` there).
`tsup` bundles it into `dist/main.js`, so the published package carries the
client rather than fetching it. `commander` stays a normal dependency — tsup
leaves `dependencies` external — so a global install pulls two packages.

```bash
pnpm run check        # type-check + lint + dead-code + unit tests
pnpm run lint         # oxlint, reports only
pnpm run lint:fix     # oxlint --fix, rewrites what it can
pnpm run test         # vitest unit tests
pnpm run analyze:deadcode  # fallow: exports nobody imports
pnpm run dev -- bookmarks list   # run from source via tsx
```

The dead-code gate (`fallow`, as in the frontend) fails on an exported symbol
with no consumer. Specs count as consumers, so a helper exported purely to be
tested is fine; an export nobody imports at all is not. There is deliberately no
baseline file here — the package started clean and should stay that way.

Linting is `oxlint` alone (`.oxlintrc.json`) — the frontend's eslint layer
exists for Vue, and none of it applies here. `check` deliberately runs the
non-fixing `lint`: it is what `prepublishOnly` calls, and a publish is no place
to rewrite sources on the way past.

End-to-end tests live in `frontend/e2e/cli.spec.ts` and run as part of the
Playwright suite against a real server:

```bash
cd ../frontend && pnpm exec playwright test e2e/cli.spec.ts --project=chromium
```

## Releasing

The CLI versions independently of the application (AD-5 in
`docs/cli-tool.md`): the app ships on `v*` tags, the CLI on `cli-v*`.

```bash
cd cli
npm version patch          # or minor / major — updates package.json
git commit -am "chore(cli): release 0.1.1"
git tag cli-v0.1.1         # must match package.json exactly
git push && git push --tags
```

`.gitea/workflows/publish-cli.yml` takes it from there. It refuses to run if
the tag and `package.json` disagree, and does nothing if that version is
already on the registry, so a re-run is safe. `prepublishOnly` re-runs
type-check, lint, tests and the build, so a broken bundle cannot be published.

A `cli-v*` tag does not trigger `build.yml`, so cutting a CLI release never
rebuilds the container images, the frontend or the extension, and cannot
trigger a deploy.

Related docs: `docs/cli-tool.md`, use cases UC-079 (manage bookmarks via CLI)
and UC-080 (configure CLI login).

## Licence

MIT (see `LICENSE`), covering this package — the CLI sources and the API
client bundled into `dist/main.js`.

The rest of LinkWeave (server and web UI) is under the Business Source License
1.1. The client is deliberately separate: BUSL exists to stop someone reselling
the service, which is no reason to restrict a tool people install to talk to
their own instance.

Version `0.1.0` was published under BUSL-1.1 and stays that way — a licence
change applies only from `0.2.0` onward.
