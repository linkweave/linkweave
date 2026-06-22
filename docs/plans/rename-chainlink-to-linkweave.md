# Rename: `chainlink` → `linkweave` — Implementation Plan

Status: **DRAFT for review — do not execute yet.**
Scale: ~1,930 occurrences across 484 files (`.java` 1302, `.md` 372, `.ts` 114, `.yml` 41, `.json` 35, `.properties` 34, `.vue` 26, `.html` 16, `.xml` 6, `.sql` 1).

## 1. Goal & scope

Rename the **product/codebase identity** from `chainlink` to `linkweave`. The public brand is already `linkweave.dev` (Cloudflare tunnel). This plan covers the source tree, build, CI, and local runtime.

**Included** beyond the literal text rename:
- **Component suffix `Cl` → `Lw`** (ChainLink → LinkWeave): 20 design-system components (`ButtonCl.vue` → `ButtonLw.vue`, etc.). See §3 Tier E / §6a.
- **Browser extension** identity (name, titles, description, id, zip filename). See §3 + §7a.

**Explicitly phased OUT of the first pass** (separate, later cutover — see §8):
- The deployed backend hostnames `chainlink.markushofstetter.com` / `dev-chainlink.markushofstetter.com` and their **OIDC cookie domains**. These are tied to DNS + the OIDC/Keycloak provider config and must be changed in lockstep with infra, not in the code-rename PR. The extension's `host_permissions` point at these and therefore **also stay** until cutover.
- **All env-var names supplied by the prod host** (`CL_*`, `CLINK_*`, `CHAINLINK_*` secrets/paths) — they're a runtime contract with the deploy repo `~/source/IdeaProjects/docker-compose-config/chainlink` (separate git repo) + its host `.env`. Renaming them code-side without the host breaks prod. See §8a.

Decisions: **D1 = yes** (code identity now, hostnames later), **D2 = yes** (rename + move DB), **D3 = yes** (Tauri bundle id), **D4 = yes** (`Cl`→`Lw`), **D5 = yes** (extension id).

## 2. Canonical naming map

| From | To | Applies to |
|------|----|-----------|
| `chainlink` | `linkweave` | identifiers, paths, lowercase strings |
| `Chainlink` | `Linkweave` | Title-case prose / display text (33 hits) |
| `CHAINLINK` | `LINKWEAVE` | env vars, constants (9 hits) |
| `org.chainlink` | `org.linkweave` | Java package (true move) |

## 3. Risk tiers

**Tier A — sensitive / coupled (review each by hand):**
- `application.properties`:
  - `quarkus.datasource.jdbc.url ... ${CHAINLINK_DB_PATH:../developer-local-settings/chainlink.db}` and `%test ... chainlink-test.db` → rename file + env var (D2). NOTE: this is the **dev/local** default only — **prod sets `QUARKUS_DATASOURCE_JDBC_URL` directly** in compose (§8c), so the prod DB move is a separate server-side step.
  - `quarkus.http.auth.form.cookie-name=chainlink-credential` and `same-site-cookie."chainlink-credential"` → changing **logs out all sessions** (acceptable pre-launch).
  - `%test/%desktop ... session.encryption-key=chainlink-*-key` → literal secrets; changing invalidates existing encrypted sessions (fine pre-launch).
  - `cookie-domain=*chainlink.markushofstetter.com` → **Tier-out (§8), do NOT change in pass 1.**
  - `quarkus.application.name`, `app.deployment.app-project`, `CHAINLINK_DESKTOP_WEB_ROOT`, fault-tolerance keys (`org.linkweave.api...`), `chainlink.favicon.*`, `chainlink.fetch.*` config namespaces.
