package org.chainlink.api.auth.apikey;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@RequiredArgsConstructor
public class ApiKeyRepo extends BaseRepo<ApiKey> {

    /**
     * Debounce window for {@code last_used_at} writes (BR-014, UC-078). A key authenticated
     * more often than this only records its first use per window, avoiding a row write on every
     * authenticated request while keeping the displayed "last used" fresh to within the window.
     * Shared with {@link ApiKeyService}, which uses it for an in-memory fast path before touching
     * the database.
     */
    public static final Duration LAST_USED_DEBOUNCE = Duration.ofMinutes(5);

    private final AppClock appClock;

    @NonNull
    public List<ApiKey> findByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QApiKey.apiKey)
            .where(QApiKey.apiKey.user.id.eq(userId.getUUID()))
            .orderBy(QApiKey.apiKey.timestampErstellt.desc())
            .fetch();
    }

    public long countActiveByUser(@NonNull ID<User> userId) {
        var now = appClock.offsetDateTime().now();
        return db.select(QApiKey.apiKey.id.count())
            .from(QApiKey.apiKey)
            .where(QApiKey.apiKey.user.id.eq(userId.getUUID()))
            .where(QApiKey.apiKey.revokedAt.isNull())
            .where(QApiKey.apiKey.expiresAt.isNull().or(QApiKey.apiKey.expiresAt.gt(now)))
            .fetchOne()
            .orElse(0L);
    }

    @NonNull
    public Optional<ApiKey> findByIdAndUser(@NonNull ID<ApiKey> apiKeyId, @NonNull ID<User> userId) {
        return db.selectFrom(QApiKey.apiKey)
            .where(QApiKey.apiKey.id.eq(apiKeyId.getUUID()))
            .where(QApiKey.apiKey.user.id.eq(userId.getUUID()))
            .fetchOne();
    }

    @NonNull
    public Optional<ApiKey> findActiveByHash(@NonNull String keyHash) {
        var now = appClock.offsetDateTime().now();
        return db.selectFrom(QApiKey.apiKey)
            .where(QApiKey.apiKey.keyHash.eq(keyHash))
            .where(QApiKey.apiKey.revokedAt.isNull())
            .where(QApiKey.apiKey.expiresAt.isNull().or(QApiKey.apiKey.expiresAt.gt(now)))
            .where(QApiKey.apiKey.user.aktiv.isTrue())
            .fetchOne();
    }

    /**
     * Records the most recent use of a key, debounced to at most one write per
     * LAST_USED_DEBOUNCE_MINUTES. The time guard lives in the {@code WHERE} clause so it
     * is evaluated atomically by the database — a read-then-write in Java would let concurrent
     * requests with the same key both see a stale timestamp and both write.
     *
     * @return the number of rows updated (0 when within the debounce window)
     */
    public long updateLastUsedAt(@NonNull ID<ApiKey> apiKeyId) {
        var now = appClock.offsetDateTime().now();
        var threshold = now.minus(LAST_USED_DEBOUNCE);
        return db.update(QApiKey.apiKey)
            .set(QApiKey.apiKey.lastUsedAt, now)
            .where(QApiKey.apiKey.id.eq(apiKeyId.getUUID()))
            .where(QApiKey.apiKey.lastUsedAt.isNull()
                .or(QApiKey.apiKey.lastUsedAt.lt(threshold)))
            .execute();
    }
}
