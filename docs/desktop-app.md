# Desktop Application — Implementation Plan

Implementation plan for **UC-052: Run Chainlink as a Desktop Application**.

## Context

Chainlink today is a hosted web app: Vue 3 SPA + Quarkus 3.30 backend + SQLite, deployed at `dev-chainlink.markushofstetter.com`. This plan covers packaging the same codebase as a self-contained desktop application (macOS first, Windows/Linux as natural follow-ups). The goal is a learning exercise: understand what changes to the existing code, what gets added, and what blockers exist. It is not a production ship.

**Short answer: feasible.** The architecture is well-suited — SPA + embedded SQLite + self-contained backend. There are 3–4 real blockers, all solvable.

## Architecture

The desktop bundle ships two processes inside one installable application:

1. **Vue SPA** — already builds to `frontend/dist/` as static assets. Loaded by the desktop shell's webview.
2. **Quarkus backend** — runs as a sidecar child process spawned by the shell on launch, killed on quit. Bound to `127.0.0.1` on a random free port (BR-052-2).

```
┌─────────────────────────────────────┐
│         Tauri / Electron App        │
│                                     │
│  ┌──────────────┐   ┌────────────┐  │
│  │  Webview     │──▶│  Quarkus   │  │
│  │  (Vue SPA)   │   │  (sidecar) │  │
│  └──────────────┘   └─────┬──────┘  │
│                           │         │
│                     ┌─────▼──────┐  │
│                     │  SQLite    │  │
│                     │  (in user  │  │
│                     │   data dir)│  │
│                     └────────────┘  │
└─────────────────────────────────────┘
```

## Shell Choice: Tauri vs Electron

**Recommended: Tauri.**

| | Tauri | Electron |
|---|---|---|
| Bundle size (excluding JRE) | ~10 MB | ~150 MB |
| Memory | OS webview | Bundles Chromium |
| Sidecar support | First-class | Manual via `child_process` |
| Learning value | Higher (Rust + new ecosystem) | Lower (well-known) |

The JVM (~80 MB JRE) is the heaviest thing in the bundle either way, so Tauri's bundle-size win is partially eaten. A future `jlink`-trimmed runtime or a GraalVM native-image build would shrink it further, but native-image is out of scope for the prototype (slower steady-state runtime, and the build pipeline itself is non-trivial).

## Real Blockers (Ordered by Severity)

### 1. Hardcoded SQLite path (must fix)

`api/src/main/resources/application.properties:46`

```
quarkus.datasource.jdbc.url=jdbc:sqlite:../developer-local-settings/chainlink.db
```

Must become env-driven so the shell can point it at the OS-appropriate user data directory (e.g. `~/Library/Application Support/Chainlink/chainlink.db` on macOS — BR-052-1).

**Fix:** `${CHAINLINK_DB_PATH:../developer-local-settings/chainlink.db}` and have the shell export `CHAINLINK_DB_PATH` before spawning the JVM.

### 2. OIDC login is impossible in a desktop context (must work around)

Google OAuth requires browser redirect to a fixed callback URL. A desktop app on a random localhost port can't reliably handle this without extra infrastructure (loopback handler or device flow).

**Workaround:** Form-based auth already exists (`/api/j_security_check` + `/api/auth/register`) and is enabled. For the desktop build, hide the "Sign in with Google" button in `frontend/src/views/LoginView.vue` based on a build-time flag (e.g. `VITE_DESKTOP=true`). See BR-052-3 and A7 in UC-052.

### 3. Favicon cache directory is also relative (small fix)

`api/src/main/resources/application.properties` — `chainlink.favicon.cache-dir=developer-local-settings/favicon-cache`. Same env-var fix as #1: `CHAINLINK_FAVICON_CACHE_DIR`.

### 4. Frontend API base URL (small fix)

`frontend/src/api/client.ts` uses `basePath: ''` (relative). In dev, Vite proxies `/api` to the dev server. In a desktop bundle there is no proxy, and the backend port is determined at runtime.

**Fix:** Inject the backend port at app startup. Tauri exposes it via the `window.__TAURI__` invoke API; Electron via a `preload.js` script. Then `client.ts` reads it:

```ts
const port = (window as any).__CHAINLINK_DESKTOP__?.backendPort
const basePath = port ? `http://127.0.0.1:${port}` : ''
```

### 5. Favicon fetcher needs internet (acceptable)

Not a blocker. Desktop apps are allowed to need a network. Already degrades gracefully when offline (UC-050, A6 in UC-052).

## Critical Files to Modify

| File | Change |
|---|---|
| `api/src/main/resources/application.properties` | Make DB path + favicon dir env-driven |
| `frontend/src/api/client.ts` | Read backend port from injected global |
| `frontend/src/views/LoginView.vue` | Conditionally hide OIDC button when `import.meta.env.VITE_DESKTOP === 'true'` |
| `frontend/src/main.ts` | Read injected `window.__CHAINLINK_DESKTOP__` flag + port |
| **New** `desktop/` (repo root) | Tauri (or Electron) project containing the shell |
| **New** build script | (a) `npm run build` SPA, (b) copy `dist/` into shell, (c) `mvnw package` backend, (d) copy `target/quarkus-app/` into shell's resources, (e) `tauri build` |

## Implementation Order

1. **Make paths env-configurable** (#1, #3). Verify `CHAINLINK_DB_PATH=/tmp/foo.db java -jar quarkus-app/quarkus-run.jar` works.
2. **Make frontend API base URL injectable** (#4). Verify SPA still works in regular dev mode (port unset → falls back to relative).
3. **Scaffold a minimal Tauri project in `desktop/`**. Get it to spawn the JVM, poll `/q/health`, then load the SPA.
4. **Hide OIDC button** when `VITE_DESKTOP=true` (#2).
5. **Build a `.dmg`/`.app` for macOS**. Verify launch → register → add bookmark → quit → relaunch → bookmark persists.
6. **Single-instance lock** (BR-052-4) and **graceful shutdown** (BR-052-5).

That covers the learning goal. Code signing, notarization, auto-update, Windows/Linux installers, and JRE bundling (jlink) are natural follow-ups.

## Verification

After step 5:

- Launch the bundled `.app` from `/Applications`
- Confirm Quarkus starts (Console.app stdout, or shell-piped log file)
- Confirm SPA loads and `/q/health` returns 200 from inside webview devtools
- Register a new local user via form auth
- Add a bookmark, restart the app, verify it persists
- Confirm DB file is at `~/Library/Application Support/Chainlink/chainlink.db`, **not** in the app bundle (BR-052-1)
- Quit the app; confirm Quarkus actually exits (`pgrep -f quarkus-run` returns nothing)
- Launch a second instance; confirm the existing window comes forward instead of a duplicate launching (BR-052-4)
- Disconnect from the network; confirm bookmarks still browse/edit normally and only favicon fetches fail (A6)

## Out of Scope (Explicit)

- GraalVM native-image build — possible but slower steady-state and complex to maintain
- OIDC desktop flow (Device Flow / loopback handler)
- Auto-update infrastructure
- Code signing / notarization
- Windows / Linux installers
- Bundling a trimmed JRE via jlink (eventual answer for bundle size, not needed for prototype)
