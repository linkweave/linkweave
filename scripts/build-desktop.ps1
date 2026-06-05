#
# Builds the Chainlink desktop bundle (UC-052) on Windows.
# PowerShell equivalent of build-desktop.sh. Run from repo root:
#   powershell -ExecutionPolicy Bypass -File scripts\build-desktop.ps1
#
$ErrorActionPreference = "Stop"
$REPO_ROOT = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $REPO_ROOT

# --- Argument parsing ---
$BundleJRE = $true
$SkipSmoke = $false
foreach ($arg in $args) {
    switch ($arg) {
        "--no-jre"    { $BundleJRE = $false }
        "--skip-smoke" { $SkipSmoke = $true }
        default { Write-Error "unknown argument: $arg"; exit 2 }
    }
}

function Log($msg) { Write-Host "`n=== $msg ===`n" }

# PowerShell does NOT treat a native command's non-zero exit as a terminating error (even under
# $ErrorActionPreference='Stop'), so check $LASTEXITCODE after each external command — otherwise a
# failed step (e.g. jlink) silently falls through to a confusing later error.
function Assert-LastExit($what) {
    if ($LASTEXITCODE -ne 0) { Write-Error "$what failed (exit code $LASTEXITCODE)"; exit $LASTEXITCODE }
}

# --- Stage 0: preflight ---
Log "0/7 preflight"
foreach ($tool in @("node", "pnpm", "java", "cargo")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Error "missing required tool: $tool"; exit 1
    }
}
Write-Host "node: $(node -v)"
Write-Host "pnpm: $(pnpm -v)"
# `java --version` writes to stdout (one line); `-version` writes to stderr, which under
# $ErrorActionPreference='Stop' would surface as a terminating NativeCommandError.
Write-Host "java: $(java --version | Select-Object -First 1)"
Write-Host "cargo: $(cargo --version)"

# --- Stage 1: determine version (single source of truth: DESKTOP_VERSION from CI; git fallback) ---
# The tracked tauri.conf.json / package.json are NOT mutated; the version is merged in at bundle
# time via `tauri build --config` (stage 6), so a failed build never leaves the tree dirty.
Log "1/7 determine version"
if ($env:DESKTOP_VERSION) {
    $Version = $env:DESKTOP_VERSION
} else {
    $Version = (git rev-parse --short=7 HEAD)
}
Write-Host "Version: $Version"
$VersionConfig = Join-Path $env:TEMP "tauri-version-$(Get-Random).json"
'{{"version": "{0}"}}' -f $Version | Set-Content $VersionConfig -NoNewline

# --- Stage 2: build SPA ---
Log "2/7 build SPA (VITE_DESKTOP=true)"
Set-Location "$REPO_ROOT\frontend"
pnpm install --frozen-lockfile
Assert-LastExit "pnpm install"
$env:VITE_DESKTOP = "true"
pnpm run build
Assert-LastExit "pnpm run build"
Remove-Item Env:\VITE_DESKTOP

# Stamp build metadata into the SPA so the running app can show its version. The frontend fetches
# /commit.json at runtime (see useCommitInfo.ts) — mirroring the Docker build (frontend/Dockerfile).
# Without this file the app falls back to "version unknown". dist\ is copied to the web root in
# stage 5, so writing it here is enough.
$Commit = (git rev-parse --short=7 HEAD)
if (-not $Commit) { $Commit = "unknown" }
'{{"commit":"{0}","version":"{1}"}}' -f $Commit, $Version | Set-Content "$REPO_ROOT\frontend\dist\commit.json" -NoNewline

# --- Stage 3: package backend ---
Log "3/7 package backend (quarkus.profile=desktop)"
Set-Location "$REPO_ROOT\api"
.\mvnw.cmd -q -DskipTests package "-Dquarkus.profile=desktop"
Assert-LastExit "mvnw package"

