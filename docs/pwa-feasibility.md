# PWA Feasibility Study

**Requirement:** FR-091
**Status:** Draft
**Date:** 2026-05-19
**Related:** [offlinemode.md](offlinemode.md), [browser-extension.md](browser-extension.md), [cli-tool.md](cli-tool.md)

---

## TL;DR

**Yes, a PWA is feasible — and most of the work is already done.** `vite-plugin-pwa` is configured (`frontend/vite.config.ts:27-52`) with a manifest, workbox runtime caching, and an `autoUpdate` service worker. The Offline Mode initiative (FR-052–FR-061) already commits to the service-worker + IndexedDB stack a PWA needs. To call ourselves "installable" the remaining gap is small: proper icons, a quick Lighthouse audit, and a decision about Web Share Target. Push notifications and write-queue offline are separate, larger initiatives and should be deferred.

**Recommendation:** ship installable-PWA as a small follow-up (~3–5 dev-days) after the offline mode work lands. Treat Web Share Target as an optional add-on. Defer push notifications.

---

## 1. Current State

What's already wired in `frontend/`:

| Area | Status | Reference |
|------|--------|-----------|
| `vite-plugin-pwa` dependency | ✅ Installed | `package.json` |
| Web App Manifest (name, short_name, theme, display, icons) | ✅ Defined | `vite.config.ts:40-51` |
| Service worker registration (`autoUpdate`) | ✅ Plugin-injected | `vite.config.ts:28` |
| `controllerchange` → reload | ✅ Wired | `main.ts:30-34` |
| App-shell navigate fallback | ✅ Configured | `vite.config.ts:30` |
| Runtime caching for static assets | ✅ Configured | `vite.config.ts:32-38` |
| HTTPS (required for SW) | ✅ Dev (self-signed); prod assumed via self-hosted reverse proxy | `vite.config.ts:64-69` |
| `theme-color` meta tag | ✅ Present | `index.html:7` |
| Offline data (IndexedDB, user-scoped) | 🟡 Planned | `offlinemode.md`, FR-052–FR-061 |

What's missing for "installable, passes Lighthouse PWA":

- **Real icon assets at 192×192 and 512×512.** The manifest references `/chainlink-favicon.png` for both, but `frontend/public/` only contains `chainlink-favicon.png` (a single favicon-sized PNG) and `favicon.ico`. Browsers will install but the install dialog and home-screen icon will look poor.
- **No `purpose: "maskable"` icon.** Android adaptive icons get cropped without one.
- **No `screenshots` array in the manifest.** Chrome's richer install UI (especially on Android) needs at least one wide + one narrow screenshot.
- **No explicit `<link rel="manifest">` in `index.html`.** vite-plugin-pwa injects this at build time — verify it lands in the production build (`pnpm run build` then inspect `dist/index.html`).
- **No Web Share Target** (`share_target` in manifest). Without it, the OS "Share" sheet on mobile can't send a URL to Chainlink.

---

## 2. Installability

