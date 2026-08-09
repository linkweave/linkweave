# Use Case: Manage Bookmarks via CLI

## Overview

**Use Case ID:** UC-079
**Use Case Name:** Manage Bookmarks via CLI
**Primary Actor:** CLI User
**Goal:** Create, list, inspect, edit, and delete bookmarks from the command line — along with the collections, tags and folders they are organised into, and the trashbin they are recovered from — so that the user can manage a bookmark library from a terminal or shell script without opening a browser.
**Status:** Done

## Traceability

**Maps to:** FR-085, NFR-022, C-019, C-020

---

## Preconditions

- The user has installed the LinkWeave CLI (`npm install -g @linkweave/cli`).
- The user has configured their CLI credentials via UC-080 (`linkweave login`).
- The LinkWeave API server is reachable at the configured URL.
- The user has at least one collection (auto-provisioned on first web login).

## Main Success Scenario — Add Bookmark

1. User runs `linkweave bookmarks add <url>` with optional flags `--title`, `--collection`, `--folder`, `--tags`, `--description`.
2. CLI reads the API key from `$XDG_CONFIG_HOME/linkweave/config.json` (or `LINKWEAVE_API_KEY` env var).
3. CLI reads the server URL from config (default: `https://linkweave.dev`).
4. If `--collection` is not specified, CLI calls `GET /api/auth/me` to retrieve the user's default collection ID.
5. If `--title` is not specified, CLI uses the URL as a fallback title (the server does not auto-fetch titles via the API).
6. CLI sends `POST /api/bookmarks` with the `X-API-Key` header and a `BookmarkSaveJson` body.
7. Server authenticates via UC-078, authorizes via `AuthorizationService.requireCollectionAccess()`, and creates the bookmark.
8. CLI receives the created bookmark and displays a success message: `✓ Bookmark created: {title} ({url})`.

## Main Success Scenario — List Bookmarks

1. User runs `linkweave bookmarks list` with optional flags `--collection`, `--folder`, `--tag`, `--format`.
2. CLI resolves the collection ID (default collection if not specified).
3. CLI sends `GET /api/bookmarks?collectionId={id}` with the `X-API-Key` header.
4. Server returns all bookmarks in the collection.
5. CLI displays the bookmarks in the requested format:
   - `--format=table` (default): renders a table with columns ID, Title, URL, Tags.
   - `--format=json`: outputs raw JSON to stdout (for piping to `jq` or other tools).
   - `--format=ids`: outputs one bookmark ID per line (for use in shell loops).

## Main Success Scenario — Show Bookmark

1. User runs `linkweave bookmarks show <bookmarkId>` with an optional `--format`.
2. CLI sends `GET /api/bookmarks/{bookmarkId}` with the `X-API-Key` header.
3. For `--format=table` (default) the CLI additionally fetches the collection's tags, and its folders when the bookmark sits in one, so the record reads in names rather than IDs.
4. CLI displays one field per row: ID, Title, URL, Description, Collection, Folder, Tags, Clicks, Last clicked, Created, Updated.
5. `--format=json` prints the raw payload and skips step 3; `--format=ids` prints the ID alone.

## Main Success Scenario — Export and Import

1. User runs `linkweave bookmarks export`, optionally with `--collection` and `--output`.
2. CLI sends `GET /api/collections/{collectionId}/export` and receives a Netscape bookmark file (`text/html`) — the interchange format every browser reads and writes.
3. Without `--output` the HTML goes to stdout, so it redirects and pipes like any other command output (BR-018); with it, the CLI writes the file and reports the path.
4. User runs `linkweave bookmarks import <file>` to go the other way.
5. CLI checks the file locally before uploading: the name must end `.html`/`.htm`, and the content must be non-empty and at most 5 MB — the same constraints `ImportResource` enforces, checked first so a doomed upload is not spent.
6. CLI sends `POST /api/collections/{collectionId}/import` as `multipart/form-data`.
7. Server parses the file, merging folders by path and skipping bookmarks whose URL is already in the collection — an import adds to a collection, it does not replace it.
8. CLI reports the `ImportSummaryJson`: bookmarks created, folders created, and duplicates skipped.

