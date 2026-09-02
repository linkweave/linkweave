package org.linkweave.api.autotag.llm;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Test;
import org.linkweave.api.shared.config.ConfigService;

/**
 * Unit tests for {@link LlmTaggingClientImpl}'s cold-start pull guard and its
 * failure handling (UC-108 BR-108-2).
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
 * <p>The Ollama REST clients are faked with JDK dynamic proxies rather than
 * concrete classes: {@link OllamaClient}/{@link OllamaPullClient} carry
 * {@code @Path}, so an indexed test class implementing them would be picked up
 * as a JAX-RS resource during {@code @QuarkusTest} augmentation. Proxies are
 * runtime objects, invisible to the static index.
 */
class LlmTaggingClientImplTest {

    @Test
    void shouldRunPullOffRequestThreadAndFastFailWhileCold() throws Exception {
        // ARRANGE
        PullState pull = new PullState(true);
        ChatState chat = new ChatState();
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));

        // ACT — the cold suggest returns immediately and schedules the (blocking)
        // pull on the executor, so the request thread is never pinned.
        LlmTaggingClient.Result firstCold = client.suggest(List.of("rust"), "content");

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
        Assertions.assertThat(client.suggest(List.of("rust"), "content").tagNames()).isEmpty();
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
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));

        // ACT — the first cold suggest schedules the pull; wait until the model is warm.
        Assertions.assertThat(client.suggest(List.of("rust"), "content").tagNames()).isEmpty();
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // Once warm, every suggestion is served directly without re-pulling.
        int chatBefore = chat.count.get();
        client.suggest(List.of("rust"), "content");
        client.suggest(List.of("rust"), "content");

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
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get()).isOne();

        // ACT — the model stops answering in time. This is the exact failure that
        // filled the 2026-08-28 log, where it was misread as a missing model and
        // queued a re-download onto an already-overloaded host.
        chat.failure = () -> new RuntimeException(
            "The timeout period of 8000ms has been exceeded while executing POST /api/chat "
            + "for server ollama:11434");
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content");

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
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");

        // ACT — the Ollama container is down.
        chat.failure = () -> new RuntimeException(
            new java.net.ConnectException("Connection refused"));
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content");

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
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get()).isOne();

        // ACT — Ollama loses the weights (volume cleared / model deleted) and says
        // so explicitly: 404 with its "not found, try pulling it first" body.
        chat.failure = () -> new WebApplicationException(
            Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"model 'gemma2:2b' not found, try pulling it first\"}")
                .build());
        client.suggest(List.of("rust"), "content");

        // ASSERT — this, and only this, re-arms the pull, so once the model is back
        // the next suggestion serves again rather than failing until a restart.
        chat.failure = null;
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get())
            .as("model is re-pulled after the service reports it missing")
            .isEqualTo(2);
    }

    @Test
    void shouldReturnTagsFromHostedProviderWithBearerHeaderAndPrompt() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        openAi.response = new OpenAiClient.ChatCompletionResponse(List.of(
            new OpenAiClient.Choice(
                new OpenAiClient.Message("assistant", "{\"tags\":[\"rust\",\"databases\"]}"))));
        LlmTaggingClientImpl client = newOpenAiClient(openAi, Optional.of("test-key"), "glm-4.6");

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust", "databases", "career"), "Async Rust blog");

        // ASSERT
        Assertions.assertThat(result.tagNames())
            .as("tags parsed from the hosted provider's choices")
            .containsExactly("rust", "databases");
        Assertions.assertThat(openAi.count.get())
            .as("the hosted provider is called exactly once")
            .isOne();
        Assertions.assertThat(openAi.authorization)
            .as("the API key is sent as a Bearer token")
            .isEqualTo("Bearer test-key");
        Assertions.assertThat(openAi.lastRequest.model())
            .as("the configured hosted model is used")
            .isEqualTo("glm-4.6");
        Assertions.assertThat(openAi.lastRequest.messages()).hasSize(2);
        Assertions.assertThat(openAi.lastRequest.messages().get(1).content())
            .as("the allowed vocabulary and JSON contract are stated in the prompt")
            .contains("Allowed tags", "rust", "databases", "career");
    }

    @Test
    void shouldReturnEmptyAndNotCallProviderWhenApiKeyIsBlank() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        LlmTaggingClientImpl client = newOpenAiClient(openAi, Optional.empty(), "glm-4.6");

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content");

        // ASSERT
        Assertions.assertThat(result.tagNames())
            .as("a missing API key fast-fails with an empty list")
            .isEmpty();
        Assertions.assertThat(result.outcome())
            .as("an unconfigured provider is reported as disabled, not as a failure")
            .isEqualTo(SuggestionOutcome.DISABLED);
        Assertions.assertThat(openAi.count.get())
            .as("the provider must not be called without an API key")
            .isZero();
    }

    @Test
    void shouldReturnEmptyWhenHostedResponseHasNoUsableContent() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        LlmTaggingClientImpl client = newOpenAiClient(openAi, Optional.of("k"), "glm-4.6");

        // ACT / ASSERT — each degenerate response yields an empty list, never throwing
        assertEmptyFor(openAi, client, new OpenAiClient.ChatCompletionResponse(null));
        assertEmptyFor(openAi, client, new OpenAiClient.ChatCompletionResponse(List.of()));
        assertEmptyFor(openAi, client, new OpenAiClient.ChatCompletionResponse(
            List.of(new OpenAiClient.Choice(null))));
    }

    // --- helpers ---

    /**
     * Polls {@code suggest} until the background pull has made the model resident.
     */
    private static List<String> awaitServed(LlmTaggingClientImpl client) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<String> served = client.suggest(List.of("rust"), "content").tagNames();
            if (!served.isEmpty()) {
                return served;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("model never became warm");
    }

    private static LlmTaggingClientImpl newClient(OllamaPullClient pull, OllamaClient chat) {
        ConfigService config = testConfig(false, Optional.empty(), "glm-4.6");
        LlmTaggingClientImpl client = new LlmTaggingClientImpl(
            config, new ObjectMapper(), ManagedExecutor.builder().build(), newBreaker(config));
        client.ollamaPullClient = pull;
        client.ollamaClient = chat;
        client.ollamaWarmUpClient = warmUpClient();
        return client;
    }

    private static LlmTaggingClientImpl newOpenAiClient(OpenAiState openAi, Optional<String> apiKey, String model) {
        ConfigService config = testConfig(true, apiKey, model);
        LlmTaggingClientImpl client = new LlmTaggingClientImpl(
            config, new ObjectMapper(), ManagedExecutor.builder().build(), newBreaker(config));
        client.openAiClient = openAiClient(openAi);
        return client;
    }

    /**
     * A real {@link LlmCircuitBreaker} rather than a stub: these tests care that a
     * failure does not trigger a download, and the breaker sits on that path.
     * {@code init()} is called by hand because {@code @PostConstruct} only runs
     * under CDI.
     */
    private static LlmCircuitBreaker newBreaker(ConfigService config) {
        LlmCircuitBreaker breaker = new LlmCircuitBreaker(config, new SimpleMeterRegistry());
        breaker.init();
        return breaker;
    }

    /**
     * Anonymous {@link ConfigService} subclass: the real one is populated by
     * MicroProfile Config injection, so a bare instance has null Durations that
     * the breaker would trip over on construction.
     */
    private static ConfigService testConfig(boolean openAi, Optional<String> apiKey, String openAiModel) {
        return new ConfigService() {
            @Override
            public boolean isAutotagProviderOpenAi() {
                return openAi;
            }

            @Override
            public String getAutotagModel() {
                return "gemma2:2b";
            }

            @Override
            public String getAutotagKeepAlive() {
                return "15m";
            }

            @Override
            public Optional<String> getAutotagOpenAiApiKey() {
                return apiKey;
            }

            @Override
            public String getAutotagOpenAiModel() {
                return openAiModel;
            }

            @Override
            public int getAutotagSuggestTimeoutMs() {
                return 8000;
            }

            @Override
            public int getAutotagCircuitFailureThreshold() {
                return 3;
            }

            @Override
            public Duration getAutotagCircuitCooldown() {
                return Duration.ofSeconds(30);
            }

            @Override
            public Duration getAutotagCircuitCooldownMax() {
                return Duration.ofMinutes(10);
            }

            @Override
            public int getAutotagMaxConcurrent() {
                return 2;
            }

            @Override
            public Duration getAutotagPullMinInterval() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration getAutotagPullMinIntervalMax() {
                return Duration.ofHours(1);
            }
        };
    }

    private static void assertEmptyFor(
        OpenAiState openAi, LlmTaggingClientImpl client, OpenAiClient.ChatCompletionResponse response) {
        openAi.response = response;
        Assertions.assertThat(client.suggest(List.of("rust"), "content").tagNames())
            .as("a degenerate hosted response yields an empty list")
            .isEmpty();
    }

    private static OllamaPullClient pullClient(PullState state) {
        return (OllamaPullClient) Proxy.newProxyInstance(
            OllamaPullClient.class.getClassLoader(),
            new Class<?>[] {OllamaPullClient.class},
            (proxy, method, args) -> {
                if (!"pull".equals(method.getName())) {
                    return null;
                }
                state.entered.countDown();
                if (state.blocking) {
                    state.release.await();
                }
                state.count.incrementAndGet();
                return new OllamaPullClient.PullResponse("success");
            });
    }

    private static OllamaClient chatClient(ChatState state) {
        return (OllamaClient) Proxy.newProxyInstance(
            OllamaClient.class.getClassLoader(),
            new Class<?>[] {OllamaClient.class},
            (proxy, method, args) -> {
                if (!"chat".equals(method.getName())) {
                    return null;
                }
                Supplier<RuntimeException> failure = state.failure;
                if (failure != null) {
                    throw failure.get();
                }
                state.count.incrementAndGet();
                return new OllamaClient.ChatResponse(
                    new OllamaClient.Message("assistant", "{\"tags\":[\"rust\"]}"), true);
            });
    }

    private static OllamaWarmUpClient warmUpClient() {
        return (OllamaWarmUpClient) Proxy.newProxyInstance(
            OllamaWarmUpClient.class.getClassLoader(),
            new Class<?>[] {OllamaWarmUpClient.class},
            (proxy, method, args) -> "generate".equals(method.getName())
                ? new OllamaWarmUpClient.GenerateResponse(true)
                : null);
    }

    private static final class PullState {
        final boolean blocking;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();

        PullState(boolean blocking) {
            this.blocking = blocking;
        }
    }

    private static OpenAiClient openAiClient(OpenAiState state) {
        return (OpenAiClient) Proxy.newProxyInstance(
            OpenAiClient.class.getClassLoader(),
            new Class<?>[] {OpenAiClient.class},
            (proxy, method, args) -> {
                if ("complete".equals(method.getName())) {
                    state.count.incrementAndGet();
                    state.authorization = (String) args[0];
                    state.lastRequest = (OpenAiClient.ChatCompletionRequest) args[1];
                    return state.response;
                }
                return null;
            });
    }

    private static final class ChatState {
        final AtomicInteger count = new AtomicInteger();
        volatile Supplier<RuntimeException> failure = null;
    }

    private static final class OpenAiState {
        final AtomicInteger count = new AtomicInteger();
        volatile String authorization;
        volatile OpenAiClient.ChatCompletionRequest lastRequest;
        volatile OpenAiClient.ChatCompletionResponse response;
    }
}
