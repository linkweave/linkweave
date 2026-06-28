package org.linkweave.infrastructure.i18n.datetime;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DateUtil {

    @UtilityClass
    public static class Truncation {

        /**
         * Truncate to microseconds because the database does not support nanoseconds.
         */
        public static LocalDateTime truncateToMicros(LocalDateTime localDateTime) {
            return localDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        /**
         * Truncate to microseconds because the database does not support nanoseconds.
         */
        public static OffsetDateTime truncateToMicros(OffsetDateTime offsetDateTime) {
            return offsetDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        /**
         * Truncate to microseconds because the database does not support nanoseconds.
         */
        public static ZonedDateTime truncateToMicros(ZonedDateTime zonedDateTime) {
            return zonedDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        public static LocalTime truncateToMicros(LocalTime localTime) {
            return localTime.truncatedTo(ChronoUnit.MICROS);
        }
    }
}
