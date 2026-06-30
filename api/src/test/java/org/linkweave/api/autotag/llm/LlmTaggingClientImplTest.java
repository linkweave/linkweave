package org.linkweave.api.autotag.llm;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Test;
import org.linkweave.api.shared.config.ConfigService;

/**
 * Unit tests for {@link LlmTaggingClientImpl}'s cold-start pull guard. The
 * pull's read-timeout is 30 minutes, so it must run off the request thread: a
 * cold suggestion schedules the pull on the {@link ManagedExecutor} and
 * fast-fails (returns empty) instead of blocking the caller, and only once the
 * model is resident are suggestions served.
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
        List<String> firstCold = client.suggest(List.of("rust"), "content");

        // ASSERT
        Assertions.assertThat(firstCold)
            .as("a cold suggest fast-fails instead of blocking on the pull")
            .isEmpty();
        Assertions.assertThat(pull.entered.await(2, SECONDS))
            .as("the pull runs in the background, off the request thread")
            .isTrue();

        // A concurrent caller, while the pull is still downloading, also fast-fails
        // and never invokes the model.
        Assertions.assertThat(client.suggest(List.of("rust"), "content")).isEmpty();
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
        Assertions.assertThat(client.suggest(List.of("rust"), "content")).isEmpty();
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
    void shouldRePullAfterTheModelIsLostMidRun() throws Exception {
        // ARRANGE — warm the model so suggestions are served.
        PullState pull = new PullState(false);
        ChatState chat = new ChatState();
        LlmTaggingClientImpl client = newClient(pullClient(pull), chatClient(chat));
        Assertions.assertThat(client.suggest(List.of("rust"), "content")).isEmpty();
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get()).isOne();

        // ACT — Ollama loses the weights (volume cleared / model deleted), so the
        // chat call now fails on an otherwise-warm client.
        chat.failing = true;
        Assertions.assertThatThrownBy(() -> client.suggest(List.of("rust"), "content"))
            .as("a lost model surfaces as a runtime failure for the service to swallow")
            .isInstanceOf(RuntimeException.class);

        // ASSERT — the failure invalidates the resident-model flag and re-arms the
        // pull, so once the model is back the next suggestion re-pulls and serves
        // (rather than failing forever until a JVM restart).
        chat.failing = false;
        Assertions.assertThat(awaitServed(client)).containsExactly("rust");
        Assertions.assertThat(pull.count.get())
            .as("model is re-pulled after being lost mid-run")
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
        List<String> result = client.suggest(List.of("rust", "databases", "career"), "Async Rust blog");

        // ASSERT
        Assertions.assertThat(result)
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
        List<String> result = client.suggest(List.of("rust"), "content");

        // ASSERT
        Assertions.assertThat(result)
            .as("a missing API key fast-fails with an empty list")
            .isEmpty();
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
            List<String> served = client.suggest(List.of("rust"), "content");
            if (!served.isEmpty()) {
                return served;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("model never became warm");
    }

    private static LlmTaggingClientImpl newClient(OllamaPullClient pull, OllamaClient chat) {
        ConfigService config = new ConfigService() {
            @Override
            public boolean isAutotagProviderOpenAi() {
                return false;
            }

            @Override
            public String getAutotagModel() {
                return "gemma2:2b";
            }

            @Override
            public String getAutotagKeepAlive() {
                return "15m";
            }
        };
        LlmTaggingClientImpl client =
            new LlmTaggingClientImpl(config, new ObjectMapper(), ManagedExecutor.builder().build());
        client.ollamaPullClient = pull;
        client.ollamaClient = chat;
        return client;
    }

    private static LlmTaggingClientImpl newOpenAiClient(OpenAiState openAi, Optional<String> apiKey, String model) {
        ConfigService config = new ConfigService() {
            @Override
            public boolean isAutotagProviderOpenAi() {
                return true;
            }

            @Override
            public Optional<String> getAutotagOpenAiApiKey() {
                return apiKey;
            }

            @Override
            public String getAutotagOpenAiModel() {
                return model;
            }
        };
        LlmTaggingClientImpl client =
            new LlmTaggingClientImpl(config, new ObjectMapper(), ManagedExecutor.builder().build());
        client.openAiClient = openAiClient(openAi);
        return client;
    }

    private static void assertEmptyFor(
        OpenAiState openAi, LlmTaggingClientImpl client, OpenAiClient.ChatCompletionResponse response) {
        openAi.response = response;
        Assertions.assertThat(client.suggest(List.of("rust"), "content"))
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

    private static OllamaClient chatClient(ChatState state) {        return (OllamaClient) Proxy.newProxyInstance(
            OllamaClient.class.getClassLoader(),
            new Class<?>[] {OllamaClient.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "chat" -> {
                        if (state.failing) {
                            throw new RuntimeException("model 'gemma2:2b' not found");
                        }
                        state.count.incrementAndGet();
                        return new OllamaClient.ChatResponse(
                            new OllamaClient.Message("assistant", "{\"tags\":[\"rust\"]}"), true);
                    }
                    case "generate" -> {
                        return new OllamaClient.GenerateResponse(true);
                    }
                    default -> {
                        return null;
                    }
                }
            });
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
        volatile boolean failing = false;
    }

    private static final class OpenAiState {
        final AtomicInteger count = new AtomicInteger();
        volatile String authorization;
        volatile OpenAiClient.ChatCompletionRequest lastRequest;
        volatile OpenAiClient.ChatCompletionResponse response;
    }
}
