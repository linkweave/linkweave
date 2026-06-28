package org.linkweave.infrastructure.clock;

import java.time.Clock;
import java.time.Instant;

/**
 * Provides the application {@link Clock}. Primarily a seam so the clock can be
 * mocked/time-travelled in tests. Production code should depend on {@link AppClock}.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface ClockProvider extends jakarta.validation.ClockProvider {

    @Override
    Clock getClock();

    void resetUsing(Instant instant);

    void reset();

    boolean isTimeTravelling();
}