**Verdict: easy.** Required boxes per the [W3C install criteria](https://web.dev/articles/install-criteria):

| Criterion | Status |
|-----------|--------|
| Served over HTTPS | ✅ (prod = self-hosted; operator's responsibility) |
| Valid Web App Manifest with `name`, `start_url`, `icons`, `display: standalone` | 🟡 Partial — need real icons |
| Service worker with a `fetch` handler | ✅ (workbox-generated) |
| Not already installed | ✅ |

Concrete fix-list:
1. Generate `chainlink-192.png`, `chainlink-512.png`, and `chainlink-512-maskable.png`. Update the manifest icons array.
2. Add 1–2 desktop and 1–2 mobile screenshots (`form_factor: "wide"` / `"narrow"`) under `public/screenshots/`.
3. Run Lighthouse → PWA category in Chrome DevTools against a production build; fix flagged items.

Estimated effort: **~1 day** (mostly art assets + verification).

---

## 3. Offline Capability

The offline mode plan (`docs/offlinemode.md`) already maps this. Summary of what works offline vs. what doesn't, for a PWA built on top of that plan:

### Works offline
- App shell loads (cached by service worker).
- Browse cached collections, folders, bookmarks, tags (read from IndexedDB).
- Search/filter over cached data.
- Auth: trusts the cached identity stored under `{email}:user-info` (per C-009 "Offline Auth Trust Model"). No password re-check while offline.

### Does NOT work offline
- **Writes** (create/edit/delete bookmark, move, share). The plan explicitly says read-only — no write queue, no background sync, no conflict resolution (FR-056).
- **Collaboration / live updates.** A shared collection won't see another user's changes until back online.
- **Saving from the extension.** The extension talks to the API; if the API is unreachable, the extension fails the save. This is an extension concern, not a PWA concern.
- **First login.** Per C-010, offline mode requires a prior successful login while online.

This is acceptable for a "phase 1" PWA. A future "write-queue" phase would be a separate, larger workstream and probably wants conflict-resolution rules per entity type.

---

## 4. Authentication Implications

Auth today: form-based (`j_security_check`), cookie session (see `browser-extension.md:30`).

When a user installs the PWA and launches it in standalone mode:

- **It's still a browser context.** Same cookie jar as the underlying browser, same origin, same TLS. Login flows work unchanged.
- **No change needed for cookie/session logic.** This is in contrast to a *native* app, which would need an alternative (API key — already planned in FR-083/FR-084 for the CLI).
- **OAuth/Google redirect flows** work but with one caveat: a popup-based OAuth flow can look awkward in standalone display mode (popup opens in the system browser). If the project moves to Google sign-in later, prefer redirect-based flow over popup.
- **Logout** clears the cookie as today. The offline-mode plan adds session-cleanup for IndexedDB (FR-041 already covers this for Pinia state; the IndexedDB extension is in the offline plan).

No blocking issues. No new auth surface area introduced by the PWA itself.

---

## 5. Overlap with Browser Extensions and CLI

PWA, extension, and CLI are complementary rather than redundant:

| Use case | Best tool | Why |
|----------|-----------|-----|
| Save the page I'm reading right now | Extension | Only the extension can read the *active tab's* URL from the surrounding browser. A PWA can't. |
| Manage / browse / organize my bookmarks | PWA | Full UI, same as the web app, installable, offline-capable. |
| "Share to Chainlink" from mobile OS share sheet | PWA + `share_target` | Extensions don't run on mobile Chrome. PWAs do, and registering as a share target gets Chainlink into the system Share menu. |
| Scripted/CLI workflows | CLI (FR-085) | Headless automation, no browser. |

The PWA does *not* obsolete the extension. The most interesting overlap is **mobile**: today there's no good way to save a page from a phone, because the extension only runs on desktop browsers. A PWA with `share_target` fills that gap and is the strongest argument for shipping this initiative.

---

## 6. Effort Estimate

Assumes the offline mode work (FR-052–FR-061) is already delivered. PWA-specific deltas:

| Slice | Effort | Notes |
|-------|--------|-------|
| Real icon set (192, 512, maskable) + manifest update | 0.5d | Mostly art assets |
| Manifest screenshots (wide + narrow) | 0.5d | Capture from staging |
| Lighthouse PWA audit + fix gaps | 0.5–1d | Unknown until run |
| Verify manifest link injection in prod build | 0.25d | One-time check |
| `share_target` + handler route (`/share?url=...&title=...`) | 1–2d | Frontend route + backend already accepts POSTs |
| End-to-end install test (Chrome desktop, Android Chrome, iOS Safari A2HS) | 0.5d | Manual smoke |
| **Subtotal: installable PWA** | **~3–5 dev-days** | |
| Push notifications (VAPID, SW push handler, backend dispatch, UI to opt in) | ~5–10 dev-days | Separate workstream — defer |
| Offline writes / sync queue | ~10+ dev-days | Separate workstream — defer |

---

## 7. Recommended Next Steps

1. **Ship installable PWA** as FR-091's follow-up requirement once offline mode (FR-052–FR-061) is in. Split into:
   - New FR: "Installable PWA" — covers icons, screenshots, Lighthouse pass.
   - New FR (optional): "Web Share Target" — mobile share-sheet integration.
2. **Do not** commit to push notifications or offline writes in the same slice. They each merit their own feasibility/spec round.
3. **Verify** the production build emits `<link rel="manifest">` and registers the service worker — vite-plugin-pwa does this automatically but it should be confirmed once.

---

## 8. Risks

- **iOS limitations.** iOS Safari supports A2HS but limits service-worker storage (~50MB) and aggressively evicts it after weeks of disuse. Treat iOS as best-effort, not parity with Android/desktop. Document this for users.
- **Self-hosted HTTPS.** PWAs require HTTPS in production. Some self-hosters terminate TLS at a reverse proxy with self-signed certs; the SW will refuse to register without a trusted certificate. Call this out in the self-hosting docs.
- **Icon/manifest mistakes are silent.** A broken icon doesn't fail the build — it just gives users an ugly install dialog. Mitigation: include a Lighthouse PWA audit step in CI, or at minimum a manual smoke test before releases that touch the manifest.
