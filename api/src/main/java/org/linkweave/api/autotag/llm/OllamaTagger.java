package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;

/**
 * Tag suggestion through a local Ollama server (FR-095).
 *
 * <p>Unlike a hosted provider, this one owns a model lifecycle: the stock Ollama
 * image ships no weights, so the configured model has to be pulled once before
 * any suggestion can be served, and a failed call may say something about
 * whether those weights are still there. All of that state lives here, where it
 * is reachable only when this provider is selected.
 */
@Slf4j
class OllamaTagger extends AbstractLlmTagger {

    private final ConfigService config;
    private final OllamaClient ollamaClient;
    private final OllamaPullClient ollamaPullClient;
    private final OllamaWarmUpClient ollamaWarmUpClient;
    private final ManagedExecutor managedExecutor;

    /** Set once the configured model has been pulled successfully this run. */
    private final AtomicBoolean modelPulled = new AtomicBoolean(false);

    /**
     * Held while a pull is downloading. Lets concurrent callers detect an
     * in-progress pull and fast-fail instead of blocking on the monitor.
     */
    private final AtomicBoolean pullInProgress = new AtomicBoolean(false);

    /** Epoch millis before which no new pull may start (BR-108-6). */
    private final AtomicLong nextPullAllowedEpochMs = new AtomicLong(0);

    /** Current pull backoff, doubling per failed pull up to the configured ceiling. */
    private final AtomicLong pullBackoffMs = new AtomicLong(0);

    OllamaTagger(
        @NonNull ConfigService config,
        @NonNull OllamaClient ollamaClient,
        @NonNull OllamaPullClient ollamaPullClient,
        @NonNull OllamaWarmUpClient ollamaWarmUpClient,
        @NonNull ObjectMapper objectMapper,
        @NonNull ManagedExecutor managedExecutor) {
        super(objectMapper);
        this.config = config;
        this.ollamaClient = ollamaClient;
        this.ollamaPullClient = ollamaPullClient;
        this.ollamaWarmUpClient = ollamaWarmUpClient;
        this.managedExecutor = managedExecutor;
    }

    @Override
    public @NonNull Result suggest(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent, @NonNull String scope) {
        if (!modelPulled.get()) {
            triggerPullAsync();
            LOG.debug("Ollama model not warm yet; scheduled pull and skipping this suggestion");
            return Result.failed(SuggestionOutcome.PREPARING);
        }
        OllamaClient.ChatRequest request = new OllamaClient.ChatRequest(
            config.getAutotagModel(),
            List.of(
                new OllamaClient.Message("system", SYSTEM_PROMPT),
                new OllamaClient.Message("user", bookmarkContent)),
            ollamaConstrainedFormat(vocabulary, config.getAutotagMaxTags()),
            new OllamaClient.Options(0.0),
            false,
            config.getAutotagKeepAlive());

        logModelRequest("Ollama", request);
        OllamaClient.ChatResponse response = ollamaClient.chat(request);
        if (response.message() == null || response.message().content() == null) {
            LOG.debug("Ollama chat response had no message content");
            return Result.of(List.of());
        }
        LOG.debug("Ollama chat response content: {}", response.message().content());
        return Result.of(parseTags(response.message().content()));
    }

    @Override
    public void warmUp() {
        if (!ensureModelPulled()) {
            return;
        }
        try {
            ollamaWarmUpClient.generate(new OllamaWarmUpClient.GenerateRequest(
                config.getAutotagModel(), config.getAutotagKeepAlive()));
        } catch (RuntimeException e) {
            reactToFailure("warm-up generate", LlmFailure.classify(e), e);
            throw e;
        }
    }

    @Override
    void onCallFailed(@NonNull LlmFailure failure, @NonNull RuntimeException cause) {
        reactToFailure("chat", failure, cause);
    }

    /**
     * Reacts to a failed model call.
     *
     * <p>The one rule that matters here (BR-108-2): only an explicit "model not
     * installed" answer clears {@link #modelPulled} and schedules a pull.
     * Reacting to <em>any</em> {@link RuntimeException} queues a multi-gigabyte
     * download onto a host that a read timeout says is already too loaded to
     * answer, which makes the next timeout likelier. That loop is the repeating
     * warning in the 2026-08-28 log.
     */
    private void reactToFailure(
        @NonNull String operation, @NonNull LlmFailure failure, @NonNull RuntimeException cause) {
        LOG.debug("Ollama {} failed ({}): {}", operation, failure, cause.getMessage());
        if (!failure.warrantsPull()) {
            return;
        }
        if (modelPulled.compareAndSet(true, false)) {
            LOG.warn("Ollama reports model '{}' is not installed; scheduling a pull",
                config.getAutotagModel());
        }
        triggerPullAsync();
    }