- `desktop/src-tauri/tauri.conf.json` — **bundle identifier / productName**. Changing the identifier changes the desktop app's update channel & data dir; intended but call it out (D3).
- DB files on disk: `developer-local-settings/chainlink.db`, `api/chainlink-test.db` — physically move/rename or the app silently creates a fresh empty DB.
- **Browser extension** (`frontend/extension-public/manifest.json`, `frontend/src/extension/{popup,options}.html`):
  - `name` "Chainlink"→"Linkweave", `description`, `default_title`, `<title>` tags → change now (user-visible).
  - **extension `id` `chainlink@markushofstetter.com`** → changing it changes the installed-extension identity (like the Tauri bundle id) — fine pre-launch (D5 = yes).
  - `host_permissions` (`*chainlink.markushofstetter.com/*`) → **keep** (point at the backend, which stays per D1; revisit at §8).
  - `package:extension` zip filename `chainlink-ext-*` in `frontend/package.json` → `linkweave-ext-*`.

**Tier B — build / CI / deploy:**
- `api/pom.xml`: `<groupId>org.chainlink</groupId>`, `<artifactId>chainlink</artifactId>`.
- `.gitea/workflows/{build,e2e}.yml`, `.github/workflows/build-desktop.yml` — artifact/image names.
- `screenshot-service/docker-compose.yml`, `docs/monitoring/*` (prometheus job, `grafana-.../chainlink-overview.json`).
- nginx upstream `proxy_pass http://chainlink` + the server config we just edited (deployment-side, in the infra repo/host — track separately).

**Tier C — Java package move (mechanical but must use IDE):**
- `api/src/main/java/org/chainlink` and `api/src/test/java/org/chainlink` → `org/linkweave`.
- Use **IntelliJ "Refactor → Rename" on the package** (updates dirs, `package` decls, imports, and string refs to `org.chainlink.*` in `application.properties`). Do NOT `sed` this.

**Tier D — bulk low-risk text:**
- `.md` (372), code comments, i18n display strings, HTML titles. Safe scripted replace with the casing map, then eyeball the diff.

**Tier E — component suffix `Cl` → `Lw` (IDE-driven):**
- 20 files `frontend/src/.../*Cl.vue` → `*Lw.vue` (Button, Input, Select, Dialog, DialogFooter, Switch, Textarea, ColorInput, FormField, Collapsible, DropdownMenuContent, DropdownMenuItem, HelpPopover, LanguageSwitcher, UserMenu, Buildversion, FolderSelect, FolderBreadcrumb, Header, Sidebar).
- Components are **explicitly imported** (17 importing files), not auto-imported — so each rename touches the file name, the symbol, the import path, and every `<ButtonCl>` template usage. Use IDE rename (IntelliJ/Volar "Rename symbol") per component, or rename file → let the IDE update refs. Independent of the `chainlink` text rename; can be its own commit.
- Watch for kebab-case template usage (`<button-cl>`) and any `components.d.ts` if Volar generates one.

## 4. Phase 0 — Prep

1. Clean working tree; start from `main` (current branch is `feature/clickable-preview` — finish/merge or branch off main).
2. Create branch `rename/chainlink-to-linkweave`.
3. **Back up the DB**: copy `developer-local-settings/chainlink.db` somewhere safe.
4. Set up the GitHub **mirror** (decision: keep Gitea primary):
   ```bash
   # create empty repo github.com/linkweave/<repo> first (no README)
   git remote add github git@github.com:linkweave/<repo>.git
   git push github --all && git push github --tags
   # ongoing mirror: push to both, or add a Gitea Actions step / cron `git push github --mirror`
   ```
   Keep `origin` = Gitea. Treat GitHub as a downstream mirror.

## 5. Phase 1 — Package + build identity (IDE-driven)

1. IntelliJ: rename package `org.chainlink` → `org.linkweave` (main + test). Let it update `application.properties` references.
2. `api/pom.xml`: groupId `org.linkweave`, artifactId `linkweave`.
3. `./mvnw -q -DskipTests package` to confirm it compiles before touching anything else.

## 6. Phase 2 — Sensitive runtime (hand-edited, Tier A)

