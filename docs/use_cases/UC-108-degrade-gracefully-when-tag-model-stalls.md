# Use Case: Degrade Gracefully When the Tag-Suggestion Model Stalls

## Overview

**Use Case ID:** UC-108
**Use Case Name:** Degrade Gracefully When the Tag-Suggestion Model Stalls
**Primary Actor:** User (creating or editing a bookmark)
**Secondary Actors:** Operator, local model service (Ollama), hosted LLM provider
**Goal:** Keep bookmark creation and editing fast and predictable when the tag-suggestion model is slow, hung, or unavailable — the feature goes quiet, the host stays idle, and the operator can see that it happened, instead of every save dragging behind a model that is not answering.
**Status:** Implemented

## Traceability

**Maps to:** NFR-029
**Related:** UC-097 (Auto-Tag Bookmark with Local LLM — the feature this hardens; BR-077 "best-effort, non-blocking" is the rule that was violated in practice); UC-045 (rule-based fallback); UC-005 / UC-007 (Create / Edit Bookmark — the flows that must stay fast); UC-088 (Collect Application Metrics); UC-109 (Survive SQLite Write Contention — the same incident, the other half)

---

## Background — observed failure (2026-08-28)

Saving bookmarks became intermittently very slow. The application log shows this repeating:

```
Ollama chat failed (The timeout period of 30000ms has been exceeded while executing
POST /api/chat for server ollama:11434); re-pulling model 'gemma2:2b' before the next suggestion
```

Eight such lines appear in a single log excerpt, interleaved with `SQLITE_BUSY` failures (UC-109). Reading the code paths behind that line exposes four distinct defects, each of which this use case is written to prevent:

1. **A 30-second budget on an interactive path.** `quarkus.rest-client.ollama.read-timeout=30000`, and the suggestion request auto-fires 700 ms after the user stops typing a title/URL (`useAiTagSuggestions.ts`, `AUTO_FIRE = true`). A stalled model therefore holds a server worker thread — and the dialog's "Suggested tags" spinner — for a full 30 s per attempt, on every bookmark the user creates.
2. **A timeout is misread as a missing model.** `LlmTaggingClientImpl.invalidateModelAfterFailure` clears the `modelPulled` flag and schedules `POST /api/pull` after *any* `RuntimeException` — including a read timeout. But a timeout means "the model is busy or too slow", not "the weights are gone". The re-pull adds disk and CPU load to a host that is already too loaded to answer in 30 s, which makes the next timeout more likely: a feedback loop, visible in the log as the same warning repeating.
3. **Silent total loss of the feature.** While `modelPulled` is false, `suggestViaOllama` returns an empty list without calling the model at all. After the first timeout, the user gets *no* AI suggestions and *no* indication why — the section simply shows the empty state, which is indistinguishable from "the model found nothing" (UC-097 A4).
4. **An unbounded background pull.** `quarkus.rest-client.ollama-pull.read-timeout=1800000` (30 minutes) on a `ManagedExecutor` thread. A hung Ollama can pin that thread for half an hour per pull cycle.

None of this ever blocks the save request itself — the suggestion endpoint is separate and the client aborts on unmount — but it makes the whole application feel slow while it is happening, and it starves the host that the database is also running on.

## Preconditions

- LLM auto-tagging is enabled (`linkweave.autotag.llm.enabled=true`, FR-096).
- The user is creating or editing a bookmark in a collection that has at least one tag (UC-097 preconditions).

## Main Success Scenario

1. User enters a title and URL in the bookmark dialog.
2. System requests tag suggestions after the debounce window.
3. The model does not answer within the **suggestion budget** (BR-108-1).
4. System abandons the request at the budget, records the failure, and returns "no suggestions" to the client.
5. System opens the suggestion **circuit** after the configured number of consecutive failures (BR-108-4): further suggestion requests return immediately without contacting the model.
6. System shows the user a quiet, honest state — "tag suggestions unavailable" — distinct from "the model found nothing" (BR-108-5).
7. System logs the transition **once**, not once per call, and increments a failure metric (BR-108-8).
8. User continues: rule-based suggestions (UC-045) still appear, tags can be picked manually, and the bookmark saves at normal speed (BR-108-3).
9. After the cooldown, System probes the model with a single cheap request; on success the circuit closes and suggestions resume silently.

