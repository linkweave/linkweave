# Use Case: Run Chainlink as a Desktop Application

## Overview

**Use Case ID:** UC-052
**Use Case Name:** Run Chainlink as a Desktop Application
**Primary Actor:** User (running Chainlink locally on their own machine)
**Goal:** Use Chainlink as a self-contained desktop application that does not require a separately hosted backend or a browser
**Status:** Done

**Notes:**
- The desktop bundle ships the Vue SPA, the Quarkus backend, and a per-user SQLite database inside a single installable application (Tauri or Electron shell).
- The backend is started as a sidecar process on app launch, bound to `127.0.0.1` on a random free port, and terminated on app quit.
- The desktop build deliberately uses local form-based authentication only — Google OIDC is hidden because the OAuth redirect flow is incompatible with a localhost desktop app without additional infrastructure (loopback handler / device flow).

**Traces to:** (no FR exists yet — would need a new requirement, e.g. FR-070 "Distribute Chainlink as a desktop application").

**Implementation Plan:** See [`../desktop-app.md`](../desktop-app.md) for the architectural approach, blockers, file-by-file changes, and verification steps.

## Preconditions

- The user has installed the Chainlink desktop application on a supported operating system (macOS for the prototype; Windows/Linux later).
- The user's machine has sufficient resources to run a JVM (~300–400 MB RSS).
- The user has network access if they intend to use features that require it (favicon fetching, bookmark imports from URLs).

## Main Success Scenario

1. User launches the Chainlink desktop application from their OS application launcher.
2. System (desktop shell) determines the OS-appropriate user data directory (e.g. `~/Library/Application Support/Chainlink/` on macOS) and ensures it exists.
3. System selects a random free TCP port on `127.0.0.1` for the backend.
4. System spawns the bundled Quarkus backend as a child process, passing `CHAINLINK_DB_PATH`, `CHAINLINK_FAVICON_CACHE_DIR`, and the chosen port via environment variables.
5. System polls the backend's health endpoint until it responds successfully or a timeout elapses.
6. System opens the application window and loads the bundled Vue SPA into the embedded webview, injecting the backend port into a global object accessible to the SPA.
7. SPA reads the injected port and configures its API client to call `http://127.0.0.1:<port>`.
8. SPA presents the login screen with form-based authentication only (Google sign-in button is hidden in desktop mode).
9. User logs in (or registers a new local account on first launch — see UC-034).
10. User uses the application normally — collections, bookmarks, folders, tags, search — all backed by the local SQLite database in the user data directory.
11. User quits the application via the OS quit action.
12. System sends a graceful shutdown signal to the backend child process and waits for it to exit before the shell process terminates.

## Alternative Flows

### A1: First Launch — No Existing Database

**Trigger:** No SQLite file exists at the expected user data path (step 4)
**Flow:**

1. Quarkus starts and Flyway runs all migrations against an empty database, producing a fresh schema.
2. SPA presents the login screen with no pre-existing accounts.
3. User registers a new local account (UC-034).
4. Use case continues at step 10.

### A2: Backend Fails to Start

**Trigger:** The Quarkus child process exits with a non-zero status, or the health endpoint does not respond before the timeout (step 5)
**Flow:**

1. System captures the backend's stdout/stderr to a log file in the user data directory.
2. System displays a native error dialog: "Chainlink could not start. See the log file at `<path>` for details."
3. User dismisses the dialog; system shuts down cleanly.

### A3: Port Selection Race

**Trigger:** The chosen port is taken by another process between selection and Quarkus binding (step 4)
**Flow:**

1. Quarkus fails to bind and exits.
2. System retries port selection up to 3 times.
3. If all retries fail, flow continues with A2.

### A4: Database File Locked or Corrupt

**Trigger:** SQLite reports the database is locked or corrupted on Quarkus startup (step 4)
**Flow:**

1. Quarkus startup fails; flow continues with A2, but the error dialog additionally suggests: "Another instance of Chainlink may already be running, or the database file may be corrupt."

### A5: Second Instance Launched

**Trigger:** User launches the application while another instance is already running
**Flow:**

1. System detects the existing instance via a single-instance lock (Tauri/Electron primitive) or via the database lock.
2. System brings the existing window to the foreground and exits the new instance silently.

### A6: Offline Operation

**Trigger:** User is not connected to the internet
**Flow:**

1. The backend starts normally; SQLite is local.
2. The user can browse, edit, and organize all existing bookmarks normally.
3. Features requiring the network (fetching favicons for new bookmarks, URL imports) fail gracefully and log warnings — see UC-050.
4. Use case continues at step 10.

### A7: User Attempts Google Sign-In

**Trigger:** User somehow reaches an OIDC code path (should not be visible in desktop mode)
**Flow:**

1. System displays a message: "Google sign-in is not available in the desktop version. Please use a local account."
2. User returns to the form login.

### A8: Forced Quit

**Trigger:** User force-quits the application or the OS terminates it
**Flow:**

1. The shell process and the backend child process are killed without graceful shutdown.
2. SQLite's WAL recovery handles any incomplete transactions on the next launch (A4 may be triggered if recovery fails).

## Postconditions

### Success Postconditions

- The user's bookmarks, folders, tags, and collections are persisted in the SQLite database inside the OS user data directory.
- No backend or database process remains running after the application quits.
- The user data directory contains the database, favicon cache, and (optionally) a log file. The application bundle itself is unchanged.

### Failure Postconditions

- If the backend never started, no data is written and the user is informed via a native error dialog.
- If the application crashed mid-session, the SQLite WAL ensures the database is consistent on the next launch; uncommitted transactions are lost.

## Business Rules

### BR-052-1: User Data Lives Outside the App Bundle

The SQLite database, favicon cache, and logs MUST be stored in the OS-standard per-user application data directory, NEVER inside the application bundle. This ensures that updating or reinstalling the application does not destroy user data.

### BR-052-2: Backend Is Bound to Loopback Only

The bundled backend MUST listen only on `127.0.0.1`, never on `0.0.0.0` or a public interface. Other devices on the same network must not be able to reach the backend.

### BR-052-3: Local Authentication Only

The desktop build MUST hide the Google OIDC sign-in option and present only form-based authentication. This is a deliberate restriction to avoid the complexity of implementing the OAuth desktop flow (loopback handler or device flow) for the prototype.

### BR-052-4: Single Instance per User

Only one instance of the desktop application may run at a time per user, enforced by a shell-level single-instance lock. Multiple instances would race on the SQLite database file.

### BR-052-5: Graceful Shutdown of the Backend

When the user quits the application, the shell MUST send a graceful shutdown signal (SIGTERM or platform equivalent) to the backend process and wait for it to exit before terminating itself, to allow Quarkus to flush pending writes and close the database cleanly.

### BR-052-6: Configurable Storage Paths

The backend MUST accept the database path and favicon cache directory via environment variables (`CHAINLINK_DB_PATH`, `CHAINLINK_FAVICON_CACHE_DIR`). Hardcoded relative paths (currently `../developer-local-settings/chainlink.db`) must be replaced with environment-variable defaults.

### BR-052-7: No External Network Dependency for Core Features

The core bookmark management features (create, edit, delete, organize, search) MUST function without internet access. Only enrichment features (favicon fetching, URL metadata) may require the network.
