# Plan: Screenshot Previews

## Context

UC-054 (View Bookmark Screenshot Previews) adds a visual dimension to bookmark management. Users can enable screenshot previews per collection and switch to a "tiles" layout that shows a screenshot cover image for each bookmarked page.

Screenshots are captured by a Playwright-based microservice running as a Docker sidecar. The Quarkus API communicates with the sidecar over HTTP. Screenshot capture is **entirely asynchronous** — bookmark CRUD is never delayed. The feature degrades gracefully when the sidecar is unavailable (placeholder cards are shown).

## Use Cases

- **UC-054** — View Bookmark Screenshot Previews (user-facing)
- **UC-055** — Screenshot Cache Cleanup Job (operator-facing)

## Requirements

- **FR-065** — View Bookmark Screenshot Previews
- **FR-066** — Screenshot Cache Cleanup Job
- **NFR-015** — Screenshot Proxy SSRF Hardening
- **C-012** — Screenshot Cache Storage (filesystem, not DB)
- **C-013** — Screenshot Sidecar Service (Docker isolation)

## Goals

- Toggleable per collection (off by default, owner-only toggle).
- New "tiles" layout option (visible only when screenshots are enabled for the current collection).
- Screenshots captured asynchronously on bookmark create / URL update.
- File-based cache with TTL, negative cache entries, and size-based eviction.
- Same SSRF protection as the favicon proxy.
- Completely separate from the default code path — no impact on existing layouts when disabled.
- Backfill captures for existing bookmarks when a collection's `screenshotEnabled` toggle flips from `false` → `true`.

## Non-goals