## Alternative Flows

### A1: Cold Start (Model Present but Unloaded)

**Trigger:** The model is on disk but not resident; the first call must load it (step 3).
**Flow:**

1. System distinguishes a cold-start load from a stall: the warm-up path (`/api/generate` with no prompt) is what pays load latency, and it runs off the request path.
2. If the interactive request nonetheless exceeds its budget, it is abandoned per the main scenario — but this is **not** counted as a model failure and does **not** trigger a pull (BR-108-2).
3. Use case continues at step 8; the next suggestion, once the model is resident, succeeds.

### A2: Model Genuinely Missing

**Trigger:** The model service answers that the model is not installed (an explicit "model not found" response, not a timeout) (step 3).
**Flow:**

1. System schedules **one** pull, subject to the pull budget and backoff (BR-108-6).
2. While the pull runs, System reports "preparing the model" rather than an empty suggestion set, so the user knows the feature is coming back (BR-108-5).
3. On pull success, suggestions resume. On pull failure, the circuit opens and the backoff interval doubles up to its ceiling.

### A3: Model Service Unreachable

**Trigger:** Connection refused / DNS failure (the container is down) (step 3).
**Flow:**

1. System opens the circuit immediately — no retry storm against a host that is not listening, and no pull attempt.
2. Use case continues at step 6.

### A4: Operator Disables Auto-Fire

**Trigger:** The host is too small to serve suggestions at interactive speed, but the operator still wants the feature on demand.
**Flow:**

1. Operator sets auto-fire off (BR-108-7).
2. No model call happens until the user clicks "Suggest tags with AI".
3. Use case continues at step 3 for that explicit request.

### A5: Hosted Provider

**Trigger:** `linkweave.autotag.provider=openai` (FR-097) and the hosted API is slow or rate-limiting (step 3).
**Flow:**

1. The same budget, circuit, and metrics apply — the rules are provider-agnostic.
2. No pull logic exists for hosted providers; only the circuit and budget apply.

### A6: User Saves While a Suggestion Is In Flight

**Trigger:** User submits the dialog before suggestions return (step 3).
**Flow:**

1. Client aborts the in-flight suggestion request.
2. Save proceeds immediately; its latency is unaffected by the model (BR-108-3).
3. Use case ends — the bookmark is saved without model-suggested tags (UC-097 A5).

## Postconditions

### Success Postconditions

- Bookmark create/edit latency is independent of model health.
- A stalled model produces at most one in-flight call per user action and, once the circuit is open, none at all.
- No model download is triggered by a slow response.
- The operator can tell from logs and metrics that suggestions are degraded, and why.

### Failure Postconditions

- Suggestions are unavailable; the user is told so plainly. Rule-based suggestions and manual tagging are unaffected. Nothing is lost, and nothing is retried in a loop.

## Business Rules

### BR-108-1: Interactive Latency Budget

A suggestion request made on behalf of an open dialog must be abandoned within a budget appropriate to that interaction — a small multiple of the debounce, not a network default. The budget is operator-configurable with a default of **≤ 8 s** (proposed; the value is settled when NFR-029 is implemented) and must be strictly lower for the interactive path than for the background warm-up path. The client applies the same budget with an abort, so a server-side hang cannot leave the dialog spinning.

### BR-108-2: A Timeout Is Not a Missing Model

Only an explicit "model not installed" answer from the model service may invalidate the model-ready state or trigger a pull. Timeouts, connection failures, HTTP 5xx, and malformed responses must never trigger a pull — those indicate the service is unhealthy or overloaded, and downloading weights makes an overloaded host worse.

### BR-108-3: Never Slow Down the Save

