package org.linkweave.infrastructure.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.linkweave.infrastructure.i18n.datetime.DateUtil;
import jakarta.enterprise.context.RequestScoped;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
@RequestScoped
public class AppClock {
    private final ClockProvider clockProvider;

    private Clock clock() {
        return clockProvider.getClock();
    }

    public LocalDateTimeProvider localDateTime() {
        return new LocalDateTimeProvider(clock());
    }

    public LocalTimeProvider localTime() {
        return new LocalTimeProvider(clock());
    }

    public OffsetDateTimeProvider offsetDateTime() {
        return new OffsetDateTimeProvider(clock());
    }

    public ZonedDateTimeProvider zonedDateTime() {
        return new ZonedDateTimeProvider(clock());
    }

    public InstantProvider instant() {
        return new InstantProvider(clock());
    }

    public record InstantProvider(Clock clock) {
        public InstantProvider {
            Objects.requireNonNull(clock);
        }

        public Instant now() {
            return Instant.now(clock);
        }
    }

    public record LocalTimeProvider(Clock clock) {

        public LocalTimeProvider {
            Objects.requireNonNull(clock);
        }

        public LocalTime now() {
            return DateUtil.Truncation.truncateToMicros(LocalTime.now(clock));
        }

        public LocalTime nowWithNanos() {
            return LocalTime.now(clock);
        }
    }

    public record LocalDateTimeProvider(Clock clock) {
        public LocalDateTimeProvider {
            Objects.requireNonNull(clock);
        }

        public LocalDateTime now() {
            return DateUtil.Truncation.truncateToMicros(LocalDateTime.now(clock));
        }

        public LocalDateTime nowWithNanos() {
            return LocalDateTime.now(clock);
        }

        public LocalDateTime atStartOfDay() {
            return DateUtil.Truncation.truncateToMicros(LocalDateTime.now(clock)
                .with(LocalTime.MIN));
        }

        public LocalDateTime atEndOfDay() {
            return DateUtil.Truncation.truncateToMicros(LocalDateTime.now(clock)
                .with(LocalTime.MAX));
        }
    }

    public record OffsetDateTimeProvider(Clock clock) {
        public OffsetDateTimeProvider {
            Objects.requireNonNull(clock);
        }

        public OffsetDateTime now() {
            return DateUtil.Truncation.truncateToMicros(OffsetDateTime.now(clock));
        }

        public OffsetDateTime nowWithNanos() {
            return OffsetDateTime.now(clock);
        }
    }

    public record ZonedDateTimeProvider(Clock clock) {
        public ZonedDateTimeProvider {
            Objects.requireNonNull(clock);
        }

        public ZonedDateTime now() {
            return DateUtil.Truncation.truncateToMicros(ZonedDateTime.now(clock));
        }

        public ZonedDateTime nowWithNanos() {
            return ZonedDateTime.now(clock);
        }

    }

    @NonNull
    public LocalDateTime getNow() {
        return localDateTime().now();
    }

    @NonNull
    public LocalDate getToday() {
        return getNow().toLocalDate();
    }

}
