package ch.dvbern.dvbstarter.i18n.datetime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class DateUtil {

    @UtilityClass
    public static class Truncation {

        /**
         * Truncate to microseconds because PostgreSQL does not support nanoseconds
         */
        public static LocalDateTime truncateToMicros(LocalDateTime localDateTime) {
            return localDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        /**
         * Truncate to microseconds because PostgreSQL does not support nanoseconds
         */
        public static OffsetDateTime truncateToMicros(OffsetDateTime offsetDateTime) {
            return offsetDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        /**
         * Truncate to microseconds because PostgreSQL does not support nanoseconds
         */
        public static ZonedDateTime truncateToMicros(ZonedDateTime zonedDateTime) {
            return zonedDateTime.truncatedTo(ChronoUnit.MICROS);
        }

        public static LocalTime truncateToMicros(LocalTime localTime) {
            return localTime.truncatedTo(ChronoUnit.MICROS);
        }
    }

    /**
     * gueltigAb <= date <= gueltigBis
     */
    public static boolean contains(
        LocalDate stichtag,
        LocalDate gueltigAb,
        LocalDate gueltigBis) {

        return !(stichtag.isBefore(gueltigAb) || stichtag.isAfter(gueltigBis));
    }

    @Nullable
    public LocalDate minDate(@Nullable LocalDate date1, @Nullable LocalDate date2) {
        if (date1 == null) {
            return date2;
        }
        if (date2 == null) {
            return date1;
        }
        return date1.isBefore(date2) ? date1 : date2;
    }

    @NonNull
    public static BigDecimal calculateAlter(@NonNull LocalDate geburtsdatum, @NonNull LocalDate stichtag) {
        Period period = Period.between(geburtsdatum, stichtag);
        BigDecimal bruch = BigDecimal.valueOf(period.getMonths()).divide(BigDecimal.valueOf(12), 2, RoundingMode.FLOOR);
        return BigDecimal.valueOf(period.getYears()).add(bruch);
    }

    @NonNull
    public static String calculateAlterDuodezimal(@NonNull LocalDate geburtsdatum, @NonNull LocalDate stichtag) {
        Period period = Period.between(geburtsdatum, stichtag);
        return period.getYears() + " " + toSuperscript(period.getMonths()) + '/' + "₁₂";
    }

    @NonNull
    private static String toSuperscript(int number) {
        String[] superscripts = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹", "¹⁰", "¹¹"};
        StringBuilder result = new StringBuilder();
        String numStr = String.valueOf(number);
        for (char digit : numStr.toCharArray()) {
            int digitValue = Character.getNumericValue(digit);
            result.append(superscripts[digitValue]);
        }
        return result.toString();
    }
}
