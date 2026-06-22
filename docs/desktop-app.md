# Desktop Application — Implementation Plan

Implementation plan for **UC-052: Run LinkWeave as a Desktop Application**.

## Context

LinkWeave today is a hosted web app: Vue 3 SPA + Quarkus 3.30 backend + SQLite, deployed at `dev-chainlink.markushofstetter.com`. This plan covers packaging the same codebase as a self-contained desktop application (macOS first, Windows/Linux as natural follow-ups). The goal is a learning exercise: understand what changes to the existing code, what gets added, and what blockers exist. It is not a production ship.

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

## Repository Layout

The repo is not a unified monorepo (no root `pnpm-workspace.yaml`, `package.json`, or `pom.xml`). Each component is a self-contained sibling directory at the root that owns its own toolchain — `api/` (Quarkus/Maven), `frontend/` (Vue/pnpm), `screenshot-service/` (Node sidecar with its own `package.json` + `Dockerfile`).

The desktop shell follows the same pattern as `screenshot-service/`: a standalone runtime that orchestrates the others, living as a root sibling.

```
chainlink/
├── api/                 # Quarkus backend (Maven)
├── frontend/            # Vue SPA (pnpm) — builds to frontend/dist/
├── screenshot-service/  # Node sidecar (precedent for a sibling service dir)
├── scripts/             # cross-component scripts (certs/, + new build-desktop.sh)
└── desktop/   ← NEW Tauri shell
    ├── package.json          # @tauri-apps/cli + JS deps for the shell
    ├── src-tauri/
    │   ├── Cargo.toml
    │   ├── tauri.conf.json   # bundle config, sidecar binaries, window settings
    │   ├── src/main.rs       # spawn JVM, poll /api/ping, load 127.0.0.1:${port}
    │   └── bin/              # bundled sidecar payload (see build pipeline)
    └── (no frontend sources — Quarkus serves the SPA under Option B)
```

Under **Option B** (Quarkus serves the SPA), `desktop/` does not host or build the frontend. It ships the packaged `quarkus-app/` (with `dist/` already baked into its resources) as a Tauri sidecar resource, spawns the JVM, and points the webview at `http://127.0.0.1:${port}`. That keeps `desktop/` thin — an orchestrator, not a build aggregator.

The cross-component build that wires all three toolchains together lives in **`scripts/build-desktop.sh`** (with a `.ps1` counterpart later for Windows, matching the existing `scripts/certs/generate-keypair.{sh,ps1}` convention), not inside `desktop/`.

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
quarkus.datasource.jdbc.url=jdbc:sqlite:../developer-local-settings/chainlink.db?foreign_keys=on&busy_timeout=10000
```

Must become env-driven so the shell can point it at the OS-appropriate user data directory (e.g. `~/Library/Application Support/LinkWeave/chainlink.db` on macOS — BR-052-1).

**Fix:** make only the path segment env-driven and keep the query params (they enforce foreign keys and set the busy timeout — don't lose them):

```
quarkus.datasource.jdbc.url=jdbc:sqlite:${CHAINLINK_DB_PATH:../developer-local-settings/chainlink.db}?foreign_keys=on&busy_timeout=10000
```

Have the shell export `CHAINLINK_DB_PATH` before spawning the JVM.

### 2. OIDC login is impossible in a desktop context (must work around)

Google OAuth requires browser redirect to a fixed callback URL. A desktop app on a random localhost port can't reliably handle this without extra infrastructure (loopback handler or device flow).

**Workaround:** Form-based auth already exists (`/api/j_security_check` + `/api/auth/register`) and is enabled. For the desktop build, hide the "Sign in with Google" button in `frontend/src/views/LoginView.vue` based on a build-time flag (e.g. `VITE_DESKTOP=true`). See BR-052-3 and A7 in UC-052.

### 3. Favicon cache directory is also relative (no code change needed)

This is **not** in `application.properties`. It's a `@ConfigProperty(name = "chainlink.favicon.cache-dir", defaultValue = "developer-local-settings/favicon-cache")` in `api/src/main/java/org/chainlink/api/shared/config/ConfigService.java:108`.

Because it's already a Quarkus config property, no code change is required — Quarkus auto-maps the env var `CHAINLINK_FAVICON_CACHE_DIR` onto `chainlink.favicon.cache-dir`. The shell just exports it. (The same env-var mapping is why the DB path in #1 is the only config that needs a property-file edit — `quarkus.datasource.jdbc.url` is a single string we have to splice the path into.)

### 4. Frontend ↔ backend transport: keep it same-origin (key design decision)

`frontend/src/api/client.ts` uses `basePath: ''` (relative). In dev, Vite proxies `/api` to the dev server. In a desktop bundle there is no proxy.

There are two ways to wire the SPA to the sidecar, and the choice matters because **auth is session-cookie based** (`credentials: 'include'`):

**Option A — inject the backend port, cross-origin (what the original plan implied).** Shell injects the port, `client.ts` builds `http://127.0.0.1:${port}`. Problem: the SPA itself loads from a *different* origin (`tauri://localhost`), so every API call becomes a cross-origin credentialed request. That drags in CORS config with a specific allowed origin **and** SameSite cookie handling for the session cookie. It also doesn't fully work, because two auth calls bypass the generated client and use hardcoded relative URLs (see #4b), so injecting `basePath` alone misses them.

