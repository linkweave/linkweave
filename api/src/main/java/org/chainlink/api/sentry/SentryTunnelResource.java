package org.chainlink.api.sentry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jboss.logging.Logger;

@JaxResource
@Path("/sentry-tunnel")
@PermitAll
@Transactional(TxType.NOT_SUPPORTED)
@RateLimit(value = 400, window = 1, windowUnit = ChronoUnit.MINUTES)
@RequiredArgsConstructor
public class SentryTunnelResource {

    private static final Logger LOG = Logger.getLogger(SentryTunnelResource.class);
    private static final String ALLOWED_SENTRY_HOST_SUFFIX = ".sentry.io";
    private static final String ALLOWED_PROJECT_ID = "4511463699120208";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper;

    @POST
    @Blocking
    @PermitAll
    @Consumes(MediaType.WILDCARD)
    public Response tunnel(String body) {
        try {
            String envelopeHeader = body.split("\n", 2)[0];
            var header = objectMapper.readTree(envelopeHeader);

            var dsnNode = header.get("dsn");
            if (dsnNode == null || dsnNode.isNull()) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            URI dsn = URI.create(dsnNode.asText());
            String host = dsn.getHost();
            String projectId = dsn.getPath().replaceFirst("^/+", "");

            // Prevent abuse: only forward to Sentry's ingest, for this specific project
            if (!host.endsWith(ALLOWED_SENTRY_HOST_SUFFIX) || !ALLOWED_PROJECT_ID.equals(projectId)) {
                LOG.warnf("Rejected Sentry tunnel request for dsn=%s", dsnNode.asText());
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            URI ingestUrl = URI.create("https://" + host + "/api/" + projectId + "/envelope/");

            var request = HttpRequest.newBuilder(ingestUrl)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/x-sentry-envelope")
                    .build();

            var sentryResponse = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return Response.status(sentryResponse.statusCode()).build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while forwarding Sentry envelope", e);
            return Response.serverError().build();
        } catch (Exception e) {
            LOG.error("Failed to forward Sentry envelope", e);
            return Response.serverError().build();
        }
    }
}
