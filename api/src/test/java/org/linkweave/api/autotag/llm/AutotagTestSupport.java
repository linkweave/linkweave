package org.linkweave.api.autotag.llm;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.linkweave.api.shared.config.ConfigService;

/**
 * Shared fakes for the autotag provider tests.
 *
 * <p>The REST clients are faked with JDK dynamic proxies rather than concrete
 * classes: {@link OllamaClient} and friends carry {@code @Path}, so a class
 * implementing one would be indexed as a JAX-RS resource during
 * {@code @QuarkusTest} augmentation and registered as a live endpoint. A proxy
 * is a runtime object and invisible to the static index.
 */
final class AutotagTestSupport {

    /** Concurrency scope; irrelevant to these tests, which never run calls in parallel. */
    static final String SCOPE = "user-1:collection-1";

    private AutotagTestSupport() {
    }

    /**
     * A real {@link LlmCircuitBreaker} rather than a stub: these tests care that a
     * failure does not trigger a download, and the breaker sits on that path.
     * {@code init()} is called by hand because {@code @PostConstruct} only runs
     * under CDI.
     */
    static LlmCircuitBreaker newBreaker(ConfigService config) {
        LlmCircuitBreaker breaker = new LlmCircuitBreaker(config, new SimpleMeterRegistry());
        breaker.init();
        return breaker;
    }

    /**
     * Anonymous {@link ConfigService} subclass: the real one is populated by
     * MicroProfile Config injection, so a bare instance has null Durations that
     * the breaker would trip over on construction.
     */
    static ConfigService testConfig(boolean openAi, Optional<String> apiKey, String openAiModel) {
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
            public int getAutotagMaxTags() {
                return 3;
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

    static OllamaPullClient pullClient(PullState state) {
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

    static OllamaClient chatClient(ChatState state) {
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
                state.lastRequest = (OllamaClient.ChatRequest) args[0];
                return new OllamaClient.ChatResponse(
                    new OllamaClient.Message("assistant", "{\"tags\":[\"rust\"]}"), true);
            });
    }

    static OllamaWarmUpClient warmUpClient() {
        return (OllamaWarmUpClient) Proxy.newProxyInstance(
            OllamaWarmUpClient.class.getClassLoader(),
            new Class<?>[] {OllamaWarmUpClient.class},
            (proxy, method, args) -> "generate".equals(method.getName())
                ? new OllamaWarmUpClient.GenerateResponse(true)
                : null);
    }

    static OpenAiClient openAiClient(OpenAiState state) {
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

    static final class PullState {
        final boolean blocking;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();

        PullState(boolean blocking) {
            this.blocking = blocking;
        }
    }

    static final class ChatState {
        final AtomicInteger count = new AtomicInteger();
        volatile Supplier<RuntimeException> failure = null;
        volatile OllamaClient.ChatRequest lastRequest;
    }

    static final class OpenAiState {
        final AtomicInteger count = new AtomicInteger();
        volatile String authorization;
        volatile OpenAiClient.ChatCompletionRequest lastRequest;
        volatile OpenAiClient.ChatCompletionResponse response;
    }
}