- Full-page or scrollable screenshots (viewport-only capture at 1280×800).
- User-configurable viewport sizes or capture options.
- Screenshots behind authentication (only public pages).
- OCR or content extraction from screenshots.
- Rate limiting on the screenshot sidecar (handled by the sidecar's internal cache).
- Synchronous capture on display. The GET endpoint is **cache-read-only**; it never triggers a sidecar call. Captures are exclusively driven by async events (create / update / toggle-enable backfill).

---

## Architecture Decision: Playwright Sidecar

```
┌─────────────────────┐       HTTP        ┌──────────────────────────┐
│  LinkWeave API      │ ───────────────── │  Playwright Screenshot   │
│  (Quarkus)          │  POST /screenshot │  Service (Node.js)       │
│                     │  {url, width,     │  :3000                   │
│  ScreenshotFetcher  │   format:"jpeg"}  │                          │
│  ScreenshotCache    │ ◀───────────────  │  Returns JPEG bytes      │
│  ScreenshotResource │                   │  (with internal cache)   │
└─────────────────────┘                   └──────────────────────────┘
```

**Why a sidecar?**
- Running Chromium inside the Quarkus JVM is impractical (native deps, crash risk, memory pressure).
- The sidecar is independently scalable, replaceable, and can be disabled without affecting the main app.
- Pre-built Docker images exist (e.g. `ghcr.io/vlazic/playwright-screenshot-api`) supporting AMD64 + ARM64.
- The sidecar has its own internal cache, reducing redundant captures.

**Why not a SaaS?** — Self-hosted deployment (C-004), privacy, no per-screenshot costs.

---

## Data Model

### Modified Entity: `Collection`

Add one field to the existing `Collection` entity:

| field               | type      | notes                                    |
|---------------------|-----------|------------------------------------------|
| `screenshotEnabled` | `boolean` | NOT NULL, default `false`                |

No new entity is needed. Screenshot state is purely cache-based (filesystem).

### Flyway Migration

`api/src/main/resources/db/migration/V15__Add_screenshot_enabled.sql`

```sql
ALTER TABLE Collection ADD COLUMN screenshot_enabled BOOLEAN NOT NULL DEFAULT 0;
```

---

## Backend

### Package: `org.linkweave.api.screenshot`

```
api/src/main/java/org/linkweave/api/screenshot/
├── ScreenshotCacheService.java      — File-based cache (mirrors FaviconCacheService)
├── ScreenshotFetcherService.java    — HTTP client to Playwright sidecar
├── ScreenshotService.java           — Cache-first orchestration
├── ScreenshotResource.java          — REST endpoint for serving screenshots
├── ScreenshotTriggerService.java    — Async observer for bookmark events
├── ScreenshotCacheCleanupJob.java   — Scheduled cache eviction
└── BookmarkEvents.java             — CDI async event records
```

### ScreenshotCacheService

**Pattern:** Exact mirror of `FaviconCacheService` (see `api/.../collection/favicon/FaviconCacheService.java`).

- `.bin` + `.meta` sidecar files, keyed by `SHA-256(canonicalOrigin + normalizedPath)`.
- Success TTL: 30 days. Negative TTL: 12 hours.
- Atomic writes via temp file + `Files.move`.
- Cache dir: configurable, default `developer-local-settings/screenshot-cache/`.
- Negative entries count toward the cleanup size budget (same as success entries — they consume inodes and tiny amounts of disk, and including them keeps accounting simple).
- The cache is **URL-keyed, not bookmark-keyed.** Multiple bookmarks pointing at the same canonical URL share a single cache entry. Consequently the cache itself never has its entries "invalidated on URL change" — only the cleanup job removes entries. See *URL-update semantics* below.

Config properties:
```properties
chainlink.screenshot.cache-dir=developer-local-settings/screenshot-cache
chainlink.screenshot.success-ttl=30D
chainlink.screenshot.negative-ttl=12H
```

### ScreenshotFetcherService

- HTTP client to the Playwright sidecar.
- Uses Quarkus `RestClient` or plain `HttpURLConnection` (like `FaviconFetcherService`).
- Sends `POST /screenshot` with body: `{url, width: 1280, height: 800, format: "jpeg", quality: 60, scale: 1}`.
- **SSRF protection:** Reuses `FaviconFetcherService.isPublicHost()` and `isAllowedScheme()`.
- Timeout: 15 seconds.
- Returns `Optional<FetchedScreenshot>` (bytes + contentType).

Config properties:
```properties
chainlink.screenshot.service-url=http://localhost:3000
chainlink.screenshot.timeout=15S
chainlink.screenshot.enabled=true
```

### ScreenshotService

**Pattern:** Differs from `FaviconService` — read-only on the request path. Capture is always driven by the async trigger service, never by a user-facing GET. This avoids per-tile 15s blocking on first view and prevents thundering-herd captures when a user opens a freshly-enabled tiles layout.

```java
Optional<CachedScreenshot> getScreenshot(ID<Bookmark> bookmarkId)   // read-only
void triggerCapture(ID<Bookmark> bookmarkId, URL url)                // called by trigger service
```

`getScreenshot` (called from the REST endpoint):
1. Load bookmark → compute cache key from canonical URL.
2. Return `ScreenshotCacheService.get(key)` (which may be a success entry, a negative entry treated as empty, or a miss treated as empty).
3. **Never** calls the fetcher.

`triggerCapture` (called from the async observer):
1. Skip if feature disabled globally or for the collection.
2. Skip if a success entry already exists and is fresh (TTL not expired).
3. Skip if a negative entry exists and is fresh (don't retry failures within the negative TTL).
4. Dedup: maintain an in-process `ConcurrentHashMap<CacheKey, CompletableFuture<?>>` of in-flight captures. If a capture for this key is already running, no-op. (Mitigates duplicate bookmarks of the same URL created in quick succession; the sidecar's own cache is a second layer of defense, not the primary one.)
5. Call `ScreenshotFetcherService.fetch(url)` → write success or negative entry → remove from in-flight map.

### ScreenshotResource

**Pattern:** Mirrors `FaviconResource`.

| method | path | auth | returns |
|--------|------|------|---------|
| GET | `/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot` | `requireCollectionAccess` | 200 (JPEG bytes) or 204 (no screenshot) |

```java
@GET
public Response getScreenshot(
    @PathParam("collectionId") ID<Collection> collectionId,
    @PathParam("bookmarkId") ID<Bookmark> bookmarkId
) {
    authorizationService.requireCollectionAccess(collectionId);
    return screenshotService.getScreenshot(bookmarkId)
        .map(c -> Response.ok(c.bytes())
            .header("Content-Type", c.contentType())
            .header("Cache-Control", "private, max-age=86400")
            .build())
        .orElseGet(() -> Response.noContent().build());
}
```

### BookmarkEvents (CDI async events)

```java
public record BookmarkCreatedEvent(ID<Bookmark> bookmarkId, URL url, ID<Collection> collectionId) {}
public record BookmarkUpdatedEvent(ID<Bookmark> bookmarkId, URL oldUrl, URL newUrl, ID<Collection> collectionId) {}
public record CollectionScreenshotsEnabledEvent(ID<Collection> collectionId) {}
```

Firing rules:
- `BookmarkCreatedEvent` — from `BookmarkService.createBookmark()`.
- `BookmarkUpdatedEvent` — from `BookmarkService.updateBookmark()`, only when the URL actually changes.
- `CollectionScreenshotsEnabledEvent` — from `CollectionService.updateCollection()` when `screenshotEnabled` transitions `false → true`. Drives the backfill (see below).

### URL-update semantics

When a bookmark's URL changes, the trigger service:
1. Fires a capture for the **new** URL (if not already cached).
2. Does **not** delete the cache entry for the old URL. Other bookmarks (in this or other collections) may still point at it, and the size-based cleanup job will reclaim it once it's stale.

Rationale: a per-URL cache trivially shared across bookmarks is much simpler than reference-counting; the only cost is slightly delayed disk reclamation.

### ScreenshotTriggerService

`@ObservesAsync` for `BookmarkCreatedEvent` and `BookmarkUpdatedEvent`.

```java
void onBookmarkCreated(@ObservesAsync BookmarkCreatedEvent event) {
    if (!screenshotEnabledGlobally) return;
    Collection collection = collectionRepo.getById(event.collectionId());
    if (!collection.isScreenshotEnabled()) return;
    screenshotService.triggerCapture(event.bookmarkId(), event.url());
}
```

On URL update: trigger new capture for the new URL (old entry is left for the cleanup job — see *URL-update semantics*).

### Backfill on toggle

`ScreenshotTriggerService` also observes `CollectionScreenshotsEnabledEvent`:

```java
void onScreenshotsEnabled(@ObservesAsync CollectionScreenshotsEnabledEvent event) {
    if (!screenshotEnabledGlobally) return;
    bookmarkRepo.streamByCollection(event.collectionId())
        .forEach(b -> screenshotService.triggerCapture(b.getId(), b.getUrl()));
}
```

- Streamed (not loaded all at once) to avoid memory blow-up on large collections.
- The dedup map and existing-cache-entry checks inside `triggerCapture` prevent re-capturing URLs that are already cached from another collection.
- No backfill is triggered on `true → false` (data stays cached until cleanup; flipping back on then re-uses it).
- No rate limiting beyond the dedup map; if this proves problematic on huge collections, add a token bucket in a follow-up — call out as known limitation, not a blocker.

### ScreenshotCacheCleanupJob

**Pattern:** Mirrors `FaviconCacheCleanupJob`.

Config properties:
```properties
chainlink.screenshot.cache-cleanup.max-size=200MB
chainlink.screenshot.cache-cleanup.min-bookmark-age=28D
chainlink.screenshot.cache-cleanup.cron=0 0 3 ? * SUN
chainlink.screenshot.cache-cleanup.enabled=true
```

### Modified Backend Files

| file | change |
|------|--------|
| `Collection.java` | Add `screenshotEnabled` boolean field |
| `CollectionUpdateJson.java` | Add `screenshotEnabled` field |
| `CollectionInfoJson.java` | Add `screenshotEnabled` field |
| `CollectionInfoMapperService.java` | Map `screenshotEnabled` |
| `CollectionService.updateCollection()` | Persist `screenshotEnabled`; fire `CollectionScreenshotsEnabledEvent` on `false → true` transition. Owner-only enforcement is already handled by the existing collection update authorization in the Resource layer — no new check needed, but assert it in tests. |
| `BookmarkService.java` | Fire `BookmarkCreatedEvent` / `BookmarkUpdatedEvent` on create/update |
| `application.properties` | Add all screenshot config properties |

---

## Frontend

### New Layout Type

`stores/ui.ts`:
```typescript
export type BookmarkLayout = 'list' | 'grid' | 'grouped' | 'tiles'
```

### New Components

#### `BookmarkTile.vue`
- Fixed aspect-ratio card with screenshot as cover image.
- Screenshot loaded via `<img src="/api/collections/{cid}/bookmarks/{bid}/screenshot" loading="lazy">`.
- Falls back to a styled placeholder (gradient + favicon + title) on error or 204.
- Same context menu (edit, move, delete) as `BookmarkCard`.
- Supports drag-and-drop (same pattern as existing cards).

#### `BookmarkTileLayout.vue`
- Responsive CSS grid: `grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4`.
- Renders `BookmarkTile` for each bookmark.
- Supports all standard events: `@edit`, `@delete`, `@move`.

### Modified Frontend Files

| file | change |
|------|--------|
| `stores/ui.ts` | Add `'tiles'` to `BookmarkLayout` type |
| `BookmarkList.vue` | Add `v-else-if="ui.bookmarkLayout === 'tiles'"` branch rendering `BookmarkTileLayout` |
| `SettingsDialog.vue` | Add `tiles` option to layout picker (icon: `LayoutDashboard`); only visible when `collectionStore.collectionInfo?.screenshotEnabled` is true |
| `CollectionView.vue` | Add `tiles` to container class width mapping (`max-w-7xl`) |
| `EditCollectionDialog.vue` or `CollectionManageView.vue` | Add toggle switch for "Screenshot Previews" bound to `screenshotEnabled` |
| `i18n/locales/{en,de}.json` | Add screenshot and tiles translation keys |

### Layout Availability Logic

Implemented as a Vue `watch` on `collectionStore.collectionInfo?.screenshotEnabled` (so it fires both on collection switch *and* when the current collection's flag flips server-side, e.g. another tab disables it):

```ts
watch(
  () => collectionStore.collectionInfo?.screenshotEnabled,
  (enabled) => {
    if (!enabled && ui.bookmarkLayout === 'tiles') {
      ui.setBookmarkLayout('grid')
    }
  },
  { immediate: true },
)
```

Mobile tile sizing: the grid uses `min-w-[140px]` per tile via `grid-cols-[repeat(auto-fill,minmax(140px,1fr))]` rather than fixed `grid-cols-2`, so 1280×800 source images don't render at a postage-stamp size on narrow viewports.

---

## Data Flow

### Capture Flow (Async, Background)

```
BookmarkService.createBookmark()
  → bookmarkRepo.persist()
  → fireAsync(BookmarkCreatedEvent)
  → return BookmarkJson immediately

ScreenshotTriggerService.onBookmarkCreated() [async]
  → check: global enabled? collection.screenshotEnabled?
  → ScreenshotService.triggerCapture()
    → ScreenshotFetcherService.fetch(url)
      → SSRF check (isPublicHost, isAllowedScheme)
      → POST http://screenshot-service:3000/screenshot
      ← JPEG bytes
    → ScreenshotCacheService.putSuccess(key, bytes, "image/jpeg")
```

### Display Flow (Cache-Read-Only)

```
BookmarkTile renders
  → <img src="/api/collections/{cid}/bookmarks/{bid}/screenshot" loading="lazy">
  → ScreenshotResource.getScreenshot()
    → authorizationService.requireCollectionAccess()
    → ScreenshotService.getScreenshot(bookmarkId)
      → ScreenshotCacheService.get(key)
        → SUCCESS HIT → return CachedScreenshot
        → NEGATIVE HIT or MISS → return Optional.empty()
    → 200 OK (bytes) or 204 No Content
```

The GET path never calls the sidecar. If the cache is cold for a bookmark, the client receives 204 and shows a placeholder; the async trigger (create/update/backfill) is responsible for populating the cache. Clients may refresh the image after a short delay (~2s) following a bookmark create to pick up a freshly-captured screenshot; otherwise the placeholder persists until the next render.

### Cache Cleanup Flow (Scheduled)

```
@Scheduled(cron)
  → compute total cache directory size
  → if over budget → iterate oldest bookmarks, delete cache entries
  → log summary
```

---

## Implementation Phases

### Phase 1: Backend — Entity & Screenshot Infrastructure

- [ ] Create Flyway migration `V15__Add_screenshot_enabled.sql`
- [ ] Modify `Collection` entity + DTOs + Service for `screenshotEnabled`
- [ ] Create `ScreenshotCacheService` (adapt from `FaviconCacheService`)
- [ ] Create `ScreenshotFetcherService` with HTTP client + SSRF guards
- [ ] Create `ScreenshotService` (cache-first orchestration)
- [ ] Create `ScreenshotResource` (REST endpoint)
- [ ] Create `BookmarkEvents` records
- [ ] Modify `BookmarkService` to fire async events
- [ ] Create `ScreenshotTriggerService` (async observer)
- [ ] Create `ScreenshotCacheCleanupJob`
- [ ] Add config properties to `application.properties`
- [ ] Write unit tests for all new services
- [ ] Run `./mvnw verify` — all tests pass

### Phase 2: Frontend — Tile Layout & Screenshot Display

- [ ] Add `'tiles'` to `BookmarkLayout` type in `stores/ui.ts`
- [ ] Create `BookmarkTile.vue` component
- [ ] Create `BookmarkTileLayout.vue` component
- [ ] Modify `BookmarkList.vue` to support tiles layout
- [ ] Modify `SettingsDialog.vue` to add tiles option
- [ ] Modify collection settings to add screenshot toggle
- [ ] Add layout fallback logic when switching collections
- [ ] Add i18n translations
- [ ] Run `npm run generate-api` after backend changes
- [ ] Run `npm run type-check` — no errors

### Phase 3: Docker & Deployment

- [ ] Add `screenshot-service` container to Docker Compose with:
  - `mem_limit: 1g`, `cpus: 1.0` (Chromium is memory-hungry; cap it so a runaway can't OOM the host).
  - `healthcheck` hitting the sidecar's own health endpoint; `restart: unless-stopped`.
  - Exposed only on the internal compose network — not published to the host.
- [ ] Verify ARM64 + AMD64 image availability for the chosen image tag (pin a specific digest, not `:latest`).
- [ ] Configure network so Quarkus API can reach sidecar by service name (`http://screenshot-service:3000`).
- [ ] Document local dev story: the sidecar is **optional** in `pnpm run dev` / `mvnw quarkus:dev`. With `chainlink.screenshot.enabled=false` (or sidecar simply unreachable), `ScreenshotTriggerService` no-ops cleanly and all tile renders return 204 → placeholders. Devs who want to test the full flow start the sidecar via `docker compose up screenshot-service`.

---

## Configuration Reference

```properties
# Screenshot feature master switch
chainlink.screenshot.enabled=true

# Playwright sidecar URL
chainlink.screenshot.service-url=http://localhost:3000

# Fetch timeout
chainlink.screenshot.timeout=15S

# Cache directory
chainlink.screenshot.cache-dir=developer-local-settings/screenshot-cache

# Cache TTL
chainlink.screenshot.success-ttl=30D
chainlink.screenshot.negative-ttl=12H

# Cleanup job
chainlink.screenshot.cache-cleanup.enabled=true
chainlink.screenshot.cache-cleanup.max-size=200MB
chainlink.screenshot.cache-cleanup.min-bookmark-age=28D
chainlink.screenshot.cache-cleanup.cron=0 0 3 ? * SUN

# Disable in tests
%test.chainlink.screenshot.enabled=false
%test.chainlink.screenshot.cache-cleanup.enabled=false
```

---

## Tests

### Backend

| test class | scope |
|------------|-------|
| `ScreenshotCacheServiceTest` | Cache read/write/eviction, TTL expiry, negative entries |
| `ScreenshotFetcherServiceTest` | Mock HTTP client, SSRF guards, timeout |
| `ScreenshotServiceTest` | Cache-hit returns bytes; cache-miss returns empty (no sidecar call); negative entry returns empty; `triggerCapture` dedup via in-flight map; `triggerCapture` skips when fresh entry exists; `triggerCapture` skips when fresh negative entry exists |
| `ScreenshotResourceTest` | Auth checks, 200/204 responses, GET never triggers a capture (verify fetcher mock is untouched) |
| `ScreenshotTriggerServiceTest` | Async create/update event handling, skip-when-disabled (global + per-collection), URL-update fires capture for new URL only, backfill on `CollectionScreenshotsEnabledEvent` streams all bookmarks and dedups against pre-existing cache entries |
| `CollectionServiceTest` | Owner-only enforcement of `screenshotEnabled` toggle; `CollectionScreenshotsEnabledEvent` fired only on `false → true` transition (not on no-op updates) |
| `ScreenshotCacheCleanupJobTest` | Size-based eviction, skip when under budget |

### Frontend

- Unit: `BookmarkTile.vue` — renders screenshot, falls back to placeholder
- Unit: `ui.ts` — tiles layout type, fallback when screenshots disabled
- E2E: Enable screenshots on collection → switch to tiles → verify screenshot placeholder → create bookmark → verify tile appears

---

## Critical Files (Quick Index)

### Existing files to modify

- `api/.../collection/Collection.java` — add `screenshotEnabled` field
- `api/.../collection/CollectionUpdateJson.java` — add field
- `api/.../collection/CollectionInfoJson.java` — add field
- `api/.../collection/CollectionInfoMapperService.java` — map field
- `api/.../collection/CollectionService.java` — persist toggle
- `api/.../bookmark/BookmarkService.java` — fire async events
- `api/src/main/resources/application.properties` — config
- `frontend/src/stores/ui.ts` — add tiles layout
- `frontend/src/components/bookmark/BookmarkList.vue` — add tiles branch
- `frontend/src/components/ui/SettingsDialog.vue` — add tiles option

### New files to create

- `api/.../screenshot/ScreenshotCacheService.java`
- `api/.../screenshot/ScreenshotFetcherService.java`
- `api/.../screenshot/ScreenshotService.java`
- `api/.../screenshot/ScreenshotResource.java`
- `api/.../screenshot/ScreenshotTriggerService.java`
- `api/.../screenshot/ScreenshotCacheCleanupJob.java`
- `api/.../screenshot/BookmarkEvents.java`
- `api/src/main/resources/db/migration/V15__Add_screenshot_enabled.sql`
- `frontend/src/components/bookmark/BookmarkTile.vue`
- `frontend/src/components/bookmark/BookmarkTileLayout.vue`

### Reference files (patterns to follow)

- `api/.../collection/favicon/FaviconCacheService.java` — cache pattern
- `api/.../collection/favicon/FaviconFetcherService.java` — SSRF guards, HTTP client
- `api/.../collection/favicon/FaviconService.java` — cache-first orchestration
- `api/.../collection/favicon/FaviconResource.java` — binary resource serving
- `api/.../collection/favicon/FaviconCacheCleanupJob.java` — scheduled cleanup
- `frontend/src/components/bookmark/BookmarkFavicon.vue` — lazy image with fallback
- `frontend/src/components/bookmark/BookmarkCard.vue` — card pattern with context menu
- `frontend/src/components/bookmark/BookmarkGroupedLayout.vue` — grid layout pattern

## Verification

1. `cd api && ./mvnw verify` — migration applies, all tests green.
2. `cd frontend && npm run generate-api && npm run type-check` — no type errors.
3. `cd frontend && npm run dev` — manual verification:
   - Enable screenshots on a collection → tiles option appears in settings.
   - Switch to tiles layout → bookmarks render as tiles with screenshot placeholders.
   - Create a new bookmark → tile appears with screenshot (after async capture).
   - Disable screenshots → tiles option disappears, layout falls back to grid.
4. `npx playwright test --project=chromium` — E2E tests pass.