> **REFINED BOUNDARY (discovered during execution):** config-property KEYS (`chainlink.*`, referenced via `@ConfigProperty` in Java), env-var NAMES (`CHAINLINK_*`/`CL_*`/`CLINK_*`), and **metric names** (`chainlink.collections.total` etc., queried by Grafana) are all **frozen in pass 1** and deferred to Phase 4 (§8a) — they bind to the prod `.env`, the Grafana dashboard, and `desktop/src-tauri/src/lib.rs`. Pass 1 renames only source identifiers + human text.

1. `application.properties`: rename only **non-key/non-contract** values — `quarkus.application.name=chainlink-api`→`linkweave-api`, the form cookie **name** `chainlink-credential` (+ its `same-site-cookie` refs), and literal encryption-key *defaults* (desktop/test). **Leave** all `chainlink.*` keys, `CHAINLINK_*`/`CL_*`/`CLINK_*` `${...}` env names, and `cookie-domain` lines **untouched** (Phase 4).
2. DB (D2): env var `CHAINLINK_DB_PATH`→`LINKWEAVE_DB_PATH`, default path → `linkweave.db`, test db → `linkweave-test.db`; **move the actual files**: `git mv developer-local-settings/chainlink.db .../linkweave.db` (and delete/regenerate the test db). Grep for `CHAINLINK_DB_PATH` / `CHAINLINK_DESKTOP_WEB_ROOT` in any deploy scripts on the host.
3. `V1__Initial_schema.sql` header comment.
4. Tauri (D3): bundle identifier + productName.

### Phase 2a — Component suffix `Cl` → `Lw` (Tier E, IDE-driven)

Best done as its **own commit** (mechanical, easy to review/revert). For each of the 20 `*Cl.vue`: rename file `XxxCl.vue`→`XxxLw.vue` and rename the symbol so the IDE updates the 17 importing files + template usages. Then grep to confirm nothing dangles:
```bash
grep -rIE '\b[A-Z][A-Za-z]+Cl\b|<[a-z-]+-cl\b' frontend/src --include='*.vue' --include='*.ts'  # expect empty
```
`pnpm run type-check` is the gate here — a missed import fails the build loudly.

## 7. Phase 3 — Bulk text + frontend/CI (Tier B & D)

Scripted replace with the 3-casing map across `.md .ts .vue .html .json .yml .yaml .xml` (excluding `node_modules`, `target`, `dist`), then review diff. Specifically verify: i18n `de.json`/`en.json` user-facing strings, `index.html` `<title>`, CI workflow image/artifact names, grafana dashboard filename + contents, prometheus job name.

### Phase 3a — Browser extension identity

