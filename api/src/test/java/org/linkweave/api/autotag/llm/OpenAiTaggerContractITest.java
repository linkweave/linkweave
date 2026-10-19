package org.linkweave.api.autotag.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
 * Wire-contract test for the OpenAI-compatible provider (NFR-032): bearer
 * header, request body and the {@code choices[0].message.content} reply shape
 * are exercised over real HTTP against a stub.
 */
@QuarkusTest
@WithTestResource(
    value = OpenAiTaggerContractITest.OpenAiStub.class,
    scope = TestResourceScope.RESTRICTED_TO_CLASS)
class OpenAiTaggerContractITest {

    private static final String SCOPE = "user-1:collection-1";

    public static class OpenAiStub extends WireMockLifecycle {
        @Override
        protected String[] baseUrlKeys() {
            return new String[] {"linkweave.autotag.openai.base-url"};
        }

        @Override
        protected Map<String, String> extraConfig() {
            return Map.of(
                "linkweave.autotag.provider", "openai",
                "linkweave.autotag.openai.api-key", "test-key",
                "linkweave.autotag.openai.model", "glm-test");
        }
    }

    @Inject
    LlmTaggingClient client;

    @Inject
    LlmCircuitBreaker circuitBreaker;

    @InjectWireMock
    WireMockServer stub;

    @BeforeEach
    void resetStub() {
        stub.resetAll();
        // Failure tests must see the failure itself, not an open circuit left by an earlier one.
        circuitBreaker.recordSuccess();
    }

    @Test
    void shouldParseTagsFromChoicesAndSendBearerKey() {
        // ARRANGE — providers return many fields we do not model; they must be tolerated
        stub.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"id":"x","usage":{"total_tokens":9},
                 "choices":[{"index":0,"finish_reason":"stop",
                   "message":{"role":"assistant","content":"{\\"tags\\":[\\"rust\\"]}"}}]}
                """)));

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust", "go"), "Async Rust", SCOPE);

        // ASSERT
        assertThat(result.tagNames()).containsExactly("rust");
        assertThat(result.outcome()).isEqualTo(SuggestionOutcome.OK);
        stub.verify(postRequestedFor(urlEqualTo("/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer test-key"))
            .withRequestBody(equalToJson("""
                {"model":"glm-test","temperature":0.0,"response_format":{"type":"json_object"}}
                """, true, true)));
    }

    @Test
    void shouldReportUnavailableForServerError() {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
            .withStatus(502).withBody("{\"error\":\"bad gateway\"}")));

        // ACT
        LlmTaggingClient.Result result = client.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        assertThat(result.tagNames()).isEmpty();
        assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
    }

    @Test
    void shouldReportUnavailableWhenTheConnectionIsDropped() {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/chat/completions"))
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
