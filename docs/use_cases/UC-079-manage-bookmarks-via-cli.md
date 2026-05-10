# Use Case: Manage Bookmarks via CLI

## Overview

**Use Case ID:** UC-079
**Use Case Name:** Manage Bookmarks via CLI
**Primary Actor:** CLI User
**Goal:** Create, list, edit, and delete bookmarks from the command line so that the user can manage bookmarks from a terminal or shell script without opening a browser.
**Status:** Draft

## Traceability

**Maps to:** FR-085, NFR-022, C-019, C-020

---

## Preconditions

- The user has installed the Chainlink CLI (`npm install -g @chainlink/cli`).
- The user has configured their CLI credentials via UC-080 (`chainlink login`).
- The Chainlink API server is reachable at the configured URL.
- The user has at least one collection (auto-provisioned on first web login).

## Main Success Scenario — Add Bookmark

1. User runs `chainlink bookmarks add <url>` with optional flags `--title`, `--collection`, `--folder`, `--tags`, `--description`.
2. CLI reads the API key from `~/.chainlink/config.json` (or `CHAINLINK_API_KEY` env var).
3. CLI reads the server URL from config (default: `https://chainlink.markushofstetter.com`).
4. If `--collection` is not specified, CLI calls `GET /api/auth/me` to retrieve the user's default collection ID.
5. If `--title` is not specified, CLI uses the URL as a fallback title (the server does not auto-fetch titles via the API).
6. CLI sends `POST /api/bookmarks` with the `X-API-Key` header and a `BookmarkSaveJson` body.
7. Server authenticates via UC-078, authorizes via `AuthorizationService.requireCollectionAccess()`, and creates the bookmark.
8. CLI receives the created bookmark and displays a success message: `✓ Bookmark created: {title} ({url})`.

## Main Success Scenario — List Bookmarks

1. User runs `chainlink bookmarks list` with optional flags `--collection`, `--folder`, `--tag`, `--format`.
2. CLI resolves the collection ID (default collection if not specified).
3. CLI sends `GET /api/bookmarks?collectionId={id}` with the `X-API-Key` header.
4. Server returns all bookmarks in the collection.
5. CLI displays the bookmarks in the requested format:
   - `--format=table` (default): renders a table with columns ID, Title, URL, Tags.
   - `--format=json`: outputs raw JSON to stdout (for piping to `jq` or other tools).
   - `--format=ids`: outputs one bookmark ID per line (for use in shell loops).

## Main Success Scenario — Edit Bookmark

1. User runs `chainlink bookmarks edit <bookmarkId>` with optional flags `--title`, `--url`, `--description`, `--tags`.
2. CLI sends `PUT /api/bookmarks/{bookmarkId}` with the `X-API-Key` header and the updated `BookmarkSaveJson` body.
3. Server authenticates, authorizes, and updates the bookmark.
4. CLI displays a success message: `✓ Bookmark updated: {title}`.

## Main Success Scenario — Remove Bookmark

1. User runs `chainlink bookmarks rm <bookmarkId>`.
2. CLI sends `DELETE /api/bookmarks/{bookmarkId}` with the `X-API-Key` header.
3. Server authenticates, authorizes, and soft-deletes the bookmark (moves to trashbin).
4. CLI displays a success message: `✓ Bookmark removed: {bookmarkId}`.

## Alternative Flows

### A1: Not Authenticated

**Trigger:** CLI cannot find an API key in config or env var (step 2).
**Flow:**

1. CLI displays an error: `Error: Not authenticated. Run 'chainlink login' to configure your API key.`
2. CLI exits with code 1.

### A2: API Key Revoked or Invalid

**Trigger:** Server rejects the request with HTTP 401 (step 7 of any scenario).
**Flow:**

1. CLI displays: `Error: Authentication failed. Your API key may have been revoked. Run 'chainlink login' to reconfigure.`
2. CLI exits with code 1.

### A3: Server Unreachable

**Trigger:** The HTTP request fails due to network error (any step).
**Flow:**

1. CLI displays: `Error: Cannot reach Chainlink server at {url}. Check your network connection and server URL.`
2. CLI exits with code 1.

### A4: Collection Not Found or No Access

**Trigger:** Server returns HTTP 403 for the collection ID (step 7).
**Flow:**

1. CLI displays: `Error: Collection not found or access denied. Use 'chainlink collections list' to see your collections.`
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
5. If no match is found, CLI displays: `Error: No collection found with name '{name}'. Use 'chainlink collections list' to see your collections.`

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

### Failure Postconditions

- No data was modified on the server.
- The CLI displayed an error message.
- The CLI exited with a non-zero exit code.

## Business Rules

### BR-015: CLI Communicates Exclusively via HTTP API

The CLI never accesses the SQLite database directly. All data operations go through the existing REST API. This ensures that all business logic, authorization, audit trails, and entity listeners are applied consistently.

### BR-016: Non-Interactive by Default

Every CLI command must work without any interactive prompts. All required parameters are provided via flags or positional arguments. This enables scripting and automation.

### BR-017: Exit Codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | General error (auth, network, validation) |
| 2 | Usage error (invalid flags, missing arguments) |

### BR-018: Output to stdout, Errors to stderr

Success messages and data output go to stdout. Error messages and warnings go to stderr. This allows piping: `chainlink bookmarks list --format=json | jq '.[0].url'`.

### BR-019: Tag Name Resolution

The `--tags` flag accepts comma-separated tag names (not IDs). The CLI resolves names to IDs before sending the API request. Unknown tags are auto-created. This matches the UX expectation from the browser UI, where users type tag names.

### BR-020: Folder Name Resolution

The `--folder` flag accepts a folder name (not an ID). The CLI resolves the name to an ID by calling `GET /api/folders?collectionId={id}`. If the folder is not found, the CLI creates it. For nested folders, the user can specify a path: `--folder="Dev/TypeScript/Articles"`.

---

## CLI Command Reference

### Global Flags

| Flag | Short | Description | Default |
|---|---|---|---|
| `--server` | `-s` | Chainlink API server URL | `https://chainlink.markushofstetter.com` |
| `--api-key` | `-k` | API key (overrides config file) | — |
| `--insecure` | | Disable TLS verification | `false` |
| `--format` | `-f` | Output format (`table`, `json`, `ids`) | `table` |
| `--help` | `-h` | Show help | — |
| `--version` | `-v` | Show version | — |

### Commands

```
chainlink login [--server <url>] [--api-key <key>]
chainlink bookmarks add <url> [--title <t>] [--collection <c>] [--folder <f>] [--tags <t1,t2>] [--description <d>]
chainlink bookmarks list [--collection <c>] [--folder <f>] [--tag <t>] [--format <fmt>]
chainlink bookmarks edit <id> [--title <t>] [--url <u>] [--description <d>] [--tags <t1,t2>]
chainlink bookmarks rm <id>
chainlink collections list [--format <fmt>]
```

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