**Option B — serve the SPA from Quarkus, same-origin (chosen).** Quarkus serves the SPA so the webview can point at `http://127.0.0.1:${port}` directly. Then:
- `basePath: ''` keeps working unchanged — no port injection into the SPA at all.
- The raw `fetch('/api/...')` calls in #4b resolve correctly with no change.
- No CORS, no cross-origin cookie problems.
- The only thing the shell injects is the URL it points the webview at.

**This plan uses Option B.** It collapses #4, #4b, and the CORS/cookie problem into a single decision.

**Crucially, Quarkus must serve the SPA only in the desktop deployment, never in the normal Docker image** — in production Caddy already serves the frontend and proxies `/api`. See #4c.

### 4c. SPA served at `/` via a desktop build profile + runtime web-root (implemented)

**The blocker discovered during implementation:** the production config sets `quarkus.http.root-path=/api`, which mounts the *application* router under `/api`. App-level code can only reach that router — both `@Observes Router` and `Filters` operate under `/api` (verified empirically). Quarkus' true main router (which serves `/`) exists only as a build-time `VertxWebRouterBuildItem`, not a runtime CDI bean. So a plain runtime route **cannot** serve the SPA at `/`, and serving it under `/api` collides with the REST API on deep-link reloads (e.g. `/api/collections/123`).