Bookmark create/edit/import must never wait on the model, directly or indirectly. Verification is explicit: an automated test with a model client that hangs for longer than the budget must show unchanged save latency and must show that no model call is in flight when the save request is served.

### BR-108-4: Circuit Breaker with Cooldown

After a configured number of consecutive failures, the system stops calling the model for a cooldown period and answers suggestion requests immediately with "unavailable". After the cooldown, exactly one probe is allowed through; success closes the circuit, failure re-opens it with an increased cooldown up to a ceiling. While the circuit is open, no request thread and no executor thread may be occupied by the model.

### BR-108-5: Degraded Is Not Empty

"The model is unavailable" and "the model had no confident suggestions" (UC-097 A4) are different states and must look different to the user. The unavailable state names the situation in one quiet line; it never shows an error dialog, never blocks, and never invites a retry loop.

### BR-108-6: Pulls Are Rate-Limited, Bounded, and Isolated

At most one pull may be in flight; a pull may not start more often than its backoff interval allows (exponential, with a ceiling); a pull runs on an executor that cannot starve request handling; and a pull has a bounded overall lifetime after which it is abandoned and retried on the normal backoff. First-run download latency (minutes) is expected and acceptable — repeated downloads triggered by failures are not.

### BR-108-7: Auto-Fire Is Operator-Configurable

Whether suggestions fire automatically as the user types, or only on explicit request, is a configuration setting rather than a hard-coded constant (`AUTO_FIRE` in `useAiTagSuggestions.ts` today). Hosts that cannot serve the model at interactive speed must be able to keep the feature on demand only, without disabling it entirely (FR-096).

### BR-108-8: One Log Line Per State Change, Plus a Metric

Model health changes (healthy → degraded → healthy, pull started / finished / failed) are logged at WARN/INFO **once per transition**. Per-call failures are DEBUG. A counter tagged with provider, model, and outcome (`ok` / `timeout` / `unavailable` / `circuit_open`) is exposed via the metrics endpoint (UC-088), respecting the cardinality budget (NFR-024).

### BR-108-9: Concurrency Ceiling

Concurrent in-flight model calls are capped. Requests beyond the cap return "no suggestions" immediately rather than queueing, so a slow model can never consume the worker pool that serves bookmark reads and writes.

## Acceptance Criteria

1. With a model client stubbed to hang, a bookmark save completes within its normal latency budget and no pull request is issued.
2. With the same stub, the *n*-th consecutive suggestion request (n = the configured threshold) returns immediately and no further model calls are made until the cooldown elapses.
3. A "model not found" response — and only that — triggers exactly one pull; a second failure within the backoff interval triggers none.
4. The log contains one warning per health transition, not one per failed call.
5. Turning auto-fire off results in zero model calls until the user clicks the suggest button.

## Implementation Notes

Implemented 2026-09-02.

| Rule | Where |
| --- | --- |
| BR-108-1 (budget) | `linkweave.autotag.suggest-timeout-ms` (8 s) drives `quarkus.rest-client.ollama.read-timeout`; warm-up moved to `OllamaWarmUpClient` (120 s) so the interactive path no longer inherits a cold-start-sized timeout. The client re-uses the same budget, reported on the warm-up response, for its own abort. |
| BR-108-2 (timeout ≠ missing model) | `LlmFailure.classify` walks the cause chain; only an explicit 404 "not found / try pulling" body clears `modelPulled` or schedules a pull. |
| BR-108-3 (never slow the save) | `BookmarkAutoTagResource` is `@Transactional(NOT_SUPPORTED)`. See below. |
| BR-108-4 (circuit) | `LlmCircuitBreaker`: consecutive-failure threshold, cooldown doubling per failed probe up to a ceiling, one half-open probe admitted per cooldown. |
| BR-108-5 (degraded ≠ empty) | `SuggestedTagsResultJson.status`; the dialog renders distinct `unavailable` and `preparing` states. |
| BR-108-6 (pull limits) | Single in-flight pull, exponential backoff (`linkweave.autotag.pull.min-interval`), pull read-timeout cut from 30 min to 10. |
| BR-108-7 (auto-fire) | `linkweave.autotag.auto-fire`, reported on the warm-up response; the frontend's hard-coded `AUTO_FIRE` constant is gone. |
| BR-108-8 (log once + metric) | `LlmCircuitBreaker.logTransition`; `linkweave.autotag.suggestions{provider,model,outcome}`. |
| BR-108-9 (concurrency) | Semaphore in `LlmCircuitBreaker`; overflow answers `OVERLOADED` immediately. A permit belongs to a *scope* — one user in one collection — rather than to a request, so the cap counts concurrent editors, not keystrokes. See below. |

