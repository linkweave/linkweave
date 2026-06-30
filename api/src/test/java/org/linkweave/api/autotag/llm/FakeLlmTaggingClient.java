package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public final AtomicBoolean suggestCalled = new AtomicBoolean(false);
    public final AtomicBoolean warmUpCalled = new AtomicBoolean(false);

    public FakeLlmTaggingClient() {
        super(null, null, null);
    }

    public void reset() {
        namesToReturn = List.of();
        lastVocabulary = null;
        suggestCalled.set(false);
        warmUpCalled.set(false);
    }

    @Override
    public @NonNull List<String> suggest(@NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        suggestCalled.set(true);
        lastVocabulary = vocabulary;
        return namesToReturn;
    }

    @Override
    public void warmUp() {
        warmUpCalled.set(true);
    }
}
