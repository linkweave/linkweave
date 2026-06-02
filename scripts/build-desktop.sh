#!/usr/bin/env bash
#
# Builds the Chainlink desktop bundle (UC-052). Cross-platform; see docs/desktop-app.md
# "Build Pipeline". Chains the three toolchains in dependency order and fails fast.
#
#   frontend (VITE_DESKTOP=true)  ->  backend (quarkus.profile=desktop)  ->  stage  ->  tauri build
#
# Supports macOS, Linux, and Windows (MSYS2/Git Bash). On GitHub Actions this runs on
# all three hosted runner types; the platform detection in stages 5/6 selects the right
# bundle format automatically.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# A trimmed Java runtime is bundled by default: a dependency requires Java 25, and a desktop launch
# resolves whatever (often older) `java` the OS has. Pass --no-jre to rely on a system Java 25+.
BUNDLE_JRE=true
SKIP_SMOKE=false
for arg in "$@"; do
  case "$arg" in
    --no-jre) BUNDLE_JRE=false ;;
    --skip-smoke) SKIP_SMOKE=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '\n=== %s ===\n' "$1"; }

OS="$(uname -s)"
case "$OS" in
  Darwin) PLATFORM="macos" ;;
  Linux)  PLATFORM="linux" ;;
  MINGW*|MSYS*|CYGWIN*) PLATFORM="windows" ;;
  *) echo "unsupported OS: $OS" >&2; exit 1 ;;
esac
echo "Detected platform: $PLATFORM ($OS)"

# Stage 0 — preflight: fail early with a clear message if a toolchain is missing.
log "0/7 preflight"
for tool in node pnpm java cargo; do
  command -v "$tool" >/dev/null 2>&1 || { echo "missing required tool: $tool" >&2; exit 1; }
done
cargo tauri --version >/dev/null 2>&1 || {
  echo "missing Tauri CLI — run: cargo install tauri-cli --locked" >&2; exit 1; }
node -v; pnpm -v; java -version 2>&1 | head -1; cargo --version

# Stage 1 — stamp version into tauri.conf.json and package.json. Single source of truth: CI sets
# DESKTOP_VERSION (computed once in the workflow); a local run falls back to git.
log "1/7 stamp version"
VERSION="${DESKTOP_VERSION:-$(git describe --tags --always --dirty 2>/dev/null || git rev-parse --short=7 HEAD)}"
echo "Version: $VERSION"
cd "$REPO_ROOT/desktop/src-tauri"
TMP=$(jq --arg v "$VERSION" '.version = $v' tauri.conf.json) && echo "$TMP" > tauri.conf.json
cd "$REPO_ROOT/desktop"
TMP=$(jq --arg v "$VERSION" '.version = $v' package.json) && echo "$TMP" > package.json
cd "$REPO_ROOT"

# Stage 2 — build the SPA with the desktop flag (hides the OIDC button; #2).
log "2/7 build SPA (VITE_DESKTOP=true)"
( cd frontend && pnpm install --frozen-lockfile && VITE_DESKTOP=true pnpm run build )

# Stage 3 — package the backend with the desktop profile: root-path=/, REST under /api, OIDC
# compiled out (#4c). Same fast-jar layout as the Docker build, different build-time config.
log "3/7 package backend (quarkus.profile=desktop)"
MVNW="./mvnw"
if [ "$PLATFORM" = "windows" ]; then
  MVNW="./mvnw.cmd"
fi
( cd api && $MVNW -q -DskipTests package -Dquarkus.profile=desktop )

# Stage 4 — smoke-test: the packaged jar must actually boot under the desktop profile and answer
# the readiness probe. A @QuarkusTest runs under the `test` profile and would NOT catch a desktop
# profile missing required config (Sentry DSN, deployment env, cookie secret), so verify the real
# artifact here before bundling a backend that would only ever spin on the splash screen.
if [ "$SKIP_SMOKE" = true ]; then
  log "4/7 smoke-test SKIPPED (--skip-smoke)"
