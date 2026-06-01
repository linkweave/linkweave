package org.chainlink.api.screenshot;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;

/**
 * REST Client interface for the Playwright screenshot sidecar.
 *
 * <p>URL and timeouts are configured under {@code quarkus.rest-client.screenshot-sidecar.*}
 * in {@code application.properties}. The sidecar always returns image bytes
 * (JPEG, per the request body) on 2xx and {@code application/json} error
 * objects on 4xx/5xx — the latter surface as {@link jakarta.ws.rs.WebApplicationException}
 * to the caller, which the fetcher converts to a negative cache entry.
 */
@RegisterRestClient(configKey = "screenshot-sidecar")
@Path("/screenshot")
public interface ScreenshotSidecarClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("image/jpeg")
    byte @NonNull [] capture(@NonNull CaptureRequest request);

    record CaptureRequest(@NonNull String url, int width, int height, @NonNull String format, int quality) {}
}