## Main Success Scenario — Manage Collections, Tags and Folders

1. User runs one of the management commands (see the Command Reference below), e.g. `linkweave folders mv Dev/TypeScript Archive`.
2. CLI resolves the target collection as for any other command (step 4 of Add Bookmark), then resolves the named tag or folder path to its ID within that collection.
3. For an update, CLI first fetches the current record: the API replaces the whole entity, so fields the CLI does not expose are read back and resent unchanged (BR-028).
4. CLI sends the corresponding request — `POST`/`PUT`/`DELETE /api/collections`, `/api/tags`, `/api/folders`, or `PATCH /api/folders/{id}/move`.
5. Server authenticates via UC-078 and authorizes via `AuthorizationService`. Deleting a collection is owner-only. Renaming one is too, but the server enforces that by *keeping the existing name* rather than refusing: an admin's rename returns 200 with nothing changed, so the CLI compares the returned name against the old one and reports a failure instead of echoing back the name it asked for.
6. CLI displays a success message naming what changed, e.g. `✓ Folder moved: Dev/TypeScript → Archive/TypeScript`.

Deleting a collection or a tag cannot be undone and is gated on a confirmation (BR-027). `linkweave collections default <collection>` additionally rewrites the `defaultCollectionId` stored at login, because later commands prefer that copy over asking the server (UC-080).

## Main Success Scenario — Inspect and Recover the Trashbin

1. User runs `linkweave trash list` to see soft-deleted bookmarks and folders as one table, newest first.
2. `linkweave trash restore <id>` restores either kind without the user having to say which — the CLI looks the ID up in the trashbin first and picks the matching endpoint.
3. `linkweave trash purge <id>` and `linkweave trash empty` delete permanently, and are gated on a confirmation (BR-027).

## Main Success Scenario — Edit Bookmark

