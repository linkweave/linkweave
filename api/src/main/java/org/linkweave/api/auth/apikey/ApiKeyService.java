package org.linkweave.api.auth.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.linkweave.infrastructure.clock.AppClock;
import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.auth.apikey.json.ApiKeyCreateJson;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.db.DbConst;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.security.SecureRandomProvider;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    public static final String KEY_PREFIX = "lw_";

    public record ApiKeyCreateResult(ApiKey apiKey, String rawKey) {}

    private static final int KEY_BYTES = 32;
    private static final int PREFIX_LENGTH = DbConst.DB_API_KEY_PREFIX_LENGTH;
    private static final int MAX_ACTIVE_KEYS = 10;

    private final ApiKeyRepo apiKeyRepo;
    private final CurrentUserService currentUserService;
    private final AppClock appClock;
    private final SecureRandomProvider secureRandomProvider;

    @NonNull
    public ApiKeyCreateResult createApiKey(@NonNull ApiKeyCreateJson json) {
        ID<User> userId = currentUserService.currentUserID();
        long activeCount = apiKeyRepo.countActiveByUser(userId);
        if (activeCount >= MAX_ACTIVE_KEYS) {
            throw new AppValidationException(AppValidationMessage.maxApiKeysReached());
        }

        byte[] rawBytes = new byte[KEY_BYTES];
        secureRandomProvider.nextBytes(rawBytes);
        String rawHex = HexFormat.of().formatHex(rawBytes);
        String fullKey = KEY_PREFIX + rawHex;
        String keyHash = sha256Hex(rawHex);
        String keyPrefix = rawHex.substring(0, PREFIX_LENGTH);

        OffsetDateTime expiresAt = computeExpiresAt(json.getExpiresIn());

        ApiKey apiKey = new ApiKey(
            currentUserService.currentUserRef(),
            json.getName().trim(),
            keyHash,
            keyPrefix,
            expiresAt
        );

        apiKeyRepo.persistAndFlush(apiKey);
        return new ApiKeyCreateResult(apiKey, fullKey);
    }

    @NonNull
    public List<ApiKey> listApiKeysForCurrentUser() {
        ID<User> userId = currentUserService.currentUserID();
        return apiKeyRepo.findByUser(userId);
    }

    public void revokeApiKey(@NonNull ID<ApiKey> apiKeyId) {
        ApiKey apiKey = apiKeyRepo.findByIdAndUser(apiKeyId, currentUserService.currentUserID())
            .orElseThrow(() -> new AppValidationException(AppValidationMessage.genericMessage("AppValidation.API_KEY_NOT_FOUND")));
        if (apiKey.getRevokedAt() != null) {
            throw new AppValidationException(AppValidationMessage.apiKeyAlreadyRevoked());
        }
        apiKey.setRevokedAt(appClock.offsetDateTime().now());
        apiKeyRepo.persistAndFlush(apiKey);
    }

    @NonNull
    public Optional<ApiKeyIdentityProvider.ApiKeyIdentityData> buildIdentityFromApiKey(@NonNull String keyHash) {
        return apiKeyRepo.findActiveByHash(keyHash).map(apiKey -> {

            User user = apiKey.getUser();

            String securityRoles = user.getSecurityRoles();
            Set<String> roles = securityRoles.isBlank()
                ? Set.of()
                : Set.of(securityRoles.split(","));

            return new ApiKeyIdentityProvider.ApiKeyIdentityData(
                apiKey.getId(),
                user.getEmail().toString(),
                roles,
                apiKey.getLastUsedAt()
            );
        });
    }

    /**
     * Records that a key was just used, best-effort (never fails the request). The key was already
     * loaded during authentication, so we use its in-memory {@code lastUsedAt} as a fast path: when
     * it is still within the debounce window we skip the database entirely. The repo's
     * {@code WHERE}-clause guard then handles the concurrent case where two requests both pass this
     * check (see {@link ApiKeyRepo#updateLastUsedAt}).
     */
    public void recordApiKeyUse(@NonNull ID<ApiKey> apiKeyId, @Nullable OffsetDateTime lastUsedAt) {
        OffsetDateTime now = appClock.offsetDateTime().now();
        if (lastUsedAt != null && lastUsedAt.isAfter(now.minus(ApiKeyRepo.LAST_USED_DEBOUNCE))) {
            return;
        }
        try {
            apiKeyRepo.updateLastUsedAt(apiKeyId);
        } catch (Exception e) {
            log.warn("Failed to update last_used_at for API key {}", apiKeyId, e);
        }
    }

    @NonNull
    static String sha256Hex(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Nullable
    private OffsetDateTime computeExpiresAt(@Nullable String expiresIn) {
        ApiKeyExpiration expiration = ApiKeyExpiration.fromValue(expiresIn).orElse(ApiKeyExpiration.NEVER);
        return expiration.computeExpiresAt(appClock.offsetDateTime().now());
    }
}
