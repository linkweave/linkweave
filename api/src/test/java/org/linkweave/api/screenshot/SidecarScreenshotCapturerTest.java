package org.linkweave.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * Translation tests for the sidecar adapter. Everything here is about turning
 * one transport detail — the base64 description header — into a domain value;
 * the rest of the adapter needs a sidecar and is covered by the screenshot
 * integration tests.
 */
class SidecarScreenshotCapturerTest {

    @Test
    void shouldDecodeBase64Utf8DescriptionHeader() {
        String original = "Café — résumé of the page ☕";
        String header = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));

        assertThat(SidecarScreenshotCapturer.decodeDescription(header)).isEqualTo(original);
    }

    @Test
    void shouldReturnNullForMissingOrBlankDescriptionHeader() {
        assertThat(SidecarScreenshotCapturer.decodeDescription(null)).isNull();
        assertThat(SidecarScreenshotCapturer.decodeDescription("")).isNull();
        assertThat(SidecarScreenshotCapturer.decodeDescription("   ")).isNull();
        // base64 of an empty/whitespace string decodes to blank → treated as none
        assertThat(SidecarScreenshotCapturer.decodeDescription(
            Base64.getEncoder().encodeToString("   ".getBytes(StandardCharsets.UTF_8)))).isNull();
    }

    @Test
    void shouldReturnNullForMalformedBase64RatherThanThrow() {
        assertThat(SidecarScreenshotCapturer.decodeDescription("not valid base64 !@#")).isNull();
    }

    @Test
    void shouldReturnBytesAndDecodedDescriptionOnSuccess() throws Exception {
        // ARRANGE
        byte[] image = "jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        String header = Base64.getEncoder()
            .encodeToString("A page".getBytes(StandardCharsets.UTF_8));
        SidecarScreenshotCapturer capturer = capturerReturning(() -> Response.ok(image)
            .header(ScreenshotSidecarClient.DESCRIPTION_HEADER, header)
            .build());

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result = capturer.capture(url());

        // ASSERT
        assertThat(result).isPresent();
        assertThat(result.get().bytes()).isEqualTo(image);
        assertThat(result.get().contentType()).isEqualTo("image/jpeg");
        assertThat(result.get().description()).isEqualTo("A page");
    }

    @Test
    void shouldReturnCaptureWithoutDescriptionWhenHeaderIsAbsent() throws Exception {
        // ARRANGE
        byte[] image = "jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        SidecarScreenshotCapturer capturer = capturerReturning(() -> Response.ok(image).build());

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result = capturer.capture(url());

        // ASSERT — a missing description is still a usable capture; the image is the product
        assertThat(result).isPresent();
        assertThat(result.get().bytes()).isEqualTo(image);
        assertThat(result.get().description()).isNull();
    }

    @Test
    void shouldReturnEmptyForNonSuccessStatus() throws Exception {
        // ARRANGE
        SidecarScreenshotCapturer capturer = capturerReturning(
            () -> Response.status(Response.Status.SERVICE_UNAVAILABLE).build());

        // ACT / ASSERT
        assertThat(capturer.capture(url())).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTheSidecarCannotBeReached() throws Exception {
        // ARRANGE — what the REST Client runtime throws for a refused connection
        SidecarScreenshotCapturer capturer = capturerReturning(() -> {
            throw new ProcessingException("Connection refused");
        });

        // ACT / ASSERT — unreachable is a normal outcome here, never an exception
        assertThat(capturer.capture(url())).isEmpty();
    }

    private static URL url() throws Exception {
        return URI.create("https://example.test/page").toURL();
    }

    private static SidecarScreenshotCapturer capturerReturning(Supplier<Response> response) {
        SidecarScreenshotCapturer capturer = new SidecarScreenshotCapturer();
        capturer.screenshotSidecarClient = (ScreenshotSidecarClient) Proxy.newProxyInstance(
            ScreenshotSidecarClient.class.getClassLoader(),
            new Class<?>[] {ScreenshotSidecarClient.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "capture" -> response.get();
                case "toString" -> "fake ScreenshotSidecarClient";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return capturer;
    }
}
