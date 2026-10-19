package org.linkweave.api.screenshot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

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
 * Wire-contract test for {@link SidecarScreenshotCapturer} (NFR-032): the real
 * generated REST client talks HTTP to a stub, so a renamed header, a changed
 * request body or a different encoding fails here instead of in production.
 */
@QuarkusTest
@WithTestResource(
    value = SidecarScreenshotCapturerContractITest.SidecarStub.class,
    scope = TestResourceScope.RESTRICTED_TO_CLASS)
class SidecarScreenshotCapturerContractITest {

    public static class SidecarStub extends WireMockLifecycle {
        @Override
        protected String[] baseUrlKeys() {
            return new String[] {"quarkus.rest-client.screenshot-sidecar.url"};
        }
    }

    @Inject
    ScreenshotCapturer capturer;

    @InjectWireMock
    WireMockServer stub;

    @BeforeEach
    void resetStub() {
        stub.resetAll();
    }

    @Test
    void shouldParseImageAndDescriptionFromSuccessResponse() throws Exception {
        // ARRANGE
        byte[] image = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        String description = Base64.getEncoder().encodeToString("Café ☕".getBytes(StandardCharsets.UTF_8));
        stub.stubFor(post(urlEqualTo("/screenshot")).willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "image/jpeg")
            .withHeader("X-Page-Description", description)
            .withBody(image)));

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result = capturer.capture(url());

        // ASSERT
        assertThat(result).isPresent();
        assertThat(result.get().bytes()).isEqualTo(image);
        assertThat(result.get().contentType()).isEqualTo("image/jpeg");
        assertThat(result.get().description()).isEqualTo("Café ☕");
        stub.verify(postRequestedFor(urlEqualTo("/screenshot"))
            .withRequestBody(equalToJson("""
                {"url":"https://example.test/page","width":1280,"height":800,"format":"jpeg","quality":60}
                """)));
    }

    @Test
    void shouldReturnEmptyForNon2xxResponse() throws Exception {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/screenshot")).willReturn(aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"busy\"}")));

        // ACT / ASSERT
        assertThat(capturer.capture(url())).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTheConnectionIsDropped() throws Exception {
        // ARRANGE
        stub.stubFor(post(urlEqualTo("/screenshot"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // ACT / ASSERT
        assertThat(capturer.capture(url())).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNothingListensOnTheSidecarPort() throws Exception {
        // ARRANGE — a genuinely refused connection, not a stubbed fault
        stub.stop();
        try {
            // ACT / ASSERT
            assertThat(capturer.capture(url())).isEmpty();
        } finally {
            stub.start();
        }
    }

    private static URL url() throws Exception {
        return URI.create("https://example.test/page").toURL();
    }
}
