package org.linkweave.api.collection.events;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import lombok.Getter;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.Nullable;

/**
 * Captures the calling browser tab's own id from {@code X-Client-Id} so a change
 * can be attributed to the tab that made it (BR-205).
 *
 * <p>A header here, a query parameter on the stream: mutating requests are
 * ordinary {@code fetch} calls and can set headers, while
 * {@code CollectionEventResource} has to take the same value in the query string
 * because the browser's {@code EventSource} cannot send headers at all.
 *
 * <p>Opaque to the server — never parsed, never looked up, never trusted for
 * anything but "was it this tab?". A forged value can only cause the sender's
 * own notification to be dropped for whoever forged it.
 */
@Provider
@PreMatching
@RequestScoped
public class OriginClientIdRequestFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Client-Id";

    /**
     * Absent for anything that is not a browser: the CLI, the API-key clients,
     * tests. Not an entity id despite the name — hence
     * {@link IgnoreForIdClassTest}.
     */
    @Getter
    @IgnoreForIdClassTest
    private @Nullable String originClientId;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        originClientId = requestContext.getHeaderString(HEADER);
    }
}
