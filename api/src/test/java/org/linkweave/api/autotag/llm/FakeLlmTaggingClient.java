package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Test double for the LLM client. Extends {@link LlmTaggingClientImpl} (rather
 * than only implementing {@link LlmTaggingClient}) so it is assignable to the
 * resolved bean type and can be installed via
 * {@code QuarkusMock.installMockForType(fake, LlmTaggingClient.class)}. The
 * overrides never touch the REST clients, so the {@code null} super-constructor
 * args are harmless. Tests set {@link #namesToReturn} and inspect the call state.
 */
public class FakeLlmTaggingClient extends LlmTaggingClientImpl {

    public volatile List<String> namesToReturn = List.of();
    public volatile @Nullable List<String> lastVocabulary = null;
    public volatile @Nullable String lastScope = null;
    public final AtomicBoolean suggestCalled = new AtomicBoolean(false);
    public final AtomicBoolean warmUpCalled = new AtomicBoolean(false);

    /** Outcome to report; {@code null} derives it from {@link #namesToReturn}. */
    public volatile @Nullable SuggestionOutcome outcomeToReturn = null;

    /**
     * When set, {@link #suggest} blocks on this latch — the stalled-model stub
     * UC-108's acceptance criteria are written against.
     */
    public volatile @Nullable CountDownLatch stallUntil = null;

    /** Counts callers currently blocked inside {@link #suggest}. */
    public final AtomicInteger inFlight = new AtomicInteger(0);

    public FakeLlmTaggingClient() {
        super(null, null, null, null);
    }

    public void reset() {
        namesToReturn = List.of();
        lastVocabulary = null;
        lastScope = null;
        outcomeToReturn = null;
        stallUntil = null;
        inFlight.set(0);
        suggestCalled.set(false);
        warmUpCalled.set(false);
    }

    @Override
    public @NonNull Result suggest(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent, @NonNull String scope) {
        suggestCalled.set(true);
        lastVocabulary = vocabulary;
        lastScope = scope;
        CountDownLatch stall = stallUntil;
        if (stall != null) {
            inFlight.incrementAndGet();
            try {
                stall.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
        }
        SuggestionOutcome outcome = outcomeToReturn;
        return outcome == null ? Result.of(namesToReturn) : Result.failed(outcome);
    }

    @Override
    public void warmUp() {
        warmUpCalled.set(true);
    }
}
