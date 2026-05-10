package org.chainlink.api.dev;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.clock.ClockProvider;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.Nullable;

/**
 * Dev/test-only endpoint for shifting the application clock. Lets e2e tests
 * exercise time-sensitive features (cleanup suggestions, favicon TTLs, etc.)
 * without poking the database directly. Disabled in the prod profile.
 */
@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@UnlessBuildProfile("prod")
@RequiredArgsConstructor
@Path("/dev/time-travel")
@PermitAll
public class TimeTravelResource {

    private final ClockProvider clockProvider;

    @PermitAll
    public record TimeTravelRequest(@Nullable String instant) {}

    @PermitAll
    public record TimeTravelStatus(boolean timeTravelling, String now) {}

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(TxType.NOT_SUPPORTED)
    @PermitAll
    public Response travelTo(TimeTravelRequest request) {
        if (request == null || request.instant() == null || request.instant().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("missing 'instant' (ISO-8601 timestamp)")
                .build();
        }
        try {
            clockProvider.resetUsing(Instant.parse(request.instant()));
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("invalid ISO-8601 instant: " + e.getMessage())
                .build();
        }
        return Response.ok(currentStatus()).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(TxType.NOT_SUPPORTED)
    @PermitAll
    public TimeTravelStatus reset() {
        clockProvider.reset();
        return currentStatus();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(TxType.NOT_SUPPORTED)
    @PermitAll
    public TimeTravelStatus status() {
        return currentStatus();
    }

    private TimeTravelStatus currentStatus() {
        return new TimeTravelStatus(
            clockProvider.isTimeTravelling(),
            Instant.now(clockProvider.getClock()).toString()
        );
    }
}