else
  log "4/7 smoke-test backend boots (quarkus.profile=desktop)"
  SMOKE_PORT=18123
  SMOKE_LOG="/tmp/chainlink-desktop-smoke.log"
  QUARKUS_PROFILE=desktop QUARKUS_HTTP_HOST=127.0.0.1 QUARKUS_HTTP_PORT="$SMOKE_PORT" \
    CHAINLINK_DB_PATH="$(mktemp -d)/smoke.db" CHAINLINK_FAVICON_CACHE_DIR="$(mktemp -d)" \
    CHAINLINK_DESKTOP_WEB_ROOT="$REPO_ROOT/frontend/dist" \
    java -jar api/target/quarkus-app/quarkus-run.jar >"$SMOKE_LOG" 2>&1 &
  SMOKE_PID=$!
  trap 'kill "$SMOKE_PID" 2>/dev/null || true' EXIT
  smoke_ok=false
  for _ in $(seq 1 40); do
    sleep 1
    if curl -fs -o /dev/null "http://127.0.0.1:$SMOKE_PORT/api/ping"; then smoke_ok=true; break; fi
    kill -0 "$SMOKE_PID" 2>/dev/null || break   # JVM exited (crashed) — stop waiting
  done
  kill "$SMOKE_PID" 2>/dev/null || true
  trap - EXIT
  if [ "$smoke_ok" != true ]; then
    echo "ERROR: desktop-profile backend did not answer /api/ping. Backend log tail:" >&2
    tail -30 "$SMOKE_LOG" >&2
    exit 1
  fi
  echo "backend booted and answered /api/ping under the desktop profile"
fi

# Stage 5 — stage payloads as Tauri resources. The SPA is NOT baked into the jar; it ships as a
# sibling folder and is served at runtime from CHAINLINK_DESKTOP_WEB_ROOT. A trimmed Java 25
# runtime is jlinked in so the bundle is self-contained. Idempotent.
log "5/7 stage Tauri resources"
BIN="desktop/src-tauri/bin"
rm -rf "$BIN/quarkus-app" "$BIN/web" "$BIN/runtime"
mkdir -p "$BIN"
cp -R api/target/quarkus-app "$BIN/quarkus-app"
cp -R frontend/dist "$BIN/web"
if [ "$BUNDLE_JRE" = true ]; then
  echo "jlinking a trimmed Java runtime…"
  # -version makes `java` exit 0 (otherwise it errors "no main class", which trips set -e here).
  JAVA_HOME_DIR="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.home/{print $2; exit}')"
  JLINK="$JAVA_HOME_DIR/bin/jlink"
  if [ "$PLATFORM" = "windows" ]; then
    JLINK="$JAVA_HOME_DIR/bin/jlink.exe"
  fi
  "$JLINK" --add-modules ALL-MODULE-PATH --module-path "$JAVA_HOME_DIR/jmods" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$BIN/runtime"
  # jlink emits read-only files (e.g. under legal/); make them writable so tauri-build can
  # re-copy them into its OUT_DIR on incremental builds instead of failing with EACCES.
  chmod -R u+w "$BIN/runtime" 2>/dev/null || true
else
  echo "--no-jre: not bundling a runtime; the app will require a system Java 25+ on PATH"
  # tauri.conf.json references bin/runtime as a resource, so give it an empty dir to satisfy the
  # bundler; the shell falls back to `java` on PATH when no real runtime is present.
  mkdir -p "$BIN/runtime"
fi

# Stage 6 — bundle. Platform-appropriate targets are selected automatically by Tauri
# when "targets": "all" is set in tauri.conf.json.
log "6/7 tauri build"
( cd desktop && cargo tauri build )

# Stage 7 — collect platform-appropriate artifacts in one predictable place.
log "7/7 collect artifact"
chmod -R u+w desktop/dist 2>/dev/null || true
rm -rf desktop/dist
mkdir -p desktop/dist

case "$PLATFORM" in
  macos)
    find desktop/src-tauri/target/release/bundle -maxdepth 2 \( -name '*.dmg' -o -name '*.app' \) \
      -exec cp -R {} desktop/dist/ \;
    ;;
  linux)
    find desktop/src-tauri/target/release/bundle \( -name '*.deb' -o -name '*.AppImage' \) \
      -exec cp {} desktop/dist/ \;
    ;;
  windows)
    find desktop/src-tauri/target/release/bundle \( -name '*.exe' -o -name '*.msi' \) \
      -exec cp {} desktop/dist/ \;
    ;;
esac
echo
echo "Done ($PLATFORM). Artifacts in $REPO_ROOT/desktop/dist/"
ls -lh desktop/dist/
