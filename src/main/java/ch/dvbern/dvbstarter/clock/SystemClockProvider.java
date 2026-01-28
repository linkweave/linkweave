package ch.dvbern.dvbstarter.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import ch.dvbern.dvbstarter.shared.i18n.datetime.DateConst;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemClockProvider implements ClockProvider {

    private Duration offset = Duration.ofSeconds(0);

    @Override
    public Clock getClock() {
        return Clock.offset(Clock.system(DateConst.ZONE_ID), this.offset);
    }

    @Override
    public void resetUsing(Instant instant) {
        this.offset = Duration.between(Instant.now(), instant);
    }

    @Override
    public void reset() {
        this.offset = Duration.ofSeconds(0);
    }

    @Override
    public boolean isTimeTravelling() {
        // Achtung: gleicher Tag, andere Zeit ist immer noch Time Travelling
        return Math.abs(this.offset.getSeconds()) > 0;
    }
}
