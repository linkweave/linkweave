# Live Collection Updates — SSE design

Design document for [UC-104](../use_cases/UC-104-live-collection-updates.md) / FR-100.

**Transport decision: reactive `Multi<T>`, not classic `SseEventSink`.** The project
runs Quarkus REST (`quarkus-rest-jackson`, Quarkus 3.36.2), whose
[guide](https://quarkus.io/guides/rest) documents only the `Multi` form. The classic
JAX-RS SSE API has a history of gaps on this runtime — `@Context` injection of SSE
components ([#39128](https://github.com/quarkusio/quarkus/issues/39128)) and
`SseBroadcaster.broadcast()` throwing on an already-closed sink
([#30681](https://github.com/quarkusio/quarkus/issues/30681)), which is the ordinary
"user closed the tab" case. Whether those are fixed in 3.36.2 was not verified; the
documentation signal alone is enough to prefer `Multi`.

---

## Why this is cheap here

Three business rules that normally cost real work are free with this design:

| Rule | How it is satisfied |
| --- | --- |
| BR-203 — notify only after commit | CDI `@Observes(during = AFTER_SUCCESS)`; the container will not deliver the event unless the transaction commits |
| BR-204 — no replay, no backlog | Mutiny `BroadcastProcessor` [drops items when no subscriber is present](https://smallrye.io/smallrye-mutiny/latest/guides/hot-streams/) and never replays to late subscribers |
| BR-210 — scope is the collection | One processor per collection ID; a subscriber can only ever reach the one it asked for |

## Delivery — both phases shipped

**Phase 1 — A1, deferred screenshot capture completing.** Chosen first because it was
the only asynchronous producer that already existed (`ScreenshotCaptureJobService`,
30s tick) and could be proven in one browser with no second user.

**Phase 2 — the main success scenario.** Collaborator add / change / remove, with
attribution, as three more kinds on the same channel. No new transport, no second
connection: the shape below is unchanged from phase 1 apart from the kinds and the
per-tab id now also travelling on mutating requests.

The one clause of the use case still unimplemented is BR-209's "a change to a bookmark
the user has open in an editor is deferred until the editor closes": the list does
refresh under an open dialog. `BookmarkDialog` edits a copy of the form state, so
typed input cannot be clobbered — but the rule as written is not met.

---

## Backend

### Event

```java
public class CollectionEventJson {                 // @Value, per the project's DTO style
    ID<Collection> collectionId;
    @Nullable ID<Bookmark> bookmarkId;             // null for HEARTBEAT and for batch changes
    ChangeKind kind;                               // SCREENSHOT_READY | BOOKMARK_ADDED
                                                   //  | BOOKMARK_CHANGED | BOOKMARK_REMOVED
                                                   //  | HEARTBEAT
    @Nullable String originClientId;               // null when a background job caused it
    @Nullable String actorName;                    // null when a background job caused it
}
```

Carries no authoritative state (BR-202) — the client refetches through the normal
read path. `originClientId` and `actorName` are deliberately separate fields:

- `originClientId` is **per browser tab**, and exists only to let the tab that caused
  a change ignore its own event (BR-205).
- `actorName` is **per user**, and exists only for the attribution text (BR-209).

Filtering on user ID instead of tab ID is the trap: a user with the same collection
open in two tabs would have the second tab discard updates it genuinely needs. The
bug is invisible until someone opens a second tab, and absent entirely when testing
with two different users.

Both are `null` for the capture job, which is exactly A1's "no attribution is shown" —
and the client uses that directly: no `actorName`, no toast.

`bookmarkId` is null in two cases, which the client must tell apart from "unknown":
a `HEARTBEAT` names nothing because nothing changed, and a batch operation names
nothing because one event covers several bookmarks. Naming one of them would be
arbitrary, and one event per bookmark would turn a 500-bookmark batch into 500 frames
for a client that re-reads the whole collection anyway.

### Broadcaster

```java
@ApplicationScoped
public class CollectionEventBroadcaster {

    private final Map<ID<Collection>, BroadcastProcessor<CollectionEventJson>> processors
        = new ConcurrentHashMap<>();

    // excludeClientId: the calling tab, so it never hears its own change (BR-205).
    // Filtering lives here rather than in the resource so it is unit-testable.
    public Multi<CollectionEventJson> subscribe(
        ID<Collection> collectionId, @Nullable String excludeClientId) { ... }

    // Routing key comes from the event itself — one source of truth.
    public void publish(CollectionEventJson event) { ... }   // no-op when nobody listens
}
```

**Map growth — done in phase 1.** Without care, one entry is created per collection
ever subscribed to and never removed, so the map leaks for the lifetime of the
process. Solved with a subscriber count (`AtomicInteger`) and removal at zero.
Acquire on **subscription** and release via `onTermination`, not acquire when
`subscribe()` is called: a stream that is never subscribed to would otherwise pin
its sink forever, and one subscribed twice would release twice — dropping a sink
whose surviving subscriber then sits on an orphaned processor, silently deaf.

### Publishing after commit

`ScreenshotWriteService.applyCapture` is already the transactional sink for a
successful capture (`@Service` → `@Transactional`, `@RunAs` sysadmin). Fire a CDI
event there:

```java
bookmark.setScreenshotCapturedAt(capturedAt);
events.fire(new CollectionChanged(bookmark.getCollectionId(), bookmarkId,
                                  SCREENSHOT_READY, null, null));
```

and observe it after the commit:

```java
void onCommitted(@Observes(during = TransactionPhase.AFTER_SUCCESS) CollectionChanged e) {
    broadcaster.publish(toJson(e));
}
```

`AFTER_SUCCESS` is what makes BR-203 unbreakable — a rolled-back capture cannot
notify anyone. Firing directly from the job service instead would reintroduce
exactly the "notified about a write that never landed" bug.

`Bookmark` already implements `BelongsToCollection`, so `getCollectionId()` is
available without traversing the entity graph.

**Collaborator changes (phase 2) fire the same way**, from `BookmarkService` at the
*batch* primitives — `createBookmark`, `updateBookmark`, `batchMoveToFolder`,
`batchEditTags`, `batchRemove` — so single-item callers (`removeBookmark` delegates
to `batchRemove`) are covered by one notification site rather than five.

What the write services must **not** grow is a `clientId` parameter threaded through
every signature. `CollectionChangeNotificationService` resolves the acting user and
the originating tab from the ambient request instead — the same trick
`AbstractEntityListener` already uses to stamp audit columns:

```java
@Service                                   // layer stereotype, not @ApplicationScoped:
public class CollectionChangeNotificationService {   // it reads entities (getVornameName)

    public void bookmarkAdded(ID<Collection> collectionId, ID<Bookmark> bookmarkId) { ... }
    public void bookmarkChanged(ID<Collection> collectionId, @Nullable ID<Bookmark> id) { ... }
    public void bookmarkRemoved(ID<Collection> collectionId, @Nullable ID<Bookmark> id) { ... }
}
```

The tab id arrives on `X-Client-Id`, captured by a `@RequestScoped`
`OriginClientIdRequestFilter`. It is absent for anything that is not a browser (the
CLI, API-key clients, tests), which simply means no filtering — the safe direction.

### Endpoint

```java
@GET
@Path("/{collectionId}/events")
@Transactional(TxType.NOT_SUPPORTED)          // MUST override the @JaxResource stereotype
@Produces(MediaType.SERVER_SENT_EVENTS)
@RestStreamElementType(MediaType.APPLICATION_JSON)
public Multi<CollectionEventJson> stream(@RestPath ID<Collection> collectionId,
                                         @RestQuery String clientId) {
    authorizationService.requireCollectionAccess(collectionId);   // BR-201
    return Multi.createBy().merging().streams(
        broadcaster.subscribe(collectionId, clientId),            // BR-205
        heartbeats(collectionId, HEARTBEAT_INTERVAL));            // BR-208
}
```

Three things that are not optional:

**1. `clientId` is a query parameter, not a header.** The browser's `EventSource`
cannot set request headers — there is no way to send `X-Client-Id` on the subscribe
call. Mutating requests (ordinary `fetch`) can and should use a header; only the
stream subscribe is forced into the query string.

**2. `@Transactional(TxType.NOT_SUPPORTED)` at method level.** `@JaxResource` is
`@RequestScoped + @Transactional`, and the ArchUnit rule
`NamingTest.resources_have_JaxResource_stereotype` requires every `*Resource` class
to carry that stereotype — so the stereotype cannot simply be dropped. Overriding at
the method level satisfies both. Confirmed working alongside `@Blocking` (below);
`requireCollectionAccess` reads correctly with no transaction on a worker thread.

**3. `@RateLimit` is mandatory, so BR-208 is met by sizing rather than omission.**
An earlier draft of this document said to leave the annotation off. That is not
possible: `JaxResourceTest.enforce_rate_limit_on_all_methods` requires every
`@JaxResource` to carry `@RateLimit`, and the build fails without it. The cap is
also process-wide rather than per user (see `RateLimitConst`). The endpoint
therefore takes a deliberately generous cap — the same trade `ScreenshotResource`
makes — which bounds a runaway client without being reachable by real usage.

**4. `@Blocking` is required.** A `Multi`-returning method is treated as reactive
and dispatched on the IO thread, where both the JTA interceptor and the blocking
JDBC read behind `requireCollectionAccess` throw
`BlockingOperationNotAllowedException`. This was found by the integration test,
which returned 500 rather than 403 until the annotation was added.

### Heartbeat

A periodic tick (25s) is merged into the returned `Multi`. Caddy needs no keepalive —
its `read_timeout`/`write_timeout` default to no timeout and it auto-flushes
`text/event-stream` — but whatever terminates TLS in front of it may well have an
idle timeout of its own, and heartbeats are what BR-208 calls for regardless.

It is a `ChangeKind.HEARTBEAT` event rather than an SSE comment line: the endpoint
returns a typed `Multi<CollectionEventJson>`, so every frame on it is a serialized
event, and smuggling a raw comment through would mean lying about the return type.
`bookmarkId` is therefore nullable on the wire — a heartbeat names no bookmark.
Ticks are dropped on overflow; a heartbeat backlog is worth nothing and an
unbounded one would fail the stream.

---

## Frontend

`useCollectionEvents(collectionId)`, held by `CollectionView` so the stream's lifetime
is the view's:

- opens `EventSource` on `/api/collections/{id}/events?clientId=…`
- on event → refetch the collection through the existing store path (BR-202); never
  applies the event payload directly
- tears down and reopens on collection change (BR-207 — one stream per client)
- reconnects with **jittered** backoff, bounded, then gives up silently (BR-206, A4)
- on reconnect, refetches in full, since gaps are not replayed (BR-204)
- skips subscribing entirely when offline, resuming via the existing `network-status.ts`
  path (A6)

`lib/client-id.ts` owns the per-tab `crypto.randomUUID()`, held **in memory** —
`localStorage` is shared across tabs and would recreate the per-user filtering bug. It
is shared, not private to the composable, because the same value must ride
`X-Client-Id` on every mutating request (`api/client.ts`); a tab that subscribes with
one id and writes with another would be told about its own changes.

Two effects, not one, for `SCREENSHOT_READY`, because a capture changes two things:

- `lib/preview-nonce.ts` bumps that bookmark's cache-busting nonce. A screenshot is
  **not** in the collection JSON — it is a separate endpoint answering 204 until a
  capture exists — so refetching alone would change nothing on screen; only a new URL
  makes the browser look again. The same module backs the manual refresh action, so
  both routes to "this preview is stale" are one mechanism.
- a coalesced (400 ms) `fetchCollectionInfo(id, { silent: true })` picks up the
  description backfilled from the captured page, which *is* in the JSON. `silent`
  exists for this: a background refresh must not raise the shared `loading` flag (the
  list would blink on every notification) and must not wipe the view with an error
  toast when it fails — the channel is an enhancement, never a dependency (BR-206).

Collaborator kinds take the refetch and deliberately **not** the nonce bump: only a
capture changes an image. Attribution (BR-209) is a `notification.info` toast naming
`actorName`, skipped entirely when there is none — a background job is nobody.

Session expiry during a stream (A7) routes into the existing `lib/session-watch.ts`
handling rather than reconnecting.

CSP needs no change: `EventSource` is governed by `connect-src`, which is already
`'self'` in `frontend/Caddyfile`, and the stream is same-origin.

### Proxy

**Two proxies, the same failure, fixed in both.** A proxy that withholds the
response headers until it has body bytes will hang any stream that is idle after
subscribing — which is the normal state of this channel — and `EventSource` sits
in `CONNECTING` with nothing in the server log to suggest a problem.

*Dev* — `vite.config.ts` flushes headers on `proxyRes` for `text/event-stream`.
The flush must be deferred with `setImmediate`: the event fires before http-proxy
copies the upstream headers, so an inline flush ships a bare `200` with no
`content-type` and the browser rejects the stream. Verified with `curl` through
both 5173 and 8443.

*Prod* — `frontend/Caddyfile` excludes `^/api/.+/events$` from `encode gzip`,
using `path_regexp` and **not** a `path` glob: `*` in a `path` matcher does not
cross `/`, so the obvious-looking `/api/*/events*` does not match the real
`/api/collections/{id}/events` and leaves the stream compressed. Verified by
running the file under `caddy` against an idle SSE upstream — with the glob, no
response headers arrive at all; with the regexp they arrive immediately.
Without the exclusion Caddy withholds the response headers until the first body
byte, so a stream that stays idle after subscribing never completes its handshake and
`EventSource` hangs in `CONNECTING`
([caddy#6293](https://github.com/caddyserver/caddy/issues/6293)). Production
negotiates h2, so the HTTP/1.1 six-connections-per-origin limit does not apply.

---

## Tests

What exists, by layer:

- **Backend unit** — `CollectionEventBroadcasterTest` (11): fan-out, no cross-collection
  leak (BR-210), no replay to a late subscriber (BR-204), the four `excludeClientId`
  cases (BR-205), sink released at zero subscribers, and two regressions for the
  acquire/release asymmetry — a stream never subscribed to must pin nothing, and two
  subscriptions of one stream must be tracked separately.
  `CollectionEventResourceTest` pins the heartbeat: an opening frame **immediately**
  on subscribe (what completes the browser's handshake), then repeating.
- **Backend integration** — `CollectionEventPublisherITest`: published after commit, and
  **nothing published when the transaction rolls back** (BR-203, the one that earns its
  keep). `CollectionEventResourceITest`: 403/401 over HTTP (BR-201), plus the accepted
  path driven through CDI rather than RestAssured — a successful subscribe never
  completes, so consuming the `Multi` directly is what makes it assertable, including
  that cancellation releases the sink through the heartbeat merge.
  `CollectionChangeNotificationServiceITest`: the write paths announce themselves, one
  event per batch, and the acting tab hears nothing (BR-205 over real HTTP with
  `X-Client-Id`). `ScreenshotWriteServiceITest` covers the A1 producer.
- **Frontend unit** — `useCollectionEvents.spec.ts` (13) against a fake `EventSource`:
  heartbeats ignored, nonce invalidation, burst coalescing, attribution present/absent,
  one stream per collection, teardown on unmount, offline skip and resume, refetch on
  reconnect, bounded give-up.
- **Frontend e2e** — `live-updates.spec.ts`, below.

An earlier draft proposed consuming the stream in the ITest with a
`@RegisterRestClient` interface returning `Multi<CollectionEventJson>`. Not needed:
calling the resource through CDI covers the same assembly without a second HTTP client
to keep alive.

### What only e2e can prove

Everything above stubs at least one boundary. `live-updates.spec.ts` is the only test
where a real browser holds a real `EventSource` through the dev proxy while a *second
user's* write travels the whole path, and it is deliberately narrow for that reason:

| Case | Rule | Why e2e |
| --- | --- | --- |
| A collaborator's bookmark appears with no reload | Main scenario | The whole chain end to end |
| The toast names the collaborator | BR-209 | Attribution reaching the DOM |
| A collaborator's deletion disappears | Main scenario | The removal kind, same path |
| The acting tab is told nothing about its own write | BR-205 | `X-Client-Id` on a real request from a real tab — the one wiring no unit test can see |

Deliberately **not** e2e: A1's preview appearing (needs the capture sidecar to actually
fetch a page — slow and flaky for what two integration tests already prove), reconnect
backoff (fake timers do it better), and BR-207's stream re-targeting (asserting on
connection identity from Playwright is far more fragile than the unit test).

The e2e is mutation-checked in both directions: with `useCollectionEvents` disabled in
`CollectionView` the delivery tests fail, and with `OriginClientIdRequestFilter`
returning null the BR-205 test fails. A live-update test that passes without live
updates would be worse than no test at all.

That second check is why `e2e/helpers/toasts.ts` exists. **Asserting a toast's absence
against the live DOM proves nothing**: a toast that appears and auto-dismisses before
the assertion runs is indistinguishable from one that never appeared, and the first
version of the BR-205 test passed against a deliberately broken server for exactly
that reason. The helper records every toast as it appears, so "this was never
announced" is asserted over history rather than over a snapshot.

---

## Single-instance constraint

Fan-out is in-process, so this is correct only while the app runs as one instance —
which C-003 (single SQLite database), C-011/C-012 (local-disk caches) and the
in-process `@Scheduled` jobs already require independently. FR-100 states this.

Going multi-instance would need a shared bus (each instance subscribes; on a local
event, publish to the bus; each instance re-broadcasts to its own subscribers).
Nothing above changes shape — `broadcaster.publish(...)` gains a network hop. SQLite
is the wall here, not SSE.
