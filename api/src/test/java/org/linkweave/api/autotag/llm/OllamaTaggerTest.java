package org.linkweave.api.autotag.llm;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.SCOPE;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.chatClient;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.newBreaker;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.pullClient;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.testConfig;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.warmUpClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Test;
import org.linkweave.api.autotag.llm.AutotagTestSupport.ChatState;
import org.linkweave.api.autotag.llm.AutotagTestSupport.PullState;
import org.linkweave.api.shared.config.ConfigService;

/**
 * {@link OllamaTagger}'s cold-start pull guard and its failure handling
 * (UC-108 BR-108-2).
 *
 * <p>The pull's read-timeout is minutes, so it must run off the request thread: a
 * cold suggestion schedules the pull on the {@link ManagedExecutor} and reports
 * {@code PREPARING} instead of blocking the caller, and only once the model is
 * resident are suggestions served.
 *
 * <p>The other half of these tests is about what a failure is allowed to cost.
 * Downloading 1.6 GB of weights is the right response to exactly one signal —
 * the server saying the model is not installed — and the wrong response to a
 * timeout, which says the host is already too busy to answer.
 *
 * <p>Exercised through {@link CircuitBreakingLlmTagger}, because the mapping from
 * a thrown call to a {@link SuggestionOutcome}, and the callback that re-arms the
 * pull, both live there. Testing the tagger bare would split one behaviour across
 * two assertions and weaken the regression.
 */
class OllamaTaggerTest {

    @Test
    void shouldRunPullOffRequestThreadAndFastFailWhileCold() throws Exception {
        // ARRANGE
        PullState pull = new PullState(true);
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(pull), chatClient(chat));

        // ACT — the cold suggest returns immediately and schedules the (blocking)
        // pull on the executor, so the request thread is never pinned.
        LlmTaggingClient.Result firstCold = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(firstCold.tagNames())
            .as("a cold suggest fast-fails instead of blocking on the pull")
            .isEmpty();
        Assertions.assertThat(firstCold.outcome())
            .as("the dialog is told the model is being prepared, not that it found nothing")
            .isEqualTo(SuggestionOutcome.PREPARING);
        Assertions.assertThat(pull.entered.await(2, SECONDS))
            .as("the pull runs in the background, off the request thread")
            .isTrue();

        // A concurrent caller, while the pull is still downloading, also fast-fails
        // and never invokes the model.
        Assertions.assertThat(client.suggest(List.of("rust"), "content", SCOPE).tagNames()).isEmpty();
        Assertions.assertThat(chat.count.get())
            .as("the model must not be invoked while cold")
            .isZero();

