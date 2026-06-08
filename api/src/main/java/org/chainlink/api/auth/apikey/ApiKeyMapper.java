package org.chainlink.api.auth.apikey;

import org.chainlink.api.auth.apikey.json.ApiKeyCreatedJson;
import org.chainlink.api.auth.apikey.json.ApiKeyJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
public class ApiKeyMapper {

    private ApiKeyMapper() {}

    @NonNull
    public static ApiKeyJson toJson(@NonNull ApiKey apiKey) {
        return new ApiKeyJson(
            apiKey.getId(),
            apiKey.getName(),
            apiKey.getKeyPrefix(),
            apiKey.getTimestampErstellt(),
            apiKey.getExpiresAt(),
            apiKey.getLastUsedAt()
        );
    }

    @NonNull
    public static ApiKeyCreatedJson toCreatedJson(@NonNull ApiKey apiKey, @NonNull String rawKey) {
        return new ApiKeyCreatedJson(
            apiKey.getId(),
            apiKey.getName(),
            apiKey.getKeyPrefix(),
            apiKey.getTimestampErstellt(),
            apiKey.getExpiresAt(),
            apiKey.getLastUsedAt(),
            rawKey
        );
    }
}
