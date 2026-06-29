package org.linkweave.infrastructure.i18n.datetime;

import java.time.LocalDate;
import java.time.ZoneId;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DateConst {

    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");

    public static final int MIN_YEAR = 0;
    public static final int MAX_YEAR = 9999;
    public static final LocalDate BEGIN_OF_TIME = LocalDate.of(MIN_YEAR, 1, 1);
    public static final LocalDate END_OF_TIME = LocalDate.of(MAX_YEAR, 12, 31);
}
