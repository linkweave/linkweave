# Plan: Local-LLM Auto-Tagging Service

## Context

[UC-097](../use_cases/UC-097-autotag-bookmark-with-local-llm.md) adds a second, AI-based tag suggestion source alongside the rule-based engine ([UC-045](../use_cases/UC-045-auto-tag-bookmark-by-url-pattern.md) / [auto-tag-custom-rules.md](auto-tag-custom-rules.md)). A locally-hosted small model (**gemma 2B**) reads a bookmark's title + URL and proposes tags drawn **only from the collection's existing tag set**. Suggestions are reviewed by the user, not auto-applied.

This document covers the *engineering* side: how the model is hosted, how its lifecycle (load / idle-unload) is managed, how the API talks to it, how constrained output is enforced, and how suggestions are computed on demand and surfaced. The UX flow itself lives in UC-097.

The design deliberately mirrors the **screenshot sidecar** ([screenshot-previews.md](screenshot-previews.md)): a separate container the Quarkus API reaches over HTTP via a Quarkus REST Client, gated behind a master switch, disabled in tests.

## Goals

- Host gemma 2B as a **companion container** running [Ollama](https://ollama.com), reachable only from the API.
- **On-demand load**: the model loads on the first tagging request; no model is resident at idle.
- **Idle unload**: the model is evicted from memory after a configurable inactivity window, reclaiming ~1.6 GB.
- **Constrained output**: the model can only return tags that already exist in the collection — enforced, not merely prompted.
- **Best-effort & non-blocking**: bookmark create / edit / import always succeed even if the model is down (BR-077).
- **Local-only by default**: with the Ollama provider, bookmark text never leaves the host (BR-080). A hosted provider is opt-in (see *Providers*).
- **Pluggable provider** (FR-097): local Ollama or any hosted OpenAI-compatible API (e.g. z.ai GLM), chosen by config behind one interface.

## Non-goals

- Creating new tags, renaming, or merging (BR-076 — vocabulary is fixed to existing tags).
- GPU inference / model fine-tuning. CPU inference of a 2B model is acceptable for on-demand tagging.
- **Persisting suggestions.** Suggestions are computed on demand and not stored; only an *accepted* tag persists (via `Bookmark.tags`). No suggestion entity, no migration, no background job. See *Why no persistence* below.
- Blocking the create/edit form on inference. The suggest call is its own request; the UI renders rule chips immediately and folds in AI chips when the call returns.
- The Anthropic/Claude SDK. A hosted option is supported via the generic **OpenAI-compatible** API (which z.ai exposes); we do not add the Anthropic SDK.
- Automatic multi-provider failover / load-balancing. Exactly one provider is active per deployment (config-selected); there's no runtime fan-out across providers.

---

## Architecture Decision: Ollama companion container

Three options were considered for *where the model runs*:

| Option | Lifecycle control | Deployment | Verdict |
|---|---|---|---|
| **A. Ollama companion container** | `keep_alive` per request — native load/unload | One compose service, same pattern as screenshot sidecar | **Chosen** for the hosted deployment |
| B. Host-installed Ollama service | Same `keep_alive`, but host-managed process | Operator installs Ollama on the host; API points at it | Fallback for the desktop app (UC-052), where there is no compose stack |
| C. Embed inference in the JVM (e.g. llama.cpp via JNI / DJL) | In-process, no extra container | No sidecar, but pulls a heavy native dep into the API | Rejected — couples model memory to the API heap and complicates the build |

**Key insight: we do not write a model-lifecycle manager.** Ollama already does load-on-demand and idle-unload natively via the `keep_alive` request parameter:

- A request naming a model that isn't resident **loads it** (cold start, a few seconds for 2B).
- `keep_alive: "15m"` keeps it resident 15 min after the *last* request; `keep_alive: 0` unloads immediately; `-1` keeps it forever.
- An optional **warm-up** request (model named, empty/no prompt) preloads weights so the first real tagging call is fast — fired when a user starts adding a bookmark.

So "manage Ollama by the API" reduces to: set `keep_alive` on each call, and optionally send a warm-up ping. No supervision, no custom load/unload endpoints.

**Container shape** (compose service `ollama`, mirroring `screenshot-service/docker-compose.yml`):

```yaml
services:
  ollama:
    build: ./ollama            # FROM ollama/ollama + entrypoint that pulls gemma2:2b
    image: linkweave-ollama:dev
    init: true
    mem_limit: 2g          # gemma2:2b ≈ 1.6 GB resident; headroom for load spikes
    cpus: 2.0
    restart: unless-stopped
    volumes:
      - ollama-models:/root/.ollama   # persist pulled weights across restarts
    # Local dev: loopback only so quarkus:dev (on the host) can reach it.
    # Production: drop `ports:`, keep both API and ollama on linkweave-internal.
    ports:
      - "127.0.0.1:11434:11434"
    networks:
      - linkweave-internal
    healthcheck:
      test: ["CMD", "ollama", "ps"]   # 0 exit once the server is up
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 20s

volumes:
  ollama-models:
networks:
  linkweave-internal:
```

**Model provisioning (decided: image entrypoint).** The base `ollama/ollama` image ships no models, so gemma 2B (`gemma2:2b`) is pulled by a thin **entrypoint wrapper** baked into our own image (`ollama/Dockerfile` `FROM ollama/ollama`). The wrapper starts the server, pulls the model if absent, and stays in the foreground:

```sh
#!/bin/sh
# ollama/entrypoint.sh
ollama serve &
SERVER_PID=$!
# wait for the server, then pull (no-op if the volume already has it)
until ollama ps >/dev/null 2>&1; do sleep 1; done
ollama pull "${LINKWEAVE_AUTOTAG_MODEL:-gemma2:2b}"
wait "$SERVER_PID"
```

Pulled weights land in the `ollama-models` volume, so it's a one-time cost across restarts; a fresh volume self-heals on next boot. Provisioning lives entirely in the container — the API never calls `POST /api/pull`, keeping the API free of provisioning concerns. The compose service builds this image (`build: ./ollama`) rather than using `ollama/ollama:latest` directly.

**Desktop app (UC-052) note.** The packaged desktop bundle has no compose stack, so there it's Option B: detect a host Ollama install (or bundle one), and point `linkweave.autotag.service-url` at `http://127.0.0.1:11434`. The API code is identical — only the URL config and provisioning differ.

---

## Lifecycle & memory model

```
                       warm-up ping (user opens "add bookmark")
   idle (0 MB)  ───────────────────────────────────────────▶  loading (~2-4s)
        ▲                                                          │
        │  keep_alive window elapses                               ▼
        └──────────────  resident (~1.6 GB)  ◀──── tagging request ┘
                          keep_alive reset on every request
```

- **Cold start (A1 in UC-097):** first request after idle loads the model. The UI shows a "preparing suggestions" state; the call returns once weights are loaded + inference done.
- **Warm window:** every tagging or warm-up call sets `keep_alive` to `linkweave.autotag.keep-alive` (default `15m`), sliding the unload deadline.
- **Idle unload:** after the window with no calls, Ollama frees the model. No action from the API.
- **Configurable** via `linkweave.autotag.keep-alive`; `0` = unload-after-each (lowest memory, slowest), `-1` = always resident (fastest, ~1.6 GB pinned).

This is entirely Ollama-native; the API only chooses the `keep_alive` value and optionally warms.

---

## Providers (FR-097)

`LlmTaggingClient` is the seam; a single bean `LlmTaggingClientImpl` dispatches on `linkweave.autotag.provider`:

| Provider | `provider=` | Transport | Model from | Constraint mechanism | Data leaves host? |
|---|---|---|---|---|---|
| **Ollama** (default) | `ollama` | `OllamaClient` → local `/api/chat` | `linkweave.autotag.model` | native `format` JSON-Schema **enum** (strict) | No |
| **OpenAI-compatible** | `openai` | `OpenAiClient` → `/chat/completions` | `linkweave.autotag.openai.model` | `response_format: json_object` + allowed list in prompt | **Yes** |

- **z.ai (GLM Coding Plan)** is the motivating hosted case: it speaks the OpenAI-compatible chat-completions API. Configure `provider=openai`, `openai.base-url=https://api.z.ai/api/paas/v4`, `openai.model=glm-4.6`, and `LINKWEAVE_AUTOTAG_API_KEY=<key>`. Any other OpenAI-compatible endpoint (OpenAI, OpenRouter, …) works the same way.
- **Why not strict enum for the hosted path?** Not all OpenAI-compatible providers honor a `json_schema` enum, so the hosted path uses the widely-supported `json_object` mode and lists the allowed tags in the prompt. Correctness is still guaranteed because the **service re-validates** every returned name against the vocabulary (below) and drops anything off-list — so even a non-compliant provider can't introduce tags.
- **Selection is per-deployment** (config-selected, effective at startup). Exactly one provider is active; there's no runtime fan-out.
- **Secret handling:** the API key is read from config (`linkweave.autotag.openai.api-key`, sourced from an env var), passed as a bearer header per call, and never logged.
- **Privacy:** `provider=openai` sends bookmark text to the hosted endpoint — an explicit operator choice that forfeits the local-only guarantee (BR-080). The default stays local.

---

## Constrained output (the core correctness mechanism)

Ollama supports **structured outputs**: pass a JSON Schema in the request's `format` field and generation is constrained to match it. We make `tags` an array whose items are an `enum` of the collection's current tag names:

```jsonc
// POST /api/chat
{
  "model": "gemma2:2b",
  "stream": false,
  "keep_alive": "15m",
  "options": { "temperature": 0 },           // deterministic classification
  "format": {
    "type": "object",
    "properties": {
      "tags": {
        "type": "array",
        "items": { "type": "string", "enum": ["rust", "databases", "career"] }
      }
    },
    "required": ["tags"]
  },
  "messages": [
    { "role": "system", "content": "Pick the tags from the allowed list that best describe this bookmark. Choose none if nothing fits. Return only tags from the list." },
    { "role": "user", "content": "Title: ...\nURL: ...\nDescription: ..." }
  ]
}
```

The enum makes out-of-vocabulary tags **impossible to generate**, so we never violate `uc_tag_name_collection` and never invent tags (BR-076). After parsing, we still **defensively re-validate** returned names against the loaded `Tag` set (belt-and-suspenders against a model that ignores the schema, and to map names → `Tag` ids).

**Vocabulary-size caveat.** The enum is the collection's full tag list. Small models + the prompt handle a few dozen comfortably; hundreds degrade quality and inflate latency. Mitigation when needed (not for v1): pre-filter to the top-N most-used tags before building the enum.

**Quality lever.** `Tag` today is only `name` + `color`, so the model classifies off the name alone. If results are weak, the cheapest improvement is an optional `Tag.description` fed into the prompt — cheaper than a bigger model. Flagged as a follow-up, not v1.

---

## Data model — none (why no persistence)

**Decided: suggestions are computed on demand and never stored.** There is **no new entity, no migration, no status column.** The only durable outcome — an *accepted* tag — already persists through the existing `Bookmark.tags` join.

Persisting suggestions would buy exactly two things, and neither justifies the machinery:

1. **Caching the LLM call** — recoverable far more cheaply with a short-lived in-memory cache (output is deterministic at `temperature=0`), no DB table or invalidation needed.
2. **Remembering dismissals** so a rejected suggestion doesn't reappear — *not required by UC-097* (its dismiss flows mean "don't apply this time," not "never show again"). If real-world use shows reappearing suggestions are annoying, the minimal fix is a tiny `(bookmark_id, tag_id)` dismissals record bolted on later — without touching the compute path.

Computing live also keeps suggestions **correct by construction**: they always reflect the collection's current tag vocabulary, so there's nothing to invalidate when tags are added or renamed.

What this removes versus a persisted design: the `BookmarkTagSuggestion` entity + repo, the Flyway migration, the `PENDING/ACCEPTED/DISMISSED` state machine, the scheduled background producer, and the create/edit/import enqueue hooks.

---

## Combining with rule-based suggestions (presentation layer)

**Decided: rules stay entirely on the frontend; the LLM only adds to the suggestion list.** No backend rule engine, no persisted `RULE` rows. The two sources have an asymmetry that makes this the natural split:

| | Rule-based (UC-045, today) | LLM (this plan) |
|---|---|---|
| Where it runs | client-side (regex) | backend endpoint (Ollama) |
| Cost to compute | ~free | seconds; loads a model |
| Computed | on demand | on demand |
| Storage | none | none |

Both sources are now computed **on demand** and neither is persisted — rules client-side from the Pinia stores, the LLM via a backend endpoint. **Unification happens in the UI, not the database.**

### Merge & hierarchy

The "Suggested Tags" component builds its list by concatenating both sources in a fixed priority order, **de-duplicated by tag** (a tag suggested by more than one source appears once, attributed to the highest-priority source):

1. **Built-in rules** (`BUILT_IN_RULES`)
2. **Custom auto-tag rules** (`AutoTagRule`, in `sortOrder`)
3. **LLM suggestions** (from the on-demand `suggest-tags` call for this bookmark)

This extends the existing `suggestTagNames` ordering in `frontend/src/lib/auto-tag-rules.ts` (built-in → custom) by appending the LLM source last. Rules win ties because they're explicit, user-authored intent; the LLM fills gaps rules don't cover.

### Where each source applies

- **Compose-time (new, unsaved bookmark):** rule chips work exactly as today (instant, client-side). The frontend can also call `suggest-tags` with the entered title/URL even before the bookmark is saved — there's no `bookmark_id` dependency since nothing is stored; the call just takes text + collection id.
- **Editing / reviewing a saved bookmark:** same merge — live rule matches plus a fresh `suggest-tags` call, ordered by the hierarchy above.
- **Import:** no pre-tagging. Suggestions are computed lazily when the user opens a bookmark to review it, avoiding a burst of inference for bookmarks that may never be reviewed.

The UI labels each chip's origin (rule vs. AI) since it knows which list a tag came from.

---

## Backend

### Package: `org.linkweave.api.autotag.llm` (or extend `org.linkweave.api.bookmark`)

Layering per CLAUDE.md: **Resource (auth) → Service (orchestration) → Repo / REST-client**. Authorization happens only in the Resource via `AuthorizationService.requireCollectionAccess`.

### `OllamaClient` (Quarkus REST Client)

Mirrors `ScreenshotSidecarClient`. URL + timeouts under `quarkus.rest-client.ollama.*`.

```java
@RegisterRestClient(configKey = "ollama")
@Path("/api")
public interface OllamaClient {
    @POST @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
    ChatResponse chat(ChatRequest request);

    @POST @Path("/generate")            // warm-up: model named, no prompt → preloads
    GenerateResponse warmUp(WarmUpRequest request);

    // records: ChatRequest(model, messages, format(JsonNode), options, stream, keepAlive), etc.
}
```

Read timeout must cover **cold start + inference** (load 2B ≈ 2–4 s + a few s inference) → ~30 s, like the screenshot sidecar's 20 s but with more headroom for the load race.

### `BookmarkAutoTagLlmService` (`@Service`, orchestration only)

Stateless — no persistence. Given a collection id + the text to classify (title/URL/description):

- Loads the collection's `Tag` vocabulary (via `TagRepo`), builds the enum schema, calls `OllamaClient`, maps returned names → `Tag` ids, **re-validates** against the vocabulary, and **returns** the matching tags to the resource.
- No auth, no HTTP-status branching that leaks to the caller. On any client failure (model down, timeout): log + return empty (best-effort, BR-077). The endpoint then simply yields no AI chips.
- Swappable behind an interface (`LlmTaggingClient`) so the service is unit-testable without a running model.
- Optional in-memory cache (e.g. Caffeine) keyed by `(collectionId, normalized text, vocab hash)` with a short TTL, to coalesce repeated opens of the same bookmark without a DB.

### `BookmarkAutoTagResource` (`@JaxResource`)

- `POST /collections/{cid}/bookmarks/{bid}/suggest-tags` → on-demand suggestions for a saved bookmark (server reads its title/URL/description). Returns the constrained tag list as DTOs.
- `POST /collections/{cid}/suggest-tags` → on-demand suggestions for unsaved compose-form text (`{title, url, description}` in the body), so suggestions work before the bookmark exists.
- `POST /collections/{cid}/autotag/warm-up` → optional warm-up ping when the add-bookmark form opens.
- Accepting a suggestion uses the **existing** tag-apply endpoint (UC-019) — there's no accept/dismiss endpoint here, because nothing about a suggestion is persisted.
- Every method: `authorizationService.requireCollectionAccess(cid)` first.

### `ConfigService` + master switch

`isAutoTagLlmEnabled()` (`linkweave.autotag.llm.enabled`, **FR-096**) gates the suggest endpoints and warm-up: when off, no model calls are made (no warm-up, no inference) and the suggest endpoints return an empty list / 204. The feature then **falls back to rule-based auto-tagging** (UC-045) with zero degradation — rules run entirely client-side and are independent of this switch, so the "Suggested Tags" section keeps showing rule chips. This also lets the whole Ollama container be left out of a deployment (no memory budget, model unavailable, or a rollback). Model name, keep-alive, timeout, and max-vocab exposed via config (see below).

---

## Data flow

### Suggestion flow (on-demand, synchronous)

```
UI opens Suggested Tags ──▶ POST suggest-tags ──▶ requireCollectionAccess
                                                       │
                                       load collection tags (enum vocab)
                                                       ▼
                              OllamaClient.chat(constrained schema)  ◀── loads model if idle
                                                       ▼
                       map + re-validate names → Tag DTOs ──▶ response
   (frontend renders rule chips instantly; AI chips fold in when this returns)
```

### Accept flow (no new endpoint)

```
user accepts an AI chip ──▶ existing tag-apply endpoint (UC-019) ──▶ Tag added to Bookmark.tags
(dismiss = the user simply doesn't accept; nothing is persisted)
```

### Warm-up flow

```
user opens "Add bookmark" ──▶ POST /autotag/warm-up ──▶ OllamaClient.warmUp(keep_alive) ──▶ model resident before first real call
```

---

## Configuration reference

```properties
# Master switch + provider selection
linkweave.autotag.llm.enabled=true
linkweave.autotag.provider=ollama                 # ollama (local) | openai (hosted)
linkweave.autotag.max-vocab=0                     # cap vocabulary size; 0 = no cap

# --- Ollama (provider = ollama) ---
linkweave.autotag.model=gemma2:2b
linkweave.autotag.keep-alive=15m                  # 0 = unload each call, -1 = always resident
linkweave.autotag.service-url=http://localhost:11434
quarkus.rest-client.ollama.url=${linkweave.autotag.service-url}
quarkus.rest-client.ollama.connect-timeout=3000
quarkus.rest-client.ollama.read-timeout=30000     # cold-start load + inference

# --- OpenAI-compatible hosted provider (provider = openai), e.g. z.ai GLM ---
linkweave.autotag.openai.model=glm-4.6
linkweave.autotag.openai.api-key=${LINKWEAVE_AUTOTAG_API_KEY:}   # secret, from env
linkweave.autotag.openai.base-url=https://api.z.ai/api/paas/v4
quarkus.rest-client.openai-autotag.url=${linkweave.autotag.openai.base-url}
quarkus.rest-client.openai-autotag.connect-timeout=3000
quarkus.rest-client.openai-autotag.read-timeout=30000

# In tests the real clients are never reached — a fake LlmTaggingClient is
# installed per-test via QuarkusMock.installMockForType(...). The flag is left at
# its default; the disabled-path test opts out with a QuarkusTestProfile.
```

---

## Tests

### Backend
- `BookmarkAutoTagLlmServiceTest` — mock `LlmTaggingClient`; assert returned names map to `Tag` ids and **out-of-vocabulary names are dropped**.
- `BookmarkAutoTagResourceITest` — `@TestSecurity`; assert auth is enforced (403 on no access) and the endpoint returns the constrained tag DTOs (no persistence to assert — nothing is stored).
- Fake `LlmTaggingClient` (installed via `QuarkusMock.installMockForType`): assert out-of-vocabulary names are dropped, the full collection vocabulary is offered to the model, and a model failure surfaces as **an empty suggestion list and no thrown error** (BR-077). (WireMock isn't a project dependency, so the seam is the interface rather than the HTTP layer.)
- Feature-flag off (FR-096, via a `QuarkusTestProfile`): assert the service returns empty and makes **no** call to the model — the rule-based fallback path.
- ArchUnit: service/repo never inject `AuthorizationService` or `OllamaClient` for auth; resource always guards.

### Frontend
- `auto-tag-rules.ts` merge test: given built-in + custom rule matches and LLM rows, assert the final list is ordered **built-in → custom → LLM** and de-duplicated by tag (a tag from two sources appears once, attributed to the highest-priority source).
- Component test for the "Suggested Tags" section: toggles, accept, empty/unavailable states, per-chip source label, AI chips loading async after rule chips.
- E2E (mock backend): LLM suggestion appears when the section opens, accept applies the tag, source label distinguishes AI from rule-based.

---

## Decisions (resolved)

1. **Config location → app config / env only.** `keep-alive`, model, and the master switch are `linkweave.autotag.*` properties (env-overridable), no admin UI. Matches the absence of an admin role today; revisit if/when one exists. UC-097 BR-081 to be reworded from "administrator" to "operator/config."
2. **Model provisioning → image entrypoint.** Our `ollama/Dockerfile` (`FROM ollama/ollama`) ships an entrypoint that `ollama serve` + `ollama pull gemma2:2b`; weights persist in the `ollama-models` volume. The API never pulls.
3. **Traceability → FR added.** Backed by **FR-095** in `docs/requirements.md`; UC-097 "Maps to" updated from the erroneous FR-052 (which is "Disable Offline Caching") to **FR-095**.
4. **Suggestion scope → rules stay on the frontend, merged in the UI.** The existing client-side rule engine is unchanged; the "Suggested Tags" component merges live-computed rule matches with on-demand LLM suggestions in a fixed hierarchy — **built-in rules → custom rules → LLM** — de-duplicated by tag. No backend rule engine. See *Combining with rule-based suggestions* above.
5. **Persistence → none.** Suggestions are computed on demand and not stored; only accepted tags persist (via `Bookmark.tags`). No entity, migration, status machine, or background job. A `(bookmark_id, tag_id)` dismissals-only record is a possible later add-on *only if* reappearing rejected suggestions prove annoying. See *Data model — none* above.

---

## Critical files (quick index)

### Backend files (implemented)
- `api/.../autotag/llm/LlmTaggingClient.java` — provider-agnostic interface (mockable seam)
- `api/.../autotag/llm/LlmTaggingClientImpl.java` — dispatches on `provider` to Ollama or OpenAI-compatible
- `api/.../autotag/llm/OllamaClient.java` — Ollama REST client (pattern: `ScreenshotSidecarClient`)
- `api/.../autotag/llm/OpenAiClient.java` — OpenAI-compatible REST client (z.ai etc.)
- `api/.../autotag/llm/BookmarkAutoTagLlmService.java` — stateless orchestration, `@NoTransactionService`
- `api/.../autotag/BookmarkAutoTagResource.java` — `suggest-tags` + `warm-up` endpoints
- `api/.../autotag/json/SuggestTagsJson.java` — compose-form request body
- `ollama/Dockerfile` + `ollama/entrypoint.sh` (pull gemma2:2b) + `ollama/docker-compose.yml`

### Existing files modified
- `application.properties` — config block above
- `ConfigService` — `isAutotagLlmEnabled()`, `isAutotagProviderOpenAi()`, model, keep-alive, max-vocab, openai model/api-key accessors

### Frontend files (not yet implemented — awaiting design input)
- `frontend/src/lib/auto-tag-rules.ts` / `useTagSuggestions` — append on-demand LLM suggestions after built-in + custom rules, dedup by tag (built-in → custom → LLM)
- "Suggested Tags" component — call `suggest-tags`, merge with live rule chips, per-chip source label (rule vs. AI); accept uses the existing tag-apply path

### Reference files (patterns to follow)
- `screenshot-service/docker-compose.yml` — companion container shape
- `api/.../screenshot/ScreenshotSidecarClient.java` — REST client
- `docs/plans/screenshot-previews.md` — end-to-end sidecar plan
- `docs/plans/auto-tag-custom-rules.md` — rule-based sibling (client-side rule engine)

## Verification

`cd api && ./mvnw verify` (backend), `cd frontend && pnpm run check` (frontend gate). Manually: bring up the `ollama` compose service, open a bookmark in a collection with existing tags, confirm AI suggestions appear in the Suggested Tags section (cold first call slower, warm calls fast), accept one and confirm the tag is applied, and confirm the model unloads after the keep-alive window (`ollama ps` shows it gone).
