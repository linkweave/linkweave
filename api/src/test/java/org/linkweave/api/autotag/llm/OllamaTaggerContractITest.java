package org.linkweave.api.autotag.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.testutil.wiremock.InjectWireMock;
import org.linkweave.api.testutil.wiremock.WireMockLifecycle;

/**
 * Wire-contract test for the Ollama provider (NFR-032): the generated REST
 * clients, their serialization and error mapping run against an HTTP stub, so a
 * change in the shape of {@code /api/chat} fails here rather than in production.
 *
 * <p>Goes through the production {@link LlmTaggingClient} bean, which is the
 * provider wrapped in its circuit breaker, so failures arrive as the outcomes
 * the rest of the application sees.
 */
@QuarkusTest
@WithTestResource(
    value = OllamaTaggerContractITest.OllamaStub.class,
    scope = TestResourceScope.RESTRICTED_TO_CLASS)
class OllamaTaggerContractITest {

    private static final String SCOPE = "user-1:collection-1";

    public static class OllamaStub extends WireMockLifecycle {
        @Override
        protected String[] baseUrlKeys() {
            return new String[] {"linkweave.autotag.service-url"};
        }
    }

    @Inject
    LlmTaggingClient client;

    @Inject
    LlmCircuitBreaker circuitBreaker;

    @InjectWireMock
    WireMockServer stub;

    @BeforeEach
    void primeModelAndResetStub() {
        stub.resetAll();
        // Failure tests must see the failure itself, not an open circuit left by an earlier one.
        circuitBreaker.recordSuccess();
        stub.stubFor(post(urlEqualTo("/api/pull")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json").withBody("{\"status\":\"success\"}")));
        stub.stubFor(post(urlEqualTo("/api/generate")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json").withBody("{\"done\":true}")));
        client.warmUp();
    }

    @Test
    void shouldParseTagsFromChatResponse() {
        // ARRANGE — Ollama sends many fields we do not model; they must be tolerated
        stub.stubFor(post(urlEqualTo("/api/chat")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"model":"gemma2:2b","created_at":"2026-01-01T00:00:00Z",
                 "message":{"role":"assistant","content":"{\\"tags\\":[\\"rust\\"]}"},
                 "done":true,"total_duration":123}
                """)));

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust", "go"), "Async Rust", SCOPE);

        // ASSERT
        assertThat(result.tagNames()).containsExactly("rust");
        assertThat(result.outcome()).isEqualTo(SuggestionOutcome.OK);
        stub.verify(postRequestedFor(urlEqualTo("/api/chat"))
            .withRequestBody(equalToJson("""
                {"stream":false,"options":{"temperature":0.0},
                 "format":{"type":"object","required":["tags"],
                   "properties":{"tags":{"type":"array","maxItems":3,
                     "items":{"type":"string","enum":["rust","go"]}}}}}
                """, true, true)));
    }

    @Test
    void shouldReportUnavailableForServerError() {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/api/chat")).willReturn(aResponse()
            .withStatus(500).withBody("{\"error\":\"boom\"}")));

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        assertThat(result.tagNames()).isEmpty();
        assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableWhenTheConnectionIsDropped() {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/api/chat"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableWhenNothingListens() {
        // ARRANGE — a genuinely refused connection
        stub.stop();
        try {
            // ACT
            LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

            // ASSERT
            assertThat(result.tagNames()).isEmpty();
            assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
        } finally {
            stub.start();
        }
    }
}
