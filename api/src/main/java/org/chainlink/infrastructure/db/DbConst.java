package org.chainlink.infrastructure.db;

import java.time.LocalDateTime;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DbConst {
    public static final byte[] EMPTY_BYTES = {};

    public static final int DB_DEFAULT_MAX_LENGTH = 255;
    public static final int DB_TEXTAREA_MAX_LENGTH_2000 = 2000;
    public static final int DB_TEXTAREA_MAX_LENGTH_5000 = 5000;
    public static final int DB_GTIN_NUM_LENGTH = 14;

    public static final int DB_UUID_LENGTH = 36;
    public static final int DB_ENUM_LENGTH = 30;
    public static final int DB_ENUM_LIST_LENGTH = 2000;
    public static final int DB_LONG_ENUM_LENGTH = 40;
    public static final int DB_FREITEXT_LENGTH = 32768;
    public static final int DB_DEFAULT_PRECISION = 30;
    public static final int DB_DEFAULT_SCALE = 2;
    public static final int DB_PRECISE_PRECISION = 30;
    public static final int DB_PRECISE_SCALE = 10;
    public static final int DB_FRANKEN_PRECISION = 19;
    public static final int DB_FRANKEN_SCALE = 2;
    public static final int DB_ZAHL_PRECISION = 19;
    public static final int DB_ZAHL_SCALE_1 = 1;

    // we have no inform about login timestamps before this date
    public static final LocalDateTime EARLIEST_COLLECTED_LOGIN_TIMESTAMP = LocalDateTime.of(2025, 4, 1, 0, 0);

}