    /**
     * Pulls the configured model into Ollama on first use (FR-095). The stock
     * Ollama image ships no weights, so the first warm-up triggers the
     * download — slow once, then cached in the {@code ollama-models} volume.
     *
     * <p>Blocks the calling thread for the download's duration (read-timeout 10
     * minutes, {@code quarkus.rest-client.ollama-pull.read-timeout}), so it must
     * only be invoked off the request path: {@link #warmUp} runs on the service's
     * {@link ManagedExecutor}, and {@link #suggest} schedules it via
     * {@link #triggerPullAsync} rather than calling it inline.
     *
     * <p>Returns {@code true} when the model is ready. Only one thread runs the
     * pull at a time; a concurrent caller fails the {@link #pullInProgress}
     * {@code compareAndSet} and fast-fails (returns {@code false}) instead of
     * serializing on a monitor. A pull that fails backs off exponentially
     * (BR-108-6) rather than retrying on the next suggestion, so a broken Ollama
     * cannot be asked to download 1.6 GB once per bookmark.
     */
    private boolean ensureModelPulled() {
        if (modelPulled.get()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (nextPullAllowedEpochMs.get() > now) {
            LOG.debug("Ollama model pull is backing off; skipping");
            return false;
        }
        if (!pullInProgress.compareAndSet(false, true)) {
            LOG.debug("Ollama model pull already in progress; skipping suggestion");
            return false;
        }
        try {
            if (modelPulled.get()) {
                return true;
            }
            String model = config.getAutotagModel();
            LOG.info("Ensuring Ollama model '{}' is pulled; first run may take several minutes...", model);
            ollamaPullClient.pull(new OllamaPullClient.PullRequest(model, false));
            modelPulled.set(true);
            pullBackoffMs.set(0);
            nextPullAllowedEpochMs.set(0);
            LOG.info("Ollama model '{}' is available", model);
            return true;
        } catch (RuntimeException e) {
            backOffPull(e);
            return false;
        } finally {
            pullInProgress.set(false);
        }
    }

    /**
     * Doubles the pull backoff up to its ceiling after a failed download, logging
     * once at WARN because a failed pull is a state change an operator wants to
     * see (BR-108-8).
     */
    private void backOffPull(@NonNull RuntimeException cause) {
        long base = config.getAutotagPullMinInterval().toMillis();
        long ceiling = config.getAutotagPullMinIntervalMax().toMillis();
        long previous = pullBackoffMs.get();
        long next = Math.min(previous == 0 ? base : previous * 2, ceiling);
        pullBackoffMs.set(next);
        nextPullAllowedEpochMs.set(System.currentTimeMillis() + next);
        LOG.warn("Ollama model pull for '{}' failed ({}); next attempt in {}s",
            config.getAutotagModel(), cause.getMessage(), next / 1000);
    }

    /**
     * Kicks off the cold-start pull on the {@link ManagedExecutor} so the request
     * thread is never pinned by the download. Deduped by the {@link #pullInProgress}
     * CAS and rate-limited by the backoff in {@link #ensureModelPulled}.
     */
    private void triggerPullAsync() {
        managedExecutor.execute(() -> {
            try {
                ensureModelPulled();
            } catch (RuntimeException e) {
                LOG.debug("Background Ollama model pull failed: {}", e.getMessage());
            }
        });
    }

    /**
     * JSON Schema pinning {@code tags} to the allowed enum — Ollama honors this
     * strictly.
     *
     * <p>{@code maxItems} is load-bearing, not tidiness. Without a bound the
     * grammar accepts an arbitrarily long array, so a model at temperature 0 that
     * starts repeating tags never has to emit the closing bracket and generates
     * until it exhausts the context window. The caller sees that as a read
     * timeout, and three in a row open the circuit breaker (BR-108-4), which is
     * how one runaway bookmark takes auto-tagging down for the whole cooldown.
     * Measured on two separate collections, the unbounded schema hung on 16% and
     * 26% of real bookmarks respectively; bounded, on none.
     *
     * <p>The bound also carries most of the precision: gemma2:2b fills whatever
     * ceiling it is given, and no bookmark in either collection legitimately
     * carries more than three tags.
     *
     * <p>{@code uniqueItems} is declared intent only — Ollama does not currently
     * enforce it, and duplicates still arrive. They are harmless because
     * {@link BookmarkAutoTagLlmService} de-dupes, but they do consume slots
     * against {@code maxItems}.
     *
     * <p>Deliberately <em>not</em> done here: naming the vocabulary in the
     * prompt, the way {@link OpenAiTagger} must because its provider may ignore
     * the schema. Tried and measured, it tells the model how much of the list is
     * available and it duly uses more of it, costing precision on both
     * collections (f1 0.850 to 0.698 on one, 0.653 to 0.537 on the other). The
     * enum alone is the better instruction here.
     */
    private static @NonNull Map<String, Object> ollamaConstrainedFormat(
        @NonNull List<String> vocabulary, int maxTags) {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tags", Map.of(
                    "type", "array",
                    "uniqueItems", true,
                    "maxItems", maxTags,
                    "items", Map.of(
                        "type", "string",
                        "enum", vocabulary))),
            "required", List.of("tags"));
    }
}
