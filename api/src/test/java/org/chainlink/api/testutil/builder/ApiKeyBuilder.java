package org.chainlink.api.testutil.builder;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.auth.apikey.ApiKey;
import org.chainlink.api.shared.user.User;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class ApiKeyBuilder {

    private final ApiKey apiKey;

    public ApiKeyBuilder() {
        this.apiKey = new ApiKey();
    }

    @NonNull
    public ApiKeyBuilder withUser(User user) {
        apiKey.setUser(user);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withName(String name) {
        apiKey.setName(name);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withKeyHash(String keyHash) {
        apiKey.setKeyHash(keyHash);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withKeyPrefix(String keyPrefix) {
        apiKey.setKeyPrefix(keyPrefix);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withExpiresAt(OffsetDateTime expiresAt) {
        apiKey.setExpiresAt(expiresAt);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withLastUsedAt(OffsetDateTime lastUsedAt) {
        apiKey.setLastUsedAt(lastUsedAt);
        return this;
    }

    @NonNull
    public ApiKeyBuilder withRevokedAt(OffsetDateTime revokedAt) {
        apiKey.setRevokedAt(revokedAt);
        return this;
    }

    @NonNull
    public static ApiKey build(Consumer<ApiKeyBuilder> block) {
        ApiKeyBuilder builder = new ApiKeyBuilder();
        block.accept(builder);
        return builder.apiKey;
    }
}
