package ch.dvbern.dvbstarter.clock;

import java.time.Clock;
import java.time.Instant;

/**
 * This interface is needed to be able to mock the clock in tests.
 *
 * It should only be used in testing. Use {@link AppClock} in production.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface ClockProvider extends jakarta.validation.ClockProvider {

    @Override
    Clock getClock();

    void resetUsing(Instant instant);

    void reset();

    boolean isTimeTravelling();
}
