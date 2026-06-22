package org.linkweave.api.screenshot;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;

/**
 * REST Client interface for the Playwright screenshot sidecar.
 *
 * <p>URL and timeouts are configured under {@code quarkus.rest-client.screenshot-sidecar.*}
 * in {@code application.properties}. The sidecar returns image bytes (JPEG, per
 * the request body) on 2xx and {@code application/json} error objects on 4xx/5xx.
 * The 2xx response also carries the page's description (when found) in the
 * {@link #DESCRIPTION_HEADER} header as base64-encoded UTF-8. We return
 * {@link Response} rather than a typed body so the fetcher can read both the
 * bytes and that header, and so HTTP error statuses arrive as a status code to
 * inspect instead of a thrown exception.
 */
@RegisterRestClient(configKey = "screenshot-sidecar")
@Path("/screenshot")
public interface ScreenshotSidecarClient {

    /** Base64-encoded (UTF-8) page description on a successful capture, if any. */
    String DESCRIPTION_HEADER = "X-Page-Description";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("image/jpeg")
    @NonNull
    Response capture(@NonNull CaptureRequest request);

    record CaptureRequest(@NonNull String url, int width, int height, @NonNull String format, int quality) {}
}