Hand-edit (don't blanket-replace, because of `host_permissions`):
- `manifest.json`: `name`, `description`, `default_title`, **`id` → `linkweave@markushofstetter.com`** (D5); **leave `host_permissions` hostnames** until §8.
- `popup.html` / `options.html` `<title>`.
- `frontend/package.json`: `chainlink-ext-*` zip filename → `linkweave-ext-*`.
- Icons (`icon-48.png` etc.) are art — swap when branding assets are ready (not blocking).

## 8. Phase 4 — Deployment cutover (SEPARATE, later)

Lives in a **separate repo**: `~/source/IdeaProjects/docker-compose-config/chainlink/docker-compose.yml` (+ a host `.env` that supplies the secrets/paths). Not in the code-rename PR — coordinate all of the below together so prod doesn't break.

**8a. Env-var contract (do NOT touch in pass 1).** These names are read from the prod `.env` via `${...}` in `application.properties`; renaming the code side without the host side breaks prod (secrets unread → OIDC/login down). Three inconsistent prefixes today:
| Env var (host `.env` + compose) | Binds to | 
|---|---|
| `CLINK_API_DEPLOYMENT_ENVIRONMENT/PUBLIC_URL/INSTANCE` | `app.deployment.*` |
| `CL_OIDC_CLIENT_SECRET` | `quarkus.oidc.credentials.secret` |
| `CL_FORM_LOGIN_COOKIE_SECRET` | `quarkus.http.auth.session.encryption-key` |
| `CHAINLINK_SCREENSHOT_SERVICE_URL`, `CHAINLINK_FAVICON_CACHE_DIR`, `CHAINLINK_SCREENSHOT_CACHE_DIR`, `CHAINLINK_FETCH_SKIP_DOMAINS` | `chainlink.*` config namespaces |

At cutover, rename each on **both** sides + the host `.env` simultaneously. **D6**: standardize on a single prefix — **recommended `LINKWEAVE_`** (or `LW_` for brevity) — instead of the current `CL_`/`CLINK_`/`CHAINLINK_` mix.

**8b. CI image names.** `git.markushofstetter.com/dividbzero/chainlink-{api,frontend,screenshot-service}` are produced by `.gitea/workflows`. Rename the workflows' image tags AND the `image:` refs in this compose in the same change, or the pull breaks.

**8c. Production DB (real data!).** Compose sets `QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/data/chainlink.db` with volume `/mnt/data/chainlink:/data` — so prod ignores `CHAINLINK_DB_PATH` (that default is dev-only; D2's rename is safe in pass 1 for local only). Renaming the prod DB = stop the stack, `mv /mnt/data/chainlink/chainlink.db .../linkweave.db` on the server, update the URL, restart. Or just leave the prod filename as-is — it's internal.

**8d. Other.** `container_name: chainlink-api`, external network `chainlink-internal` (needs `docker network create linkweave-internal` + recreate dependents), OIDC client redirect URIs + cookie-domain (`*chainlink.markushofstetter.com`), extension `host_permissions`, nginx upstream name. Sequence so sessions/redirects don't break.

**8e. Observability identity.** The Sentry project slug `pom.xml` sentry-upload `<project>chainlink-api</project>` is **frozen — rename in the external service first**: renaming the slug code-side routes source-map uploads to a non-existent Sentry project until the project is renamed in the Sentry dashboard. Metric names (`chainlink_*`, queried by Grafana) belong here too. NOTE: `app.deployment.app-project` was renamed to `linkweave` in pass 1 — it's only an MDC log-tag value (`LoggingCommonMDCFieldsRequestFilter`), not env-driven and not a breaking contract; just update any log/Sentry dashboard filter on the old `chainlink` value.

## 9. Verification gate (before merge)

```bash
cd api && ./mvnw verify                       # unit + integration
cd frontend && pnpm run type-check && pnpm run lint
pnpm exec playwright test --project=chromium
grep -rIi 'chainlink' . --include='*.java' --include='*.ts' --include='*.vue' \
  --include='*.properties' --include='*.xml' --include='*.json' \
  | grep -vE 'node_modules|/target/|/dist/'   # expect only intentional infra leftovers (cookie-domain)
```
Plus a manual smoke test: log in, load a collection, confirm the DB at the new path is the migrated one (not empty), confirm the preview/tunnel still serves at https://linkweave.dev.

## 10. Rollback

Single squashed branch; revert = drop the branch. DB is restorable from the Phase-0 backup. Mirror push is additive (no Gitea impact).

## Decisions (all resolved)
- **D1 = yes**: Code identity now; deployment hostnames/OIDC cookie-domains + extension `host_permissions` deferred to §8.
- **D2 = yes**: Rename DB file + `CHAINLINK_DB_PATH`→`LINKWEAVE_DB_PATH`, physically move the file.
- **D3 = yes**: Change Tauri bundle identifier now (before any public desktop release).
- **D4 = yes**: Component suffix `Cl`→`Lw` (20 components, Phase 2a).
- **D5 = yes**: Change extension `id` to `linkweave@markushofstetter.com` now (pre-launch).
- **D6 = OPEN**: Standardize the env-var prefix (`CL_`/`CLINK_`/`CHAINLINK_` → one prefix) at the Phase 4 cutover. Rec: `LINKWEAVE_`. *Not part of pass 1 — deployment-coupled (§8a).*