1. User runs `linkweave bookmarks edit <bookmarkId>` with optional flags `--title`, `--url`, `--description`, `--tags`.
2. CLI fetches the current state via `GET /api/bookmarks/{bookmarkId}` (the PUT body is a full `BookmarkSaveJson`, so unspecified fields are carried over).
3. CLI sends `PUT /api/bookmarks/{bookmarkId}` with the `X-API-Key` header and the merged `BookmarkSaveJson` body.
4. Server authenticates, authorizes (both the bookmark's current collection and the target collection), and updates the bookmark.
5. CLI displays a success message: `✓ Bookmark updated: {title}`.

## Main Success Scenario — Remove Bookmark

1. User runs `linkweave bookmarks rm <bookmarkId>`.
2. CLI sends `DELETE /api/bookmarks/{bookmarkId}` with the `X-API-Key` header.
3. Server authenticates, authorizes, and soft-deletes the bookmark (moves to trashbin).
4. CLI displays a success message: `✓ Bookmark removed: {bookmarkId}`.

## Alternative Flows

### A1: Not Authenticated

**Trigger:** CLI cannot find an API key in config or env var (step 2).
**Flow:**

1. CLI displays an error: `Error: Not authenticated. Run 'linkweave login' to configure your API key.`
2. CLI exits with code 1.

### A2: API Key Revoked or Invalid

**Trigger:** Server rejects the request with HTTP 401 (step 7 of any scenario).
**Flow:**

1. CLI displays: `Error: Authentication failed. Your API key may have been revoked. Run 'linkweave login' to reconfigure.`
2. CLI exits with code 1.

### A3: Server Unreachable

**Trigger:** The HTTP request fails due to network error (any step).
**Flow:**

1. CLI displays: `Error: Cannot reach LinkWeave server at {url}. Check your network connection and server URL.`
2. CLI exits with code 1.

### A4: Collection Not Found or No Access

**Trigger:** Server returns HTTP 403 for the collection ID (step 7).
**Flow:**

1. CLI displays: `Error: Collection not found or access denied. Use 'linkweave collections list' to see your collections.`
2. CLI exits with code 1.

### A5: Bookmark Not Found

**Trigger:** Server returns HTTP 404 for a bookmark ID (edit/remove scenarios).
**Flow:**

1. CLI displays: `Error: Bookmark not found: {bookmarkId}`
2. CLI exits with code 1.

### A6: Add Bookmark with Tags That Don't Exist

**Trigger:** User specifies `--tags=dev,api` but those tags don't exist in the collection (step 6).
**Flow:**

1. Server validates tag IDs. The CLI sends tag names, not IDs.
2. If the API expects tag IDs (current design), CLI must first resolve tag names to IDs by calling `GET /api/tags?collectionId={id}` and matching by name.
3. If a tag name is not found, CLI creates it by calling `POST /api/tags` with the tag name.
4. CLI then sends the bookmark creation request with the resolved tag IDs.
5. This behavior must be documented: `--tags` accepts tag names; unknown tags are auto-created.

### A7: Self-Signed Certificate in Dev

**Trigger:** Developer uses CLI against a local server with self-signed TLS certificate.
**Flow:**

1. CLI rejects the connection with a TLS error.
2. CLI displays: `Error: TLS certificate verification failed. Use --insecure flag for local development only.`
3. User re-runs with `--insecure` flag, which sets `NODE_TLS_REJECT_UNAUTHORIZED=0` for that request.
4. CLI displays a warning: `⚠ TLS verification disabled. Only use this with trusted servers.`

### A8: Collection Name Instead of ID

**Trigger:** User specifies `--collection=my-links` (a name) instead of a UUID.
**Flow:**

1. CLI calls `GET /api/collections` to list the user's collections.
2. CLI matches the collection name case-insensitively.
3. If exactly one match is found, CLI uses that collection's ID.
4. If multiple matches are found, CLI displays: `Error: Multiple collections match '{name}'. Use the collection ID instead.`
5. If no match is found, CLI displays: `Error: No collection found with name '{name}'. Use 'linkweave collections list' to see your collections.`

## Postconditions

### Success Postconditions (Add)

- A new bookmark exists in the specified collection on the server.
- The CLI displayed a success message with the bookmark title and URL.

### Success Postconditions (List)

- The CLI displayed the bookmarks in the requested format.
- No data was modified.

### Success Postconditions (Edit)

- The bookmark's fields are updated on the server.
- The CLI displayed a success message.

### Success Postconditions (Remove)

- The bookmark is soft-deleted (moved to trashbin) on the server.
- The CLI displayed a success message.

### Success Postconditions (Show)

- The CLI displayed every field of the bookmark in the requested format.
- No data was modified.

### Success Postconditions (Manage Structure)

- The collection, tag or folder exists, is renamed, is moved, or is gone, as requested.
- Fields the CLI does not expose are unchanged (BR-028); a renamed folder keeps its place in the hierarchy.
- After `collections default`, the server and — when the credentials in use are the stored ones — the local config agree on the default collection.
- The CLI displayed a success message naming what changed.

### Success Postconditions (Trashbin)

- A restored item, and anything soft-deleted alongside it, is live again.
- A purged or emptied item is gone permanently.
- The CLI displayed a success message.

### Postconditions (Declined Confirmation)

- No data was modified on the server.
- The CLI displayed `Error: Aborted.` and exited with code 1, or refused with code 2 when there was no TTY to ask.

### Failure Postconditions

- No data was modified on the server.
- The CLI displayed an error message.
- The CLI exited with a non-zero exit code.

## Business Rules

### BR-015: CLI Communicates Exclusively via HTTP API

The CLI never accesses the SQLite database directly. All data operations go through the existing REST API. This ensures that all business logic, authorization, audit trails, and entity listeners are applied consistently.

### BR-016: Non-Interactive by Default

Every CLI command must be drivable without any interactive prompts. All required parameters are provided via flags or positional arguments. This enables scripting and automation.

The one exception is the confirmation on an irreversible operation (BR-027), and it is still scriptable: `--yes` skips the prompt outright, and without a TTY the command refuses rather than blocking on a read that nobody will answer.

### BR-017: Exit Codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | General error (auth, network, validation) |
| 2 | Usage error (invalid flags, missing arguments) |

### BR-018: Output to stdout, Errors to stderr

Success messages and data output go to stdout. Error messages and warnings go to stderr. This allows piping: `linkweave bookmarks list --format=json | jq '.[0].url'`.

### BR-019: Tag Name Resolution

The `--tags` flag accepts comma-separated tag names (not IDs). The CLI resolves names to IDs before sending the API request. Unknown tags are auto-created. This matches the UX expectation from the browser UI, where users type tag names.

### BR-020: Folder Name Resolution

The `--folder` flag accepts a folder name (not an ID). The CLI resolves the name to an ID by calling `GET /api/folders?collectionId={id}`. If the folder is not found, the CLI creates it. For nested folders, the user can specify a path: `--folder="Dev/TypeScript/Articles"`.

The management commands take the same path syntax as a positional argument, and resolve it the same way — but never create: renaming or deleting a folder that does not exist is a mistake, not a request to make one. `folders create` is the exception in the other direction, creating missing parents like `mkdir -p` while refusing a path that already exists.

### BR-026: Completion Values Come From a Hidden Callback

The generated shell scripts fill in collection, tag and folder values by calling `linkweave __complete <source> [prefix]`, a hidden command that prints one candidate per line. It is total by design: not being logged in, an unreachable server, a revoked key or an unreadable config all print nothing and exit 0. An error surfaced here would be written into the command line the user is part-way through typing, which is worse than offering no suggestions. `LINKWEAVE_DEBUG=1` reports the cause on stderr for diagnosis.

Results are cached for 60 seconds in `$XDG_CACHE_HOME/linkweave/completion-cache.json` so a keypress never waits on the network twice. The cost is that a name created moments ago may be missing for up to a minute.

Positional arguments are completed from the same sources, chosen per argument slot: the value a command operates *on* is completed, while the `<new-name>` of a rename is not. Bookmark and trashbin IDs are excluded — a list of bare UUIDs is no use without a title beside it, which a one-value-per-line callback cannot carry.

### BR-027: Confirmation Is Gated on Recoverability, Not on Wording

A command prompts before acting if, and only if, the server cannot undo it:

| Command | Prompts | Why |
|---|---|---|
| `bookmarks rm` | no | soft delete; `trash restore` undoes it |
| `folders rm` | no | soft delete, cascading to sub-folders and the bookmarks inside; restoring the folder restores what went down with it |
| `tags rm` | yes | deletes outright and strips the tag from every bookmark that carried it |
| `collections rm` | yes | deletes every bookmark, folder, tag, auto-tag rule and saved search in it, none of which reaches the trashbin |
| `trash purge` / `trash empty` | yes | permanent by definition |

`--yes` (`-y`) skips the prompt. Without a TTY the command refuses with exit code 2 instead of assuming consent, because silently destroying data because stdin happened to be a pipe is the wrong default.

`trash empty` names the current item count as context but asks about "everything": the endpoint empties the trashbin unconditionally, so anything trashed while the prompt waits is destroyed too, and consent cannot be bound to a figure the command is unable to hold.

### BR-028: Updates Replace the Whole Record

The API's update endpoints take a complete entity, not a patch. Every CLI update is therefore fetch → merge → save, and any field the CLI does not expose has to be read back and resent or it is reset to a default:

- `bookmarks edit` carries over title, URL, description, folder and tags.
- `collections rename` carries over the screenshot toggle and the browser-fetch allowlist. It also has to *check* the result: the name is the one field of that payload the server may quietly decline to apply (see the scenario above).
- `tags rename` carries over the tag colour.
- `folders rename` carries over the colour **and the parent**: the endpoint reads an absent `parentId` as "move to the root", so omitting it silently re-homes the folder and its entire subtree to the top level.

The consequence is a read-modify-write race: a change made elsewhere between the fetch and the save is overwritten without warning. This is accepted rather than solved — the API offers no optimistic-locking token to bound it, and for a single-user CLI the window is small.

---

## CLI Command Reference

### Global Flags

| Flag | Short | Description | Default |
|---|---|---|---|
| `--server` | `-s` | LinkWeave API server URL | `https://linkweave.dev` |
| `--api-key` | `-k` | API key (overrides config file) | — |
| `--insecure` | | Disable TLS verification | `false` |
| `--help` | `-h` | Show help | — |
| `--version` | `-v` | Show version | — |

`--format`/`-f` (`table`, `json`, `ids`; default `table`) is scoped to the
commands that emit a record set — the `list` commands plus `bookmarks show` —
and passing it to any other command is a usage error (exit 2) rather than
being silently ignored.

`--collection` accepts an ID or a name (A8). Tags and folders are addressed the
same way: `<tag>` is a tag name or ID, `<path>` a folder path (`Dev/TypeScript`)
or ID. The `<new-name>` of a `rename` is a name the user is inventing, and is
never resolved against existing ones.

### Commands

```
linkweave login [--server <url>] [--api-key <key>] [--insecure]
linkweave logout

linkweave bookmarks add <url> [--title <t>] [--collection <c>] [--folder <f>] [--tags <t1,t2>] [--description <d>]
linkweave bookmarks list [--collection <c>] [--folder <f>] [--tag <t>] [--format <fmt>]
linkweave bookmarks show <id> [--format <fmt>]
linkweave bookmarks edit <id> [--title <t>] [--url <u>] [--description <d>] [--tags <t1,t2>]
linkweave bookmarks rm <id>
linkweave bookmarks export [--collection <c>] [--output <file>]
linkweave bookmarks import <file> [--collection <c>]

linkweave collections list [--format <fmt>]
linkweave collections create <name>
linkweave collections rename <collection> <new-name>
linkweave collections default <collection>
linkweave collections rm <collection> [--yes]

linkweave tags list [--collection <c>] [--format <fmt>]
linkweave tags rename <tag> <new-name> [--collection <c>]
linkweave tags rm <tag> [--collection <c>] [--yes]

linkweave folders list [--collection <c>] [--format <fmt>]
linkweave folders create <path> [--collection <c>]
linkweave folders rename <path> <new-name> [--collection <c>]
linkweave folders mv <path> <destination> [--collection <c>]
linkweave folders rm <path> [--collection <c>]

linkweave trash list [--format <fmt>]
linkweave trash restore <id>
linkweave trash purge <id> [--yes]
linkweave trash empty [--yes]

linkweave completion <bash|zsh|fish>
```

`linkweave __complete <source> [prefix]` also exists but is hidden: the
generated completion scripts call it to fill in collection, tag and folder
values, and it is not part of the user-facing surface (BR-026).

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-085 | Manage Bookmarks via CLI | This use case implements FR-085 |
| NFR-022 | CLI API Client Generation | TypeScript client from OpenAPI spec |
| NFR-023 | CLI Distribution | npm package |
| C-019 | CLI Lives in Monorepo | `cli/` directory |
| C-020 | CLI Language is TypeScript | TypeScript implementation |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-077 | Manage API Keys | Keys are created in the web UI |
| UC-078 | Authenticate via API Key | CLI uses API key auth |
| UC-080 | Configure CLI Login | CLI stores the API key |
| UC-005 | Create Bookmark | CLI calls the same API endpoint |
| UC-006 | View Bookmarks | CLI calls the same API endpoint |
| UC-007 | Edit Bookmark | CLI calls the same API endpoint |
| UC-008 | Delete Bookmark | CLI calls the same API endpoint |
| UC-003 | List Collections | `collections list` |
| UC-004 | Set Default Collection | `collections default` |
| UC-026 | Create Collection | `collections create` |
| UC-027 | Edit Collection | `collections rename` (name only) |
| UC-028 | Delete Collection | `collections rm` |
| UC-009 | Create Folder | `folders create`, and `add --folder` (BR-020) |
| UC-010 | View Folders | `folders list` |
| UC-011 | Rename Folder | `folders rename` |
| UC-012 | Nest Folders | `folders create` with a path, `folders mv` |
| UC-014 | Delete Folder | `folders rm` |
| UC-016 | View Tags | `tags list` |
| UC-017 | Edit Tag | `tags rename` (name only) |
| UC-018 | Delete Tag | `tags rm` |
| UC-040 | View Trashbin | `trash list` |
| UC-041 | Restore from Trashbin | `trash restore` |
| UC-042 | Permanently Delete from Trashbin | `trash purge` |
| UC-043 | Empty Trashbin | `trash empty` |