        // Release the cold-start pull; once the model is resident, suggestions are
        // served and the model is pulled exactly once.
        pull.release.countDown();
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get())
            .as("model pulled exactly once")
            .isOne();
    }

    @Test
    void shouldPullOnceThenServeSubsequentSuggestions() throws Exception {
        // ARRANGE
        PullState pull = new PullState(false);
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(pull), chatClient(chat));

        // ACT — the first cold suggest schedules the pull; wait until the model is warm.
        Assertions.assertThat(client.suggest(List.of("rust"), "content", SCOPE).tagNames()).isEmpty();
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // Once warm, every suggestion is served directly without re-pulling.
        int chatBefore = chat.count.get();
        client.suggest(List.of("rust"), "content", SCOPE);
        client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(chat.count.get())
            .as("chat served on every warm suggestion")
            .isEqualTo(chatBefore + 2);
        Assertions.assertThat(pull.count.get())
            .as("model pulled only once")
            .isOne();
    }

    @Test
    void shouldNotRePullWhenTheModelTimesOut() throws Exception {
        // ARRANGE — warm the model so suggestions are served.
        PullState pull = new PullState(false);
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get()).isOne();

        // ACT — the model stops answering in time. This is the exact failure that
        // filled the 2026-08-28 log, where it was misread as a missing model and
        // queued a re-download onto an already-overloaded host.
        chat.failure = () -> new RuntimeException(
            "The timeout period of 8000ms has been exceeded while executing POST /api/chat "
            + "for server ollama:11434");
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(result.outcome())
            .as("a timeout is reported as a timeout, not as an empty answer")
            .isEqualTo(SuggestionOutcome.TIMEOUT);
        Assertions.assertThat(result.tagNames()).isEmpty();
        Thread.sleep(150); // any pull would be scheduled asynchronously by now
        Assertions.assertThat(pull.count.get())
            .as("a timeout says the host is busy, not that the weights are gone (BR-108-2)")
            .isOne();
    }

    @Test
    void shouldNotRePullWhenTheServiceIsUnreachable() throws Exception {
        // ARRANGE
        PullState pull = new PullState(false);
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // ACT — the Ollama container is down.
        chat.failure = () -> new RuntimeException(
            new java.net.ConnectException("Connection refused"));
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
        Thread.sleep(150);
        Assertions.assertThat(pull.count.get())
            .as("there is nothing listening to download from (A3)")
            .isOne();
    }

    @Test
    void shouldRePullOnlyWhenTheServiceSaysTheModelIsMissing() throws Exception {
        // ARRANGE — warm the model so suggestions are served.
        PullState pull = new PullState(false);
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get()).isOne();

        // ACT — Ollama loses the weights (volume cleared / model deleted) and says
        // so explicitly: 404 with its "not found, try pulling it first" body.
        chat.failure = () -> new WebApplicationException(
            Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"model 'gemma2:2b' not found, try pulling it first\"}")
                .build());
        client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT — this, and only this, re-arms the pull, so once the model is back
        // the next suggestion serves again rather than failing until a restart.
        chat.failure = null;
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get())
            .as("model is re-pulled after the service reports it missing")
            .isEqualTo(2);
    }

    /**
     * An unbounded {@code tags} array is what let one bookmark take auto-tagging
     * down: the grammar never forces the closing bracket, so a model at
     * temperature 0 that starts repeating tags generates until the context is
     * full. That reaches the caller as a read timeout, and three of those open
     * the breaker (BR-108-4). Measured over 61 real bookmarks before the bound,
     * gemma2:2b timed out on 10 and repeated tags on 38 of the rest.
     */
    @Test
    void shouldBoundTheOllamaResponseSchemaToPreventRunawayGeneration() throws Exception {
        // ARRANGE
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(new PullState(false)), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // ACT
        client.suggest(List.of("rust", "databases", "career"), "Async Rust blog", SCOPE);

        // ASSERT
        Assertions.assertThat(tagsSchemaOf(chat.lastRequest))
            .as("the array is bounded, and pinned to the vocabulary")
            .containsEntry("uniqueItems", true)
            .containsEntry("maxItems", 3)
            .containsEntry("items", Map.of(
                "type", "string", "enum", List.of("rust", "databases", "career")));
    }

    /**
     * The enum is the only place the vocabulary belongs on this path. Listing it
     * in the prompt as well, which {@link OpenAiTagger} has to do because its
     * provider may ignore the schema, measurably hurt: telling the model how much
     * of the list is on offer makes it use more of it.
     */
    @Test
    void shouldKeepTheOllamaPromptFreeOfTheVocabulary() throws Exception {
        // ARRANGE
        ChatState chat = new ChatState();
        LlmTaggingClient client = newOllama(pullClient(new PullState(false)), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // ACT
        client.suggest(List.of("rust", "databases", "career"), "Async Rust blog", SCOPE);

        // ASSERT
        List<OllamaClient.Message> messages = chat.lastRequest.messages();
        Assertions.assertThat(messages).hasSize(2);
        Assertions.assertThat(messages.get(0).role()).isEqualTo("system");
        Assertions.assertThat(messages.get(0).content())
            .as("the vocabulary is carried by the schema enum, not by the prompt")
            .doesNotContain("databases", "career", "Allowed tags");
        Assertions.assertThat(messages.get(1).content())
            .as("the bookmark itself stays in the user message")
            .isEqualTo("Async Rust blog");
    }

    // --- helpers ---

    private static LlmTaggingClient newOllama(OllamaPullClient pull, OllamaClient chat) {
        ConfigService config = testConfig(false, Optional.empty(), "glm-4.6");
        OllamaTagger tagger = new OllamaTagger(
            config, chat, pull, warmUpClient(), new ObjectMapper(), ManagedExecutor.builder().build());
        return new CircuitBreakingLlmTagger(tagger, newBreaker(config));
    }

    /** Digs the {@code tags} sub-schema out of the request's untyped `format`. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> tagsSchemaOf(OllamaClient.ChatRequest request) {
        Map<String, Object> format = (Map<String, Object>) request.format();
        Map<String, Object> properties = (Map<String, Object>) format.get("properties");
        return (Map<String, Object>) properties.get("tags");
    }

    /** Polls {@code suggest} until the background pull has made the model resident. */
    private static List<String> awaitServed(LlmTaggingClient client) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<String> served = client.suggest(List.of("rust"), "content", SCOPE).tagNames();
            if (!served.isEmpty()) {
                return served;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("model never became warm");
    }
}
