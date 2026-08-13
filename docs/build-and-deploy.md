# Building, CI and Deploying

Reference for where the build lives and what gates what. Read this before
claiming anything about CI, before adding a gate, and before cutting a release.

## The one thing to get right first

**CI is Gitea, in `.gitea/workflows/`.** `.github/workflows/` holds exactly one
file — `build-desktop.yml` — because GitHub is used only for its hosted macOS
and Windows runners. Grepping `.github/` and concluding "there is no CI for X"
is wrong every time; it has been made at least once.

| Workflow | Trigger | What it is for |
| --- | --- | --- |
| `.gitea/workflows/build.yml` | push/PR to `main`, tags `v*` | The main pipeline: API, frontend, CLI, extension, images, deploy, release |
| `.gitea/workflows/e2e.yml` | push/PR to `main` | Playwright suite against a real API + Vite pair. Separate from `build.yml` so a flake cannot block image publishing |
| `.gitea/workflows/quality.yml` | push/PR to `main` | Java CPD (enforced), frontend lint — stylelint + oxlint + eslint (enforced), fallow dead-code (enforced vs. baseline), duplication/health (report-only). **Frontend only** — the CLI's dead-code gate rides its own `check` in `build.yml`. |
| `.gitea/workflows/publish-cli.yml` | tags `cli-v*` | Publishes `@linkweave/cli` to npm |
| `.gitea/workflows/site.yml` | push/PR touching `site/**` | Builds + type-checks the marketing page (deployed via Cloudflare Pages, not Docker) |
| `.gitea/workflows/pi-review.yml` | PR opened/synchronized | Automated code review comments |
| `.github/workflows/build-desktop.yml` | tags `v*`, manual dispatch | Tauri desktop bundles for macOS/Windows/Linux |

## Per-module gate commands

These are what CI runs, so run them locally before pushing.

```bash
cd api      && ./mvnw package        # compiles + runs every test (all match surefire's *Test)
cd frontend && pnpm run check        # type-check + lint + fallow dead-code
cd cli      && pnpm run check        # type-check + oxlint + fallow dead-code + vitest
```

Where each is enforced:

- **api** — `build.yml → build`: `./mvnw package`, then `jacoco:check` as a
  hard gate at **≥ 70 % line coverage**. A coverage regression blocks the image
  push and therefore the deploy. `quality.yml → backend-cpd` separately fails on
  any new Java copy-paste clone ≥ 100 tokens (baseline is zero).
- **cli** — `build.yml → test-cli`: `pnpm run check && pnpm run build` on a
  **Node 22 and 24 matrix** (22 is the floor `engines.node` claims and npm only
  warns about, so it is proven rather than asserted), then packs the tarball and
  installs it into a throwaway prefix to catch a broken `files`/`bin` entry
  before a release tag does.
- **frontend** — split across three places, none of them `pnpm run check`:
  `build.yml → test-frontend` runs vitest; `quality.yml → frontend-quality` runs
  stylelint, oxlint, eslint and fallow dead-code; **type-check is enforced by the
  image build** (`frontend/Dockerfile` runs `pnpm run build`, which is
  `run-p type-check build-only`, so a type error fails the Docker build). CI
  invokes the linters as `pnpm exec`, never `pnpm run lint` — that script passes
  `--fix`, which on a runner repairs the violation, passes, and discards the
  repair. Keep any lint added there non-fixing.
- **e2e** — `e2e.yml` builds the API and the CLI, generates a dev TLS cert with
  mkcert, boots Quarkus on 8443 and Vite on 5173, and runs Chromium. The CLI's
  e2e specs live in `frontend/e2e/cli.spec.ts` and need `cli/dist/main.js`, which
  that workflow builds explicitly.

Adding a lint or analysis gate: put it inside the module's `check` script, not
as a separate CI step — `check` is already wired into `build.yml` and, for the
CLI, into `prepublishOnly`. One edit then covers local, CI and publish.

## Releases

Two independent tag lines. Do not cross them.

- **`v*` — the application.** Fires `build.yml` (images to
  `git.markushofstetter.com`, mirrored to `ghcr.io/linkweave/*`, extension built
  and published to the Chrome Web Store and AMO, Gitea release created) and
  `build-desktop.yml` (desktop bundles). A push to `main` deploys by committing
  new image tags to the external `docker-compose-config` repo.
- **`cli-v*` — the CLI only.** Fires `publish-cli.yml` and *nothing else*: no
  images, no frontend, no extension, no deploy. The tag must match
  `cli/package.json` exactly or the workflow refuses; it exits cleanly if that
  version is already on npm, so a re-run is safe. `prepublishOnly` re-runs
  `check` + `build`. Rationale is AD-5 in `docs/cli-tool.md`.

```bash
cd cli
npm version patch            # updates package.json
git commit -am "chore(cli): release 0.2.1"
git tag cli-v0.2.1           # must equal package.json
git push && git push --tags
```

## Local dev

Assume both are already running; start them only after checking.

```bash
cd api      && ./mvnw quarkus:dev    # https://localhost:8443
cd frontend && pnpm run dev          # https://local-linkweave.localhost:5173, proxies /api
```

The e2e suite and the `verify` skill both assume that pair. Playwright's
`webServer` starts Vite itself, but **not** Quarkus — a suite that fails with
`ECONNREFUSED 127.0.0.1:8443` in `beforeAll` means the API is down, not that the
test is broken.