# --- Stage 4: smoke-test ---
if ($SkipSmoke) {
    Log "4/7 smoke-test SKIPPED (--skip-smoke)"
} else {
    Log "4/7 smoke-test backend boots (quarkus.profile=desktop)"
    $SmokePort = 18123
    # Fail clearly if the port is already taken, so we never probe an unrelated service.
    if (Get-NetTCPConnection -LocalPort $SmokePort -State Listen -ErrorAction SilentlyContinue) {
        Write-Error "smoke-test port $SmokePort is already in use; free it or pass --skip-smoke."
        exit 1
    }
    $SmokeLog = "$env:TEMP\chainlink-desktop-smoke.log"
    $SmokeDir = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "chainlink-smoke-$(Get-Random)")
    $SmokeDb = Join-Path $SmokeDir "smoke.db"
    $SmokeFav = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "chainlink-fav-$(Get-Random)")

    $env:QUARKUS_PROFILE = "desktop"
    $env:QUARKUS_HTTP_HOST = "127.0.0.1"
    $env:QUARKUS_HTTP_PORT = $SmokePort
    $env:CHAINLINK_DB_PATH = $SmokeDb
    $env:CHAINLINK_FAVICON_CACHE_DIR = $SmokeFav
    $env:CHAINLINK_DESKTOP_WEB_ROOT = "$REPO_ROOT\frontend\dist"

    $proc = Start-Process -FilePath "java" `
        -ArgumentList "-jar", "$REPO_ROOT\api\target\quarkus-app\quarkus-run.jar" `
        -RedirectStandardOutput $SmokeLog `
        -RedirectStandardError "$SmokeLog.err" `
        -PassThru -NoNewWindow

    $smokeOk = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 1
        try {
            $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$SmokePort/api/ping" `
                -UseBasicParsing -ErrorAction Stop
            $smokeOk = $true; break
        } catch {
            if ($proc.HasExited) { break }
        }
    }
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Remove-Item Env:\QUARKUS_PROFILE, Env:\QUARKUS_HTTP_HOST, Env:\QUARKUS_HTTP_PORT,
        Env:\CHAINLINK_DB_PATH, Env:\CHAINLINK_FAVICON_CACHE_DIR, Env:\CHAINLINK_DESKTOP_WEB_ROOT `
        -ErrorAction SilentlyContinue

    if (-not $smokeOk) {
        Write-Error "desktop-profile backend did not answer /api/ping. Log:"
        Get-Content $SmokeLog -Tail 30
        exit 1
    }
    Write-Host "backend booted and answered /api/ping under the desktop profile"
}

# --- Stage 5: stage Tauri resources ---
Log "5/7 stage Tauri resources"
$Bin = "$REPO_ROOT\desktop\src-tauri\bin"
@("$Bin\quarkus-app", "$Bin\web", "$Bin\runtime") | ForEach-Object {
    Remove-Item -Recurse -Force $_ -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $Bin | Out-Null
Copy-Item -Recurse "$REPO_ROOT\api\target\quarkus-app" "$Bin\quarkus-app"
Copy-Item -Recurse "$REPO_ROOT\frontend\dist" "$Bin\web"

if ($BundleJRE) {
    Write-Host "jlinking a trimmed Java runtime..."
    # CI's setup-java sets JAVA_HOME; locally derive it from java.exe's location. (Avoids
    # `java -XshowSettings ... 2>&1`, whose stderr trips $ErrorActionPreference = 'Stop'.)
    $JavaHomeDir = if ($env:JAVA_HOME) { $env:JAVA_HOME }
                   else { Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
    # JDK 25 ships without packaged modules (jmods); jlink links from the run-time image (JEP 493).
    # Enumerate modules from --list-modules, exclude jdk.jlink (a run-time-image link can't contain
    # it), and omit --module-path so jlink uses its own modules either way.
    $modules = ((& "$JavaHomeDir\bin\java.exe" --list-modules) |
        ForEach-Object { ($_ -split '@')[0].Trim() } |
        Where-Object { $_ -and $_ -ne 'jdk.jlink' }) -join ','
    Assert-LastExit "java --list-modules"
    & "$JavaHomeDir\bin\jlink.exe" --add-modules $modules `
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 `
        --output "$Bin\runtime"
    Assert-LastExit "jlink"
    if (-not (Test-Path "$Bin\runtime\bin\java.exe")) {
        Write-Error "jlink produced no runtime at $Bin\runtime"; exit 1
    }
} else {
    Write-Host "--no-jre: not bundling a runtime"
    New-Item -ItemType Directory -Force -Path "$Bin\runtime" | Out-Null
}

# --- Stage 6: tauri build ---
Log "6/7 tauri build"
Set-Location "$REPO_ROOT\desktop"
cargo tauri build --config $VersionConfig
Assert-LastExit "cargo tauri build"

# --- Stage 7: collect artifacts ---
Log "7/7 collect artifact"
$DistDir = "$REPO_ROOT\desktop\dist"
Remove-Item -Recurse -Force $DistDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

Get-ChildItem -Recurse "$REPO_ROOT\desktop\src-tauri\target\release\bundle" `
    -Include "*.exe","*.msi" | Copy-Item -Destination $DistDir

Write-Host "`nDone (windows). Artifacts in $DistDir"
Get-ChildItem $DistDir
