package ch.dvbern.dvbstarter.inputvalidation;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ValidationConstants {
    // FIXME: eigenen Datentyp draus machen
    public static final String REGEX_CHE_UID = "(CHE?[0-9]{3}?[0-9]{3}?[0-9]{3})";

    // FIXME: eigenen Datentyp draus machen
    public static final String REGEX_CH_NR = "CH-(\\d{3})\\.(\\d)\\.(\\d{3})\\.(\\d{3})-(\\d)";

    public static final int DEFAULT_MAX_LENGTH = 255;

    public static final int EXTENDED_MAX_LENGTH = 1024;

    public static final int MAX_FILES = 10;
}