**The resolution:** the desktop backend is a **separate build** anyway — `quarkus.oidc.enabled` is build-time, and OIDC must be compiled out so the desktop backend never reaches Google at startup (the desktop UI uses form auth only, #2). Since we are already producing a distinct artifact, a `%desktop` config profile flips the routing so `/` is free for the SPA, **without touching the production config**:

```properties
# application.properties — inert unless built/run with quarkus.profile=desktop
%desktop.quarkus.http.root-path=/                                  # build-time: free up /
%desktop.quarkus.rest.path=/api                                    # build-time: REST stays /api
%desktop.quarkus.oidc.enabled=false                               # build-time: compile OIDC out
%desktop.quarkus.http.auth.form.post-location=/api/j_security_check  # runtime: keep login at /api
# Self-contained / offline defaults for config that is env-var-required only in prod, otherwise
# the bundled backend won't boot (it has no Sentry DSN / deployment env / cookie secret to read):
%desktop.quarkus.log.sentry.enabled=false
%desktop.app.deployment.environment=LOCAL
%desktop.app.deployment.public-url=http://127.0.0.1/
%desktop.app.deployment.instance=desktop
%desktop.quarkus.http.auth.session.encryption-key=chainlink-desktop-local-session-key  # local-only
```

> Lesson learned: a `@QuarkusTest` runs under the `test` profile, which already neutralizes Sentry/deployment/secret — so it does **not** prove the `desktop` profile boots. The build pipeline therefore boots the packaged jar under `QUARKUS_PROFILE=desktop` and probes `/api/ping` before bundling (see Build Pipeline).

- **Docker build** (`mvnw package`, no profile): completely unchanged — `root-path=/api`, OIDC on, Google callback at `/api/q/authorized` intact. The `%desktop` lines do not apply.
- **Desktop build** (`mvnw package -Dquarkus.profile=desktop`, run with `QUARKUS_PROFILE=desktop`): SPA served at `/`, REST under `/api`, OIDC absent, form login at `/api/j_security_check`.

The SPA is still **never baked into the jar**: it ships as a Tauri resource folder and is served at runtime from the directory in `CHAINLINK_DESKTOP_WEB_ROOT` (install-specific absolute path → must be runtime, mirroring `CHAINLINK_DB_PATH` / `CHAINLINK_FAVICON_CACHE_DIR`).

**Clean separation:**
- *Build-time* (`%desktop` profile): `root-path=/`, `rest.path=/api`, `oidc.enabled=false`.
- *Runtime* (env vars, install-specific): `CHAINLINK_DB_PATH`, `CHAINLINK_FAVICON_CACHE_DIR`, `CHAINLINK_DESKTOP_WEB_ROOT`, `QUARKUS_PROFILE=desktop`.

**Implementation:** `org.chainlink.infrastructure.web.DesktopWebRootRoute` registers a `Filters` handler (gated on `chainlink.desktop.web-root` being set) that serves real files from the web-root via Vert.x `StaticHandler`, and falls back to `index.html` for SPA deep-links while passing `/api` and `/q` through to JAX-RS. With `root-path=/` the handler runs at the root, so the SPA lands at `/`. Covered by `DesktopWebRootRouteITest` (asserts `/`, `/asset`, deep-link fallback, and that `/api/ping` is not shadowed).

**Why not quinoa / `META-INF/resources` / `quarkus.http.static-dir`?** All of those bake the SPA into the jar and/or add a build dependency present in the Docker image. The runtime web-root keeps the SPA out of the jar entirely and adds zero production dependencies, so it wins for a sidecar whose webview loads `/` once.

(`chainlink.desktop.web-root` ← `CHAINLINK_DESKTOP_WEB_ROOT` via Quarkus env mapping. The handler reads the backend prefixes to skip from `quarkus.rest.path` and `quarkus.http.non-application-root-path` rather than hardcoding `/api` / `/q`, so it stays correct if those move.)

### 4b. Login calls bypass the generated client (must fix regardless)

Two auth calls use hardcoded relative URLs and do **not** go through `client.ts`'s `basePath`:

- `frontend/src/views/LoginView.vue:37` — `fetch('/api/j_security_check', ...)` (form login, *the* desktop auth path once OIDC is hidden)
- `frontend/src/views/LoginView.vue:28` — `window.location.href = '/api/auth/oidc-login'`

Under Option A these would break (they'd hit the webview origin, not the backend). Under Option B they Just Work because everything is same-origin. This is the main reason Option B is chosen — otherwise form login, the only desktop auth path, silently fails.

### 5. Favicon fetcher needs internet (acceptable)

Not a blocker. Desktop apps are allowed to need a network. Already degrades gracefully when offline (UC-050, A6 in UC-052).

## Critical Files to Modify

Assuming Option B (same-origin, SPA served by Quarkus):

| File | Change |
|---|---|
| `api/src/main/resources/application.properties` | Splice `${CHAINLINK_DB_PATH:...}` into the JDBC URL (keep `?foreign_keys=on&busy_timeout=10000`) |
| `ConfigService.java` (favicon dir) | **No change** — set env var `CHAINLINK_FAVICON_CACHE_DIR` (Quarkus auto-maps it) |
| `frontend/src/api/client.ts` | **No change** under Option B — `basePath: ''` stays correct |
| `frontend/src/views/LoginView.vue` | Hide OIDC button when `import.meta.env.VITE_DESKTOP === 'true'`. Raw `/api/...` fetches need no change under Option B |
| **New** `DesktopWebRootRoute.java` (api) | Serve the SPA from a filesystem dir **only when** `CHAINLINK_DESKTOP_WEB_ROOT` is set (#4c). Keeps the API jar identical for Docker (Caddy) and desktop |
| **New** `desktop/` (repo root) | Tauri (or Electron) project containing the shell |
| **New** build script | (a) `pnpm run build` SPA, (b) `mvnw package` backend (no SPA baked in), (c) stage `dist/` + `quarkus-app/` as Tauri resources, (d) `tauri build` |

> If you instead go with Option A (cross-origin), add: port injection in `frontend/src/main.ts` (`window.__CHAINLINK_DESKTOP__`), `client.ts` reading it, rewriting the two raw fetches in `LoginView.vue`, and CORS + SameSite cookie config on the backend.

## Build Pipeline (`scripts/build-desktop.sh`)

One script chains the three toolchains in dependency order. It is the single entry point for producing a desktop bundle and must be runnable both locally and from CI (the existing screenshot-sidecar CI job is the precedent for adding a desktop job later).

### What it produces

A `.app`/`.dmg` (macOS first) whose Tauri resources contain two self-contained payloads: the packaged `quarkus-app/` (no SPA baked in) and the built `dist/` SPA folder. At launch the shell sets `CHAINLINK_DESKTOP_WEB_ROOT` to the bundled `dist/` so Quarkus serves it same-origin (#4c). Nothing is fetched or built at app launch.

### Stages (fail-fast; `set -euo pipefail`)

| # | Stage | Command (cwd) | Output |
|---|---|---|---|
| 0 | Preflight | check `node`, `pnpm`, `java`, `cargo`, `tauri` on PATH; print versions | early, clear failure if a toolchain is missing |
| 1 | Build SPA | `pnpm install --frozen-lockfile && pnpm run build` (`frontend/`) | `frontend/dist/` (Vite default; build with `VITE_DESKTOP=true`) |
| 2 | Package backend | `./mvnw -DskipTests package` (`api/`) | `api/target/quarkus-app/` (fast-jar, **no SPA inside**) |
| 3 | Stage payloads into shell | copy `frontend/dist/` → `desktop/src-tauri/bin/web/` **and** `api/target/quarkus-app/` → `desktop/src-tauri/bin/quarkus-app/` | two Tauri resources |
| 4 | Bundle | `pnpm install && pnpm tauri build` (`desktop/`) | `desktop/src-tauri/target/release/bundle/**` |
| 5 | Collect | copy the `.dmg`/`.app` to `desktop/dist/` (git-ignored) | one predictable artifact location |

The SPA is **never** copied into `api/src/main/resources/` — that's the whole point of #4c. The backend build in stage 2 is the same `mvnw package` the Docker image uses, so the jar is identical across deployments.

### Design decisions to pin down

- **`VITE_DESKTOP=true` is set only in stage 1**, so the desktop SPA build hides the OIDC button (#2) while the normal web build is unaffected. The flag belongs to the build script, not to `frontend/`'s default scripts.
- **No tracked source tree is written.** Both payloads are staged into `desktop/src-tauri/bin/` (a gitignored build location). Nothing under `api/src/` is touched, so there's no risk of the SPA leaking into the Docker image.
- **The shell exports `CHAINLINK_DESKTOP_WEB_ROOT`** (pointing at the bundled `bin/web/`) alongside `CHAINLINK_DB_PATH` and `CHAINLINK_FAVICON_CACHE_DIR` when it spawns the JVM. These three env vars are the entire desktop-specific runtime contract.
- **JRE is bundled by default (`--no-jre` to skip).** This turned out to be *required*, not optional: a dependency (`dvbstarter`) is compiled for Java 25, and a Finder/launchd launch resolves whatever (often older) `java` the OS has — on the dev machine that was a system Java 21, which crashed the backend with `UnsupportedClassVersionError` (the app just spun on the splash). Stage 4 `jlink`s a trimmed Java 25 runtime (~94 MB, `--add-modules ALL-MODULE-PATH` for correctness over size) into `bin/runtime`, ships it as a Tauri resource, and the shell prefers `runtime/bin/java` over PATH. `--no-jre` skips it for environments with a guaranteed system Java 25+.
- **`mvnw -DskipTests`** in the build script — tests run in their own CI stage / `mvnw verify`, not in the packaging path. Keep packaging fast and deterministic.
- **Idempotent staging.** Stage 3 `rm -rf`s each destination before copying so stale assets from a previous build never leak into the bundle.
- **Platform-parameterized, macOS-first.** Keep stage commands platform-neutral where possible so a later `build-desktop.ps1` (Windows) and Linux target are additive, mirroring `scripts/certs/generate-keypair.{sh,ps1}`.

### Verification of the script itself (before trusting the bundle)

After stage 2, run the packaged jar standalone with the env vars set to confirm Option B end-to-end *outside* Tauri:

```bash
CHAINLINK_DB_PATH=/tmp/desktop-smoke.db \
CHAINLINK_FAVICON_CACHE_DIR=/tmp/desktop-fav \
CHAINLINK_DESKTOP_WEB_ROOT=frontend/dist \
java -jar api/target/quarkus-app/quarkus-run.jar
# then in a browser: SPA loads at http://127.0.0.1:<port>, /api/* works, form login works
```

Run the same jar **without** `CHAINLINK_DESKTOP_WEB_ROOT` and confirm Quarkus serves no SPA (proves the Docker/Caddy path is unaffected).

This isolates "did the build wire the SPA + paths correctly" from "does Tauri spawn/teardown correctly" — the two failure domains the rest of the plan tests separately.

## Implementation Order

1. **Make paths env-configurable** (#1, #3). Edit only the JDBC URL; favicon dir needs no code change. Verify `CHAINLINK_DB_PATH=/tmp/foo.db CHAINLINK_FAVICON_CACHE_DIR=/tmp/fav java -jar quarkus-app/quarkus-run.jar` writes to those locations.
2. **Add `DesktopWebRootRoute`** (#4c) gated on `CHAINLINK_DESKTOP_WEB_ROOT`. With it set to `frontend/dist`, confirm the SPA + `/api/*` + form login all work from a single `http://127.0.0.1:port` origin in a plain browser; with it unset, confirm Quarkus serves no SPA (Docker/Caddy path unaffected). This removes the port-injection and CORS work entirely and keeps the API jar identical across deployments.
3. **Scaffold a minimal Tauri project in `desktop/`**. Get it to spawn the JVM (with `QUARKUS_PROFILE=desktop` + the three runtime env vars), poll `/api/ping` (there is no health extension), then point the webview at `http://127.0.0.1:${port}/`. The shell starts on a bundled splash page and navigates the window once the backend answers.
4. **Hide OIDC button** when `VITE_DESKTOP=true` (#2).
5. **Write `scripts/build-desktop.sh`** (see Build Pipeline) and produce a `.dmg`/`.app` for macOS. Verify launch → register → add bookmark → quit → relaunch → bookmark persists.
6. **Single-instance lock** (BR-052-4) via `tauri-plugin-single-instance` — a second launch focuses the existing window instead of spawning another backend. **Graceful shutdown** (BR-052-5): on exit the shell sends the JVM `SIGTERM` (so Quarkus closes SQLite/WAL cleanly), waits up to 5s, then `SIGKILL`s as a fallback.

That covers the learning goal. Code signing, notarization, auto-update, and Windows/Linux installers are natural follow-ups. (JRE bundling via jlink, originally a follow-up, was pulled forward — it turned out to be required; see #4c / Build Pipeline.)

## Verification

After step 5:

- Launch the bundled `.app` from `/Applications`
- Confirm Quarkus starts (Console.app stdout, or shell-piped log file)
- Confirm SPA loads and `/api/ping` returns 204 from inside webview devtools
- Register a new local user via form auth
- Add a bookmark, restart the app, verify it persists
- Confirm DB file is at `~/Library/Application Support/LinkWeave/chainlink.db`, **not** in the app bundle (BR-052-1)
- Quit the app; confirm Quarkus actually exits (`pgrep -f quarkus-run` returns nothing)
- Launch a second instance; confirm the existing window comes forward instead of a duplicate launching (BR-052-4)
- Disconnect from the network; confirm bookmarks still browse/edit normally and only favicon fetches fail (A6)

## Out of Scope (Explicit)

- GraalVM native-image build — possible but slower steady-state and complex to maintain
- OIDC desktop flow (Device Flow / loopback handler)
- Auto-update infrastructure
- Code signing / notarization
- Windows / Linux installers
- Further trimming the bundled jlink runtime (currently `ALL-MODULE-PATH` for correctness; a `jdeps`-computed module set would shrink it)