### The defect behind BR-108-3

The background section above concluded that the suggestion path "never blocks the
save request itself". That was wrong, and the mechanism is worth recording.

`BookmarkAutoTagResource` inherited `@Transactional(REQUIRED)` from the
`@JaxResource` stereotype. Its authorization guard reads the database, so a JDBC
connection was acquired and enlisted in the resource's transaction *before* the
model was called. `BookmarkAutoTagLlmService` being `NOT_SUPPORTED` suspends that
transaction but does not end it — Agroal returns a connection when the
transaction completes, not when it is suspended — so every in-flight suggestion
held one pooled connection for the model's full 30 s read-timeout. With auto-fire
firing a suggestion for every bookmark being typed, the pool drained and
unrelated requests, saves included, blocked in `getConnection()`. This is also
where a large share of UC-109's `SQLITE_BUSY` pressure came from.

`BookmarkAutoTagStallITest` reproduces it with a two-connection pool and four
hung suggestions; it fails with `Unable to acquire JDBC Connection [Sorry,
acquisition timeout!]` against the pre-fix resource.

### Newest-wins within a scope (BR-108-9)

A client-side abort does not stop the server. When the user keeps typing, the
browser drops its previous suggestion request, but the worker thread stays in the
blocking model read until the budget expires — so a naive per-request cap let a
single fast typist hold several permits with work nobody was waiting for any
more.

Permits are therefore keyed by scope (`<userId>:<collectionId>`, built in the
resource, the only layer that knows who is calling). A newer call for a scope
that already holds a permit *takes it over* instead of claiming a second; the
superseded call finds itself no longer current when it finishes and does not hand
the permit back, so the chain owns exactly one throughout. Eviction across
different scopes is deliberately not done — cancelling one user's live request to
serve another's would be arbitrary.

This does not *cancel* the superseded call: a thread parked in a synchronous HTTP
read cannot be reclaimed, and `Thread.interrupt()` is not honoured by the
Vert.x-backed REST client. True cancellation would need the model call to be
reactive end to end — `OllamaClient.chat` returning `Uni`, and a non-blocking
suggestion endpoint, which is supported (the project uses
`quarkus-rest-client-jackson`, the reactive client) but is a real refactor in an
otherwise blocking JDBC/Hibernate path. It is worth doing only if the cap is
observed to bite; `linkweave.autotag.suggestions{outcome="overloaded"}` is the
signal to watch.

The default cap was raised from 2 to 6 at the same time. Two was sized while the
worst case was still a drained JDBC pool; with BR-108-3 fixed, the only resource
at stake is the worker pool (~200 threads), and these endpoints already carry a
60/min per-caller rate limit.

## Notes / Future Considerations

- The retry/timeout/circuit machinery here is exactly what `quarkus-langchain4j` provides out of the box; UC-097's "Notes" deferred that dependency. If BR-108-4/BR-108-6/BR-108-9 turn out to be more than a thin wrapper over SmallRye Fault Tolerance (`@Timeout`, `@CircuitBreaker`, `@Bulkhead` — already on the classpath, as `@RateLimit` in `BookmarkAutoTagResource` shows), that deferral is worth revisiting.
- Model health belongs on the operator dashboard (UC-090) next to the job metrics, not only in the log.
