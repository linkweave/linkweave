package org.chainlink.api.shared.user;

import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@RequiredArgsConstructor
public class UserSettingsRepo extends BaseRepo<UserSettings> {

    @NonNull
    public Optional<UserSettings> findByUserId(@NonNull ID<User> userId) {
        return db.selectFrom(QUserSettings.userSettings)
            .where(QUserSettings.userSettings.user.id.eq(userId.getUUID()))
            .fetchOne();
    }

    @NonNull
    public UserSettings getOrCreateForUser(@NonNull User user) {
        return findByUserId(user.getId())
            .orElseGet(() -> {
                UserSettings settings = new UserSettings(user);
                db.persist(settings);
                return settings;
            });
    }
}
