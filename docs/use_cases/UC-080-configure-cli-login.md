# Use Case: Configure CLI Login

## Overview

**Use Case ID:** UC-080
**Use Case Name:** Configure CLI Login
**Primary Actor:** CLI User
**Goal:** Store a Chainlink API key and server URL locally so that subsequent CLI commands are automatically authenticated without re-entering credentials.
**Status:** Draft

## Traceability

**Maps to:** FR-086

---

## Preconditions

- The user has installed the Chainlink CLI.
- The user has created an API key via the web UI (UC-077).

## Main Success Scenario — Interactive Login

1. User runs `chainlink login`.
2. CLI prompts for the server URL: `Chainlink server URL [https://chainlink.markushofstetter.com]:`
3. User presses Enter to accept the default or types a different URL.
4. CLI prompts for the API key: `API key (created at {serverUrl}/settings/api-keys):`
5. User pastes the API key.
6. CLI validates the key format (must match `cl_` + 64 hex chars).
7. CLI validates the key against the server by sending `GET /api/auth/me` with the `X-API-Key` header.
8. Server returns the user's profile (email, name, default collection ID).
9. CLI stores the configuration in `~/.chainlink/config.json`:
   ```json
   {
     "server": "https://chainlink.markushofstetter.com",
     "apiKey": "cl_a1b2c3d4...",
     "userEmail": "user@example.com",
     "defaultCollectionId": "550e8400-..."
   }
   ```
10. CLI displays: `✓ Logged in as user@example.com. Configuration saved to ~/.chainlink/config.json`

## Main Success Scenario — Non-Interactive Login

1. User runs `chainlink login --api-key cl_a1b2c3d4... --server https://chainlink.markushofstetter.com`.
2. CLI validates the key format (step 6 above).
3. CLI validates the key against the server (step 7 above).
4. CLI stores the configuration (step 9 above).
5. CLI displays the success message.

## Main Success Scenario — Login via Environment Variable

1. User sets `CHAINLINK_API_KEY=cl_a1b2c3d4...` in their shell environment.
2. User optionally sets `CHAINLINK_SERVER=https://chainlink.markushofstetter.com`.
3. User runs any CLI command (e.g., `chainlink bookmarks list`).
4. CLI reads the API key from the environment variable (no `chainlink login` needed).
5. CLI proceeds with the command using the environment-provided credentials.

## Alternative Flows

### A1: Invalid Key Format

**Trigger:** The entered key does not match `cl_` + 64 hex chars (step 6).
**Flow:**

1. CLI displays: `Error: Invalid API key format. Expected: cl_ followed by 64 hex characters.`
2. CLI prompts again (interactive mode) or exits with code 2 (non-interactive mode).

### A2: Key Rejected by Server

**Trigger:** `GET /api/auth/me` returns HTTP 401 (step 8).
**Flow:**

1. CLI displays: `Error: API key rejected by server. The key may be invalid or revoked. Create a new key at {serverUrl}/settings/api-keys`
2. CLI does not save the configuration.
3. CLI exits with code 1.

### A3: Server Unreachable

**Trigger:** The HTTP request to verify the key fails due to network error (step 7).
**Flow:**

1. CLI displays: `Error: Cannot reach server at {url}. Check the URL and your network connection.`
2. CLI prompts to retry or abort (interactive mode) or exits with code 1 (non-interactive mode).

### A4: Config File Already Exists

**Trigger:** `~/.chainlink/config.json` already contains credentials (step 9).
**Flow:**

1. CLI overwrites the existing configuration without prompting (the user explicitly ran `login`).
2. CLI displays: `⚠ Overwriting existing configuration for {oldEmail}.`

### A5: Config File Permissions

**Trigger:** `~/.chainlink/config.json` cannot be written (step 9).
**Flow:**

1. CLI displays: `Error: Cannot write to ~/.chainlink/config.json. Check directory permissions.`
2. CLI exits with code 1.

### A6: Self-Signed Certificate

**Trigger:** TLS verification fails during key validation (step 7).
**Flow:**

1. CLI displays: `Error: TLS certificate verification failed. Add --insecure to disable TLS verification for this login.`
2. User re-runs with `--insecure`.

## Postconditions

### Success Postconditions

- `~/.chainlink/config.json` exists and contains a valid server URL, API key, and user email.
- The API key was validated against the server (not just stored blindly).
- Subsequent CLI commands will use the stored credentials.

### Failure Postconditions

- No configuration file is created or the existing one is unchanged.
- The CLI displayed an error message.
- The CLI exited with a non-zero exit code.

## Business Rules

### BR-021: Config File Location

The configuration file is stored at `~/.chainlink/config.json`. On Windows, `~` resolves to `%USERPROFILE%`. The `~/.chainlink/` directory is created if it does not exist.

### BR-022: Config File Permissions

The CLI must set the config file permissions to `0600` (owner read/write only) on Unix-like systems to prevent other users from reading the API key.

### BR-023: Environment Variables Override Config

If `CHAINLINK_API_KEY` is set in the environment, it takes precedence over the value in `~/.chainlink/config.json`. Similarly, `CHAINLINK_SERVER` overrides the stored server URL. This allows CI/CD pipelines and scripts to inject credentials without modifying the config file.

### BR-024: Key Validation Before Storage

The CLI must always validate the API key against the server (via `GET /api/auth/me`) before storing it. This prevents storing invalid keys that would cause confusing errors on subsequent commands.

### BR-025: Logout

Running `chainlink logout` deletes `~/.chainlink/config.json` and displays: `✓ Configuration removed. Run 'chainlink login' to authenticate again.`

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-086 | CLI Login Flow | This use case implements FR-086 |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-077 | Manage API Keys | Keys are created in the web UI first |
| UC-078 | Authenticate via API Key | Stored key is used for authentication |
| UC-079 | Manage Bookmarks via CLI | Commands use the stored credentials |
