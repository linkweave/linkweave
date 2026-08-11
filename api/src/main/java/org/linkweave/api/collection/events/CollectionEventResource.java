package org.linkweave.api.collection.events;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.faulttolerance.api.RateLimit;
import io.smallrye.mutiny.Multi;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.ratelimit.RateLimitConst;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Server-sent events channel for one collection (UC-104).
 *
 * <p>Deliberately generous rate limit. BR-208 wants the channel exempt from the
 * ordinary per-endpoint caps so that a reconnect loop can never lock a user out
 * of the API — but every {@code @JaxResource} must carry {@code @RateLimit}
 * ({@code JaxResourceTest.enforce_rate_limit_on_all_methods}), and the cap is
 * process-wide rather than per user (see {@code RateLimitConst}). A cap this
 * high bounds a runaway client without being reachable by real usage, which is
 * the same trade {@code ScreenshotResource} makes for the same reason — hence the
 * shared {@link RateLimitConst#HIGH_FANOUT_PER_MINUTE}.
 */
@RateLimit(value = RateLimitConst.HIGH_FANOUT_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/events")
public class CollectionEventResource {

    /**
     * Well under the idle timeout of any intermediary worth worrying about
     * (Caddy itself has none), and cheap: one small frame per open tab.
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    private final CollectionEventBroadcaster broadcaster;
    private final AuthorizationService authorizationService;

    /**
     * Subscribes to this collection's updates for as long as the connection is
     * held open.
     *
     * <p>{@code clientId} identifies the calling <em>browser tab</em> so the tab
     * that caused a change does not act on its own notification (BR-205). It is
     * a query parameter and not a header because the browser's {@code EventSource}
     * cannot set request headers — there is no way to send it any other way on
     * the subscribe call. Mutating endpoints are free to take it as a header.
     *
     * <p>{@code TxType.NOT_SUPPORTED} overrides the {@code @Transactional} that
     * {@code @JaxResource} carries. The stereotype cannot simply be dropped —
     * {@code NamingTest.resources_have_JaxResource_stereotype} requires it — but
     * a streamed response must not sit inside a JTA transaction.
     *
     * <p>{@code @Blocking} is required, not stylistic. A {@code Multi}-returning
     * method is treated as reactive and dispatched on the IO thread, where the
     * JTA interceptor and the blocking JDBC read behind
     * {@code requireCollectionAccess} both throw
     * {@code BlockingOperationNotAllowedException}. Running the method body on a
     * worker thread fixes both; the worker is released once the method returns
     * the stream, so an open connection does not pin one.
     *
     * <p>Heartbeats are merged in because an idle stream is this channel's
     * <em>normal</em> state — screenshot completions are rare — and an
     * intermediary that drops idle connections would kill it silently (BR-208).
     * They are a transport concern, so they live here rather than in the
     * broadcaster, and they bypass the {@code clientId} filter by construction:
     * a heartbeat has no originating tab.
     */
    @GET
    @Authenticated
    @Blocking
    @Transactional(TxType.NOT_SUPPORTED)
    @Produces(MediaType.SERVER_SENT_EVENTS) // declare this endpoint as SSE stream
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<CollectionEventJson> stream(
        @PathParam("collectionId") ID<Collection> collectionId,
        @QueryParam("clientId") @IgnoreForIdClassTest @Nullable String clientId
    ) {
        authorizationService.requireCollectionAccess(collectionId); // BR-201
        return Multi.createBy().merging().streams(
            broadcaster.subscribe(collectionId, clientId), // BR-205
            heartbeats(collectionId, HEARTBEAT_INTERVAL)
        );
    }

    /**
     * Keep-alive traffic for an otherwise idle stream.
     *
     * <p>The first frame is emitted <strong>immediately</strong> on subscribe,
     * not one interval later: that is what a bare {@code ticks().every()} does,
     * and it cannot be spelled out as {@code startingAfter(ZERO)} because Mutiny
     * rejects a zero duration there. The behaviour is load-bearing rather than
     * incidental, so it is pinned by a test. It produces the first body byte at
     * once, which is
     * what completes the SSE handshake in the browser and fires {@code onopen}:
     * a stream whose first real event may be minutes away would otherwise leave
     * the client unsure it ever connected, and any intermediary that withholds
     * response headers until the first byte would stall it outright — the same
     * failure the {@code encode} exclusion in {@code frontend/Caddyfile} and the
     * dev-proxy header flush address, defended here a third time at the source.
     *
     * <p>Ticks are dropped rather than queued if the client is not keeping up: a
     * backlog of heartbeats is worth nothing, and letting it overflow would fail
     * the whole stream. Cancelled with the merged stream on disconnect.
     */
    @NonNull
    static Multi<CollectionEventJson> heartbeats(@NonNull ID<Collection> collectionId, @NonNull Duration interval) {
        return Multi.createFrom().ticks().every(interval)
            .onOverflow().drop()
            .map(tick -> CollectionEventJson.heartbeat(collectionId));
    }
}
