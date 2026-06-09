package org.chainlink.api.auth.apikey;

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
            .fetchOne();
    }

    public void updateLastUsedAt(@NonNull ID<ApiKey> apiKeyId) {
        db.update(QApiKey.apiKey)
            .set(QApiKey.apiKey.lastUsedAt, appClock.offsetDateTime().now())
            .where(QApiKey.apiKey.id.eq(apiKeyId.getUUID()))
            .execute();
    }
}
