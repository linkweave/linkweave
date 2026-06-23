# Mobile Share-to-Save — Implementation Plan

**Status:** Draft
**Date:** 2026-06-23
**Requirements:** FR-092 (new)
**Use Case:** UC-094 (new)
**Supersedes:** the "installable PWA" framing in [pwa-feasibility.md](pwa-feasibility.md) (see §1 — Rationale)
**Related:** [browser-extension.md](browser-extension.md), [offlinemode.md](offlinemode.md), [desktop-app.md](desktop-app.md)

---

## TL;DR

The original PWA initiative ([pwa-feasibility.md](pwa-feasibility.md), FR-093) evaluated a broad "installable PWA with offline + push" surface. With [desktop-app.md](desktop-app.md) now owning the desktop install story via Tauri, and offline mode (FR-052–FR-061) already shipping, the only remaining **user-facing** use case a PWA uniquely enables is **saving a page from the mobile OS share sheet** — something neither the browser extension (desktop-only) nor the CLI can do.

This plan delivers exactly that one feature via the [Web Share Target API](https://developer.chrome.com/docs/web-platform/web-share-target). Scope is intentionally narrow:

- **In:** manifest `share_target`, a `/share` route, icon polish *required to make the PWA installable* (a prerequisite for share_target on Android — see §3).
- **Out:** desktop install (Tauri owns it), push notifications, offline writes, iOS parity (technically impossible today).

**Effort: ~3–4 dev-days.** No backend changes — the feature reuses `POST /api/bookmarks`.

---

## 1. Why This Replaces the Broad PWA Plan

The feasibility study (FR-093, Done) identified three things an "installable PWA" would buy LinkWeave. Re-evaluating each against initiatives that landed *after* the study was written:

| Original PWA value | Current status | Verdict |
|---|---|---|
| Installable on **desktop** | Cannibalized by [desktop-app.md](desktop-app.md) — a real `.app` (bundled JRE, native SQLite, single-instance lock, OS integration) beats a desktop PWA install decisively | **Drop.** Tauri owns desktop. |
| **Offline** browse on mobile | Already shipped by offline mode (FR-052–FR-061) on top of the same SW + IndexedDB stack a PWA uses. Not a PWA-specific deliverable. | **Already done.** |
| **Mobile share-sheet** integration (`share_target`) | No other initiative fills this gap. Extension is desktop-only; CLI is desktop-only; Tauri doesn't run on phones. | **Keep — the entire remaining use case.** |

The feasibility doc itself flagged this in §5: *"today there's no good way to save a page from a phone… A PWA with `share_target` fills that gap and is the strongest argument for shipping this initiative."* That argument is now the *only* argument left, so the plan is scoped to it.

> Note: `pwa-feasibility.md` is **not** deleted — it remains the historical feasibility record for FR-093. This document is the implementation plan for the follow-up feature the study recommended (its §7, item 1, second bullet: *"New FR (optional): Web Share Target"*).

---

## 2. Scope

### In Scope

- Web App Manifest `share_target` entry (GET method, URL + title params).
- New frontend route `/share` that receives shared URL/title and opens the bookmark dialog pre-filled.
- Authentication hand-off: preserve the share intent across a login redirect.
- Real icon assets (192, 512, maskable) — **required** because Android only surfaces `share_target` for *installed* PWAs, and installation requires proper icons (see §3).
- Manifest `screenshots` (1 wide + 1 narrow) — required for the richer Android install dialog.
- Verify `<link rel="manifest">` lands in the production build.

### Out of Scope (explicit)

- **Desktop install** — owned by [desktop-app.md](desktop-app.md).
- **Push notifications** — separate workstream (VAPID, SW push handler, backend dispatch, opt-in UI). Defer.
- **Offline write queue / sync** — explicitly deferred in [offlinemode.md](offlinemode.md) (FR-056). Sharing while offline surfaces the existing offline banner; the save is not queued.
- **iOS parity.** iOS Safari does not implement Web Share Target. iOS users are no worse off than today (they copy/paste the URL manually). See §8.
- **Share of selected text / images** (`text` payload, `multipart/form-data` POST). Bookmark targets are URLs only; GET with `url`/`title` suffices.

---

## 3. Key Constraint: Share Target Requires Installation

This is the single most important architectural fact in the plan, and it drives the scope:

> **Android Chrome only shows an installed PWA in the system share sheet.** An uninstalled PWA — even with a valid `share_target` manifest entry — does not appear when the user taps the system "Share" button.

Consequence: the icon/screenshot/manifest-polish work that the feasibility study treated as *optional polish* is actually a **hard prerequisite** for the feature to function at all on Android. Without installable icons the PWA cannot be installed, and without installation `share_target` is inert.

This is why the plan deliberately does *not* defer the icon work: it is not polish, it is the foundation of the feature.

---

## 4. Architecture Decision: GET-based share_target, no backend changes

### AD-1: `method: GET`, not `POST`

**Decision:** Configure `share_target` with `method: "GET"` and params `{ url, title }`.

**Rationale:**
- Bookmarks are URL + title only. No files, no selected-text richness worth the complexity.
- GET makes the share action a plain navigation to `/share?url=...&title=...`, which the Vue router handles like any other route. No SW `fetch` interception, no form parsing, no `multipart/form-data`.
- The POST path exists for image sharing (`enctype: multipart/form-data`) — irrelevant here.

**Trade-off:** the shared URL and title appear in the URL bar / browser history. Acceptable for a personal bookmark manager; the data is the user's own and is not sensitive.

### AD-2: Reuse `POST /api/bookmarks` — zero backend changes

**Decision:** The `/share` route reuses the existing `useBookmarkStore().createBookmark()` (`frontend/src/stores/bookmark.ts:130`) and the existing `BookmarkDialog.vue` (`frontend/src/components/bookmark/BookmarkDialog.vue`).

**Rationale:**
- A shared save is, server-side, identical to any other bookmark create. `AuthorizationService` enforces collection access on the call regardless of origin.
- Matches the extension's approach ([browser-extension.md §5.4](browser-extension.md)): "No New API Endpoints."

### AD-3: Pending-share persists across login via `sessionStorage`

**Decision:** If the share arrives while unauthenticated, stash the params in `sessionStorage` under `linkweave:pending-share`, redirect to `/login`, and restore the share after successful auth.

**Rationale:**
- The share intent is a one-shot, in-flight action — `sessionStorage` (not `localStorage`) correctly scopes it to the tab and clears on close.
- Survives the OIDC redirect round-trip (Google login navigates away and back) better than an in-memory Pinia field would.
- Aligns with C-009/C-010: offline/standalone auth trust is per-browser; this is consistent.

---

## 5. How Web Share Target Works (end-to-end)

```
1. User browses any page in mobile Chrome (Android), taps system "Share"
        │
        ▼
2. OS share sheet renders. LinkWeave appears IF the PWA is installed.
   (Installation requires: manifest + 192/512/maskable icons + SW + HTTPS — all satisfied by §6.)
        │
        ▼
3. User taps "LinkWeave" → Chrome navigates the PWA to the manifest's
   share_target.action with query params:  /share?url=<encoded>&title=<encoded>
        │
        ▼
4. Vue router matches /share → router.beforeEach runs → initializeSession()
   → fetchCurrentUser() either resolves (authenticated) or rejects (not).
        │
        ├── authenticated ─► ShareTargetView mounts, reads ?url & ?title,
        │                    opens BookmarkDialog pre-filled, user confirms,
        │                    createBookmark() → POST /api/bookmarks.
        │
        └── not authenticated ─► stash {url,title} in sessionStorage
                                 → redirect to /login
                                 → after login, router restores /share with stashed params
```

---

## 6. Component Design

### 6.1 Manifest — `frontend/vite.config.ts`

Add `share_target` and the icon array to the existing `VitePWA({ manifest: {...} })` block (`frontend/vite.config.ts:63-74`):

```typescript
manifest: {
  name: 'LinkWeave - Bookmark Manager',
  short_name: 'LinkWeave',
  description: 'Self-hosted bookmark manager',
  theme_color: '#2563eb',
  background_color: '#ffffff',
  display: 'standalone',
  icons: [
    { src: '/icons/linkweave-192.png', sizes: '192x192', type: 'image/png' },
    { src: '/icons/linkweave-512.png', sizes: '512x512', type: 'image/png' },
    { src: '/icons/linkweave-512-maskable.png', sizes: '512x512',
      type: 'image/png', purpose: 'maskable' },
  ],
  screenshots: [
    { src: '/screenshots/desktop-wide.png', sizes: '1280x800',
      type: 'image/png', form_factor: 'wide' },
    { src: '/screenshots/mobile-narrow.png', sizes: '390x844',
      type: 'image/png', form_factor: 'narrow' },
  ],
  share_target: {
    action: '/share',
    method: 'GET',
    params: {
      title: 'title',
      url: 'url',
    },
  },
},
```

Notes:
- `params.title` / `params.url` map the Web Share `title`/`url` fields onto the query keys the `/share` route will read. We name them `title`/`url` for readability.
- Some share sources send `text` instead of `url`. If that becomes common, add `text: 'text'` and have `ShareTargetView` fall back to `text` when `url` is absent. Initial cut: `url` + `title` only.

### 6.2 Static assets — `frontend/public/icons/` and `frontend/public/screenshots/`

| Asset | Purpose |
|---|---|
| `icons/linkweave-192.png` | Standard home-screen icon (Android, Windows) |
| `icons/linkweave-512.png` | High-res home-screen icon |
| `icons/linkweave-512-maskable.png` | Android adaptive icon — without `purpose: "maskable"`, Android crops the icon to a circle, cutting off the logo |
| `screenshots/desktop-wide.png` | Richer install dialog (desktop Chrome) |
| `screenshots/mobile-narrow.png` | Richer install dialog (Android Chrome) |

Generate from the existing `linkweave-favicon.png` source. The maskable variant needs safe-zone padding (logo within the inner 80% — see [maskable.app](https://maskable.app/editor)).

### 6.3 New route — `frontend/src/router/index.ts`

Add a `/share` route. It must be **protected** (not `meta.public`) so the existing guard runs auth:

```typescript
{
  path: '/share',
  name: 'share-target',
  component: () => import('@/views/ShareTargetView.vue'),
}
```

The home-auto-redirect block (`router/index.ts:71-76`) must be extended to skip when `to.name === 'share-target'`, otherwise an authenticated user hitting `/share` gets bounced to their default collection before `ShareTargetView` mounts:

```typescript
if (auth.isAuthenticated && to.name === 'home') {   // ← 'home' only, not /share
  ...
}
```

### 6.4 Pending-share hand-off — `frontend/src/router/index.ts` (guard)

In `router.beforeEach`, when the target is `/share` and the user is not authenticated, stash and redirect:

```typescript
if (!auth.isAuthenticated && to.name === 'share-target') {
  sessionStorage.setItem('linkweave:pending-share', JSON.stringify({
    url: to.query.url, title: to.query.title,
  }))
  return { name: 'login' }
}
```

After successful login (`LoginView.vue` or the OIDC callback), check for a pending share and redirect there instead of the default home:

```typescript
const pending = sessionStorage.getItem('linkweave:pending-share')
if (pending) {
  sessionStorage.removeItem('linkweave:pending-share')
  const { url, title } = JSON.parse(pending)
  return { name: 'share-target', query: { url, title } }
}
```

### 6.5 New view — `frontend/src/views/ShareTargetView.vue`

Responsibilities:
- Read `route.query.url` and `route.query.title`.
- Validate `url` is a non-empty `http(s)` URL (reuse the existing zod schema from the bookmark form). If invalid, show a small "couldn't read the shared link" message with a manual-paste fallback.
- Ensure the user's default collection is loaded (`useCollectionStore`).
- Open `<BookmarkDialog>` in create mode, pre-filled with `url`/`title` and the default collection/folder. The dialog's current `Props` (`BookmarkDialog.vue:46-54`) has `bookmark` / `collectionId` / `preselectedFolderId` / `open` but no way to pre-fill `url`/`title` — its `initialValues` block (`BookmarkDialog.vue:70-77`) hardcodes both to `''` in create mode. A small `prefill` prop is required (see §9, Modify row 3). This is the only potentially non-trivial frontend change.
- On save success: navigate to the bookmark's collection (`name: 'collection'`), so the user lands somewhere useful rather than a bare `/share` route.
- On cancel: navigate to home (`name: 'home'`).

### 6.6 Backend

**No changes.** `POST /api/bookmarks` (with `BookmarkSaveJson`) already does exactly what the share save needs, and `AuthorizationService` enforces collection access on the call. CORS does not apply — the share save is same-origin (the PWA's own fetch).

---

## 7. Data Flow

### 7.1 Authenticated share

```
Android Chrome ──share──► PWA navigates to /share?url=U&title=T
                             │
                             ▼
   router.beforeEach → initializeSession → fetchCurrentUser() 200
                             │
                             ▼
   ShareTargetView mounts → BookmarkDialog(prefill={url:U,title:T})
                             │
                             ▼
   user picks collection/folder/tags, clicks Save
                             │
                             ▼
   useBookmarkStore.createBookmark(BookmarkSaveJson)
                             │
                             ▼
   POST /api/bookmarks  ──►  200 BookmarkJson
                             │
                             ▼
   router.push({ name:'collection', params:{id} })
```

### 7.2 Unauthenticated share (login hand-off)

```
/share?url=U&title=T  ──►  fetchCurrentUser() 401
                             │
                             ▼
   sessionStorage['linkweave:pending-share'] = {U,T}
   redirect → /login
                             │
             (user logs in via form OR Google OIDC round-trip)
                             │
                             ▼
   post-login hook reads sessionStorage → redirect → /share?url=U&title=T
                             │
                             ▼
   (flow continues as 7.1)
```

---

## 8. Platform Support & Limitations

| Platform | Share Target works? | Notes |
|---|---|---|
| **Android Chrome** | ✅ Yes (primary target) | Requires PWA installed. This is the entire justification for the feature. |
| **Desktop Chrome/Edge** | ⚠️ Technically yes, low value | Desktop users have the browser extension (FR-043) which is strictly better (reads the active tab). No regression if unused. |
| **iOS Safari** | ❌ No | iOS does not implement Web Share Target. iOS users continue to copy/paste manually — **no worse than today.** Document this; do not attempt a workaround. |
| **Desktop Firefox/Safari** | ❌ No | Irrelevant — desktop is owned by Tauri + the extension. |

The honest framing for users: *"Save-from-share works on Android. On iOS, open LinkWeave and paste the URL."*

iOS storage limits (~50 MB SW eviction) flagged in [pwa-feasibility.md §8](pwa-feasibility.md) still apply but are immaterial to *this* feature — share-to-save is an online write operation, not an offline-read operation.

---

## 9. Files to Create / Modify

### Create

| # | File | Description |
|---|---|---|
| 1 | `frontend/public/icons/linkweave-192.png` | Standard icon |
| 2 | `frontend/public/icons/linkweave-512.png` | High-res icon |
| 3 | `frontend/public/icons/linkweave-512-maskable.png` | Android adaptive icon (inner 80% safe zone) |
| 4 | `frontend/public/screenshots/desktop-wide.png` | 1280×800 install-dialog screenshot |
| 5 | `frontend/public/screenshots/mobile-narrow.png` | 390×844 install-dialog screenshot |
| 6 | `frontend/src/views/ShareTargetView.vue` | Reads `?url`/`?title`, opens pre-filled `BookmarkDialog`, handles save/cancel routing |
| 7 | `frontend/src/views/ShareTargetView.spec.ts` | Unit tests: param parsing, invalid-URL fallback, pre-fill wiring, success/redirect |
| 8 | `e2e/share-target.spec.ts` | E2E: navigate to `/share?url=...&title=...` directly (Playwright can't trigger the real OS sheet), assert pre-filled dialog + save round-trip |
| 9 | `docs/use_cases/UC-094-save-bookmark-from-mobile-share-sheet.md` | Formal use-case spec (actors, preconditions, main/alt flows) |

### Modify

| # | File | Change |
|---|---|---|
| 1 | `frontend/vite.config.ts` | Add `share_target`, real `icons[]`, `screenshots[]` to the `VitePWA` manifest (currently at lines 63-74) |
| 2 | `frontend/src/router/index.ts` | Add `/share` route; extend `beforeEach` to (a) stash pending share when unauthenticated, (b) restore it post-login, (c) ensure the home-auto-redirect does not swallow `/share` |
| 3 | `frontend/src/components/bookmark/BookmarkDialog.vue` | Add a `prefill?: { url?: string; title?: string }` prop to the `Props` interface (`:46-54`); in the `useForm` `initialValues` block (`:70-77`) read `props.prefill?.title ?? ''` and `props.prefill?.url ?? ''` for create mode (leave edit mode — `props.bookmark` path — untouched). Existing create-mode logic otherwise unchanged. |
| 4 | `frontend/src/views/LoginView.vue` (or post-login hook) | After successful login, check `sessionStorage['linkweave:pending-share']` and redirect there |
| 5 | `frontend/src/i18n/locales/en.json`, `de.json` | Add `share.*` keys (invalid-url message, fallback-paste prompt, success-toast copy) |
| 6 | `docs/requirements.md` | FR-092 row added; FR-091 duplicate resolved (PWA Feasibility Study renumbered to FR-093) — see §11 |

---

## 10. Build Sequence (Checklist)

### Phase 1 — Installable foundation (prerequisite for share_target, see §3)
- [ ] Generate `linkweave-192.png`, `linkweave-512.png`, `linkweave-512-maskable.png` from the existing favicon source; respect the maskable 80% safe zone
- [ ] Capture `desktop-wide.png` (1280×800) and `mobile-narrow.png` (390×844) from a staging build
- [ ] Update the `VitePWA` manifest `icons[]` and add `screenshots[]` in `vite.config.ts`
- [ ] `pnpm run build` → inspect `dist/index.html` to confirm `<link rel="manifest">` is injected and the manifest JSON contains the icons/screenshots
- [ ] Run Lighthouse → PWA category against the production build; fix flagged items
- [ ] **Verify:** install the PWA on Android Chrome (or Desktop Chrome with mobile emulation), confirm install dialog shows correct icons

### Phase 2 — Share target plumbing
- [ ] Add `share_target` (GET, `/share`, `title`/`url` params) to the manifest in `vite.config.ts`
- [ ] Add the `/share` route to `router/index.ts` (lazy import of `ShareTargetView.vue`)
- [ ] Extend `router.beforeEach`: skip home-auto-redirect for `share-target`; stash pending share on auth failure; restore after login
- [ ] Add `prefill` prop to `BookmarkDialog.vue` (confirmed needed — current `Props` interface at `:46-54` has no url/title prefill)
- [ ] Create `ShareTargetView.vue` (param parsing, zod URL validation, dialog wiring, save/redirect)
- [ ] Add `share.*` i18n keys (en + de)
- [ ] **Verify:** `pnpm run check` (type-check + lint + deadcode) passes

### Phase 3 — Tests
- [ ] `ShareTargetView.spec.ts` — valid URL pre-fills dialog; invalid URL shows fallback; successful save routes to collection; cancel routes home
- [ ] Router guard unit test — unauthenticated `/share` stashes to sessionStorage and redirects to `/login`; post-login restores the share
- [ ] `e2e/share-target.spec.ts` — direct navigation to `/share?url=...&title=...` (authenticated) opens pre-filled dialog and completes a save round-trip
- [ ] **Verify:** `pnpm run test` and `pnpm exec playwright test --project=chromium` pass

### Phase 4 — Manual platform smoke
- [ ] Android Chrome: install PWA → from another app/page trigger Share → LinkWeave appears → save succeeds
- [ ] Android Chrome: trigger share while logged out → completes login → save succeeds
- [ ] iOS Safari: confirm share sheet does **not** show LinkWeave (documented limitation), and that direct use is unaffected
- [ ] Desktop Chrome: confirm no regression; extension remains the preferred desktop path

---

## 11. Requirements Catalog Changes

Applied to `docs/requirements.md`:

> **FR-092 — Save Bookmark from Mobile Share Sheet.** As a User on Android, I want to send the page I'm reading to LinkWeave via the OS share sheet so that I can save it as a bookmark without copy-pasting the URL. The PWA registers as a Web Share Target (`share_target` in the Web App Manifest, `method: GET`, params `url`/`title`); tapping LinkWeave in the system share sheet opens the app at `/share?url=…&title=…` with the bookmark dialog pre-filled. The save reuses the existing `POST /api/bookmarks` endpoint — no new backend surface. Requires the PWA to be installed (real 192/512/maskable icons, screenshots, valid manifest, service worker). iOS is not supported (no Web Share Target implementation); iOS users are no worse off than today.

> **UC-094 — Save Bookmark from Mobile Share Sheet.** See `docs/use_cases/UC-094-save-bookmark-from-mobile-share-sheet.md`.

**Data bug fixed in the same edit:** `FR-091` was previously assigned twice in `requirements.md` (line 89: "Collapsible Properties Section in Edit Bookmark Dialog", line 111: "PWA Feasibility Study"). The Collapsible Properties entry is the canonical owner of FR-091 — it has a matching use-case file, `UC-091-collapsible-properties-section.md`. The duplicate **PWA Feasibility Study row has been renumbered to FR-093**, and the `[pwa-feasibility.md](pwa-feasibility.md)` header updated to match (`**Requirement:** FR-093`). FR-092 (this feature) is inserted alongside it, so the catalog tail now reads FR-090, FR-092, FR-093 with no collision.

---

## 12. Security Considerations

| Concern | Mitigation |
|---|---|
| **Crafted share URL** | `ShareTargetView` validates `url` with the existing zod URL schema before pre-filling. Invalid input shows a manual-paste fallback, never a blind create. |
| **Open redirect via `url` param** | The `url` param is treated as *bookmark data*, not as a navigation target. The app never `window.location`'s to it. |
| **CSRF on the save** | Same-origin POST to `/api/bookmarks` — unchanged from the regular web-app flow. The existing Quarkus form-auth CSRF protections apply. |
| **Auth bypass via `/share`** | `/share` is a protected route. The only special-casing is *preserving intent across login*, not *skipping login*. An unauthenticated user must still complete a real login before any save. |
| **sessionStorage leak** | `linkweave:pending-share` holds only a URL + title the user themselves shared. Cleared on first read and on tab close. No credentials, no tokens. |

---

## 13. Out of Scope (do not pull forward)

- Push notifications (defer — see [pwa-feasibility.md §6](pwa-feasibility.md))
- Offline write queue / sync (defer — see [offlinemode.md](offlinemode.md), FR-056)
- Desktop install story (owned by [desktop-app.md](desktop-app.md))
- iOS share-sheet parity (technically infeasible)
- Sharing selected text / images via `text` payload or `multipart/form-data` POST
- Duplicate-URL detection on share (could be a nice follow-up; UC-053 "warn-on-duplicate-bookmark" exists and could be wired into the dialog)
