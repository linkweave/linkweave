package ch.dvbern.dvbstarter.i18n.datetime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DateConst {

    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");

    public static final int MIN_YEAR = 0;
    public static final int MAX_YEAR = 9999;
    public static final LocalDate BEGIN_OF_TIME = LocalDate.of(MIN_YEAR, 1, 1);
    public static final LocalDate END_OF_TIME = LocalDate.of(MAX_YEAR, 12, 31);
    public static final LocalDateTime BEGIN_OF_DATETIME =
        DateUtil.Truncation.truncateToMicros(BEGIN_OF_TIME.atTime(LocalTime.MIN));
    public static final LocalDateTime END_OF_DATETIME =
        DateUtil.Truncation.truncateToMicros(END_OF_TIME.atTime(LocalTime.MAX));

    public static final OffsetDateTime BEGIN_OF_OFFSETDATETIME =
        DateUtil.Truncation.truncateToMicros(BEGIN_OF_DATETIME.atZone(ZONE_ID).toOffsetDateTime());
    public static final OffsetDateTime END_OF_OFFSETDATETIME =
        DateUtil.Truncation.truncateToMicros(END_OF_DATETIME.atZone(ZONE_ID).toOffsetDateTime());

}
