package org.linkweave.infrastructure.openapi;

import lombok.experimental.UtilityClass;

/**
 * OpenAPI constants used in {@code @Schema} declarations across the API.
 */
@UtilityClass
public class OpenApiConst {

    @UtilityClass
    public static final class Format {
        public static final String ENTITY_ID = "entity-id";
        public static final String EXCEPTION_ID = "exception-id";
        public static final String EMAIL = "email";
        public static final String DATE = "date";
        public static final String DATE_TIME = "date-time";
        public static final String URL = "url";
        public static final String UUID = "uuid";
    }
}
