package org.linkweave.api.shared.user;

import java.util.Optional;

import org.linkweave.infrastructure.runas.RunAs;
import org.linkweave.api.types.id.ID;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import org.linkweave.infrastructure.db.BaseRepo;
import org.linkweave.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

import static org.linkweave.api.shared.auth.BerechtigungName.SYSTEM_ADMIN;

@Repository
@RequiredArgsConstructor
public class UserSettingsRepo extends BaseRepo<UserSettings> {

    @NonNull
    public Optional<UserSettings> findByUserId(@NonNull ID<User> userId) {
        return db.selectFrom(QUserSettings.userSettings)
            .where(QUserSettings.userSettings.user.id.eq(userId.getUUID()))
            .fetchOne();
    }

    @Transactional(TxType.REQUIRES_NEW)
    @RunAs(username = "sysadmin", roles = {SYSTEM_ADMIN})
    public void provisionSettings(@NonNull UserSettings settings) {
        db.persist(settings);
    }

    public void deleteByUserId(@NonNull ID<User> userId) {
        db.delete(QUserSettings.userSettings)
            .where(QUserSettings.userSettings.user.id.eq(userId.getUUID()))
            .execute();
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
