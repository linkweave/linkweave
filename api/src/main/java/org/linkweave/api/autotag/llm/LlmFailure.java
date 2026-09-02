package org.linkweave.api.autotag.llm;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * How a model call failed (UC-108 BR-108-2).
 *
 * <p>The distinction exists because {@code LlmTaggingClientImpl} used to treat
 * every {@link RuntimeException} the same way: clear the "model is pulled" flag
 * and schedule {@code POST /api/pull}. A read timeout would therefore trigger a
 * 1.6 GB download — on a host already too loaded to answer in time, which makes
 * the next timeout more likely. That feedback loop is what filled the log on
 * 2026-08-28 with the same warning eight times over.
 *
 * <p>Only {@link #MODEL_NOT_FOUND} — the model service explicitly answering that
 * the weights are absent — says anything about the weights. Everything else says
 * the service is unhealthy or overloaded, and the correct response to an
 * overloaded host is to leave it alone.
 */
public enum LlmFailure {

    /** The model did not answer inside the budget. Says nothing about the weights. */
    TIMEOUT("timeout"),

    /** Connection refused, DNS failure, no route — the service is not listening. */
    UNREACHABLE("unreachable"),

    /** The service answered that the model is not installed. The only cause that may pull. */
    MODEL_NOT_FOUND("model_not_found"),

    /** HTTP 5xx, a malformed response, anything else. Unhealthy, not missing. */
    OTHER("error");

    private final String metricValue;

    LlmFailure(@NonNull String metricValue) {
        this.metricValue = metricValue;
    }

    /** Stable, low-cardinality tag value for the outcome metric (BR-108-8, NFR-024). */
    public @NonNull String metricValue() {
        return metricValue;
    }

    /** True only for the one cause that justifies downloading weights (BR-108-2). */
    public boolean warrantsPull() {
        return this == MODEL_NOT_FOUND;
    }

    /**
     * Classifies a failed model call by walking the exception's cause chain.
     *
     * <p>The chain matters: the REST Client wraps the transport failure, so a
     * connect refusal arrives as a {@code ProcessingException} whose cause is a
     * {@link ConnectException}. Timeouts are recognised both by type and by the
     * Vert.x client's message ("The timeout period of 30000ms has been
     * exceeded"), which is a plain {@code RuntimeException} carrying no useful
     * type at all.
     */
    public static @NonNull LlmFailure classify(@NonNull Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof WebApplicationException wae && isModelNotFound(wae)) {
                return MODEL_NOT_FOUND;
            }
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
                return TIMEOUT;
            }
            if (t instanceof ConnectException
                || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException) {
                return UNREACHABLE;
            }
            String message = lower(t.getMessage());
            if (message.contains("timeout period of") || message.contains("timed out")) {
                return TIMEOUT;
            }
            if (message.contains("connection refused") || message.contains("connection reset")) {
                return UNREACHABLE;
            }
        }
        return OTHER;
    }

    /**
     * Ollama reports a missing model as {@code 404} with a body like
     * {@code {"error":"model 'gemma2:2b' not found, try pulling it first"}}. The
     * status alone is not enough — a 404 from a misconfigured base URL is a
     * routing mistake, not a missing model — so the body has to agree.
     */
    private static boolean isModelNotFound(@NonNull WebApplicationException wae) {
        Response response = wae.getResponse();
        if (response == null || response.getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
            return false;
        }
        String body = readBodySafely(response);
        return body.contains("not found") || body.contains("try pulling");
    }

    /**
     * Reads the error body without letting the read itself fail the classification.
     *
     * <p>Two shapes have to work. A response that arrived over the wire holds its
     * body in a stream that {@code readEntity} drains; a response built in memory
     * (a locally constructed {@link WebApplicationException}, as in tests) holds
     * the object directly and throws {@code IllegalStateException} from
     * {@code readEntity}. Both are tried, and a body that cannot be read at all
     * means "not a missing model" — a classifier that threw while classifying a
     * failure would turn a degraded feature into a broken one.
     */
    private static @NonNull String readBodySafely(@NonNull Response response) {
        try {
            if (response.getEntity() instanceof String direct) {
                return lower(direct);
            }
        } catch (RuntimeException e) {
            // Closed or already-consumed response; fall through to readEntity.
        }
        try {
            return response.hasEntity() ? lower(response.readEntity(String.class)) : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static @NonNull String lower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
