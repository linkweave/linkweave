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

## Scope of phase 1

Alternative flow A1 only — **deferred screenshot capture completing**. It is the one
asynchronous producer that already exists (`ScreenshotCaptureJobService`, 30s tick),
and it is verifiable in a single browser with no second user. Collaborator events
(the main success scenario) are a second event kind on the same channel and follow
once the transport is proven.

---

## Backend

### Event

```java
public record CollectionEventJson(
    String collectionId,
    String bookmarkId,
    ChangeKind kind,                    // SCREENSHOT_READY | SCREENSHOT_FAILED | BOOKMARK_ADDED | ...
    @Nullable String originClientId,    // null when a background job caused it
    @Nullable String actorName          // null when a background job caused it
) {}
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

Both are `null` for the capture job, which is exactly A1's "no attribution is shown".

### Broadcaster

```java
@ApplicationScoped
public class CollectionEventBroadcaster {

    private final Map<ID<Collection>, BroadcastProcessor<CollectionEventJson>> processors
        = new ConcurrentHashMap<>();

    public Multi<CollectionEventJson> subscribe(ID<Collection> collectionId) { ... }

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
    return broadcaster.subscribe(collectionId)
        .filter(e -> !clientId.equals(e.originClientId()));       // BR-205, null passes
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

A `useCollectionEvents(collectionId)` composable:

- generates `clientId` once per tab with `crypto.randomUUID()`, held **in memory** —
  `localStorage` is shared across tabs and would recreate the per-user filtering bug
- opens `EventSource` on `/api/collections/{id}/events?clientId=…`
- on event → refetch the collection through the existing store path (BR-202); never
  apply the event payload directly
- tears down and reopens on collection change (BR-207 — one stream per client)
- reconnects with **jittered** backoff, bounded, then gives up silently (BR-206, A4)
- on reconnect, refetches in full, since gaps are not replayed (BR-204)
- skips subscribing entirely when offline, resuming via the existing `network-status.ts`
  path (A6)

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

Per CLAUDE.md, both layers:

- **Backend unit** — `CollectionEventBroadcaster`: fan-out to multiple subscribers,
  no replay to a late subscriber, `originClientId` filtering, processor removal at
  zero subscribers.
- **Backend integration** — `CollectionEventResourceITest`, consuming the stream with a
  `@RegisterRestClient` interface returning `Multi<CollectionEventJson>`
  ([REST client SSE support](https://quarkus.io/guides/rest-client#server-sent-event-sse-support)),
  which is far less awkward than driving SSE through RestAssured: 403 without collection
  access (BR-201); an event published after a committed capture reaches a subscriber;
  **no event is delivered when the transaction rolls back** (BR-203 — the test that
  actually earns its keep).
- **Frontend** — composable unit test with a mocked `EventSource` covering self-filtering
  and teardown-on-collection-change; optionally an e2e asserting a preview appears
  without a reload.

---

## Single-instance constraint

Fan-out is in-process, so this is correct only while the app runs as one instance —
which C-003 (single SQLite database), C-011/C-012 (local-disk caches) and the
in-process `@Scheduled` jobs already require independently. FR-100 states this.

Going multi-instance would need a shared bus (each instance subscribes; on a local
event, publish to the bus; each instance re-broadcasts to its own subscribers).
Nothing above changes shape — `broadcaster.publish(...)` gains a network hop. SQLite
is the wall here, not SSE.
