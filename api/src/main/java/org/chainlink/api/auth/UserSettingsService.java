package org.chainlink.api.auth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.shared.user.UserSettings;
import org.chainlink.api.shared.user.UserSettingsRepo;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {

    private final UserSettingsRepo userSettingsRepo;
    private final CurrentUserService currentUserService;

    @NonNull
    public UserSettingsJson getSettings() {
        User user = currentUserService.currentUser();
        UserSettings settings = userSettingsRepo.getOrCreateForUser(user);
        return toJson(settings);
    }

    @Transactional
    @NonNull
    public UserSettingsJson updateSettings(@NonNull UserSettingsUpdateJson update) {
        User user = currentUserService.currentUser();
        UserSettings settings = userSettingsRepo.getOrCreateForUser(user);
        settings.setOfflineCachingEnabled(update.offlineCachingEnabled());
        return toJson(settings);
    }

    @NonNull
    public UserSettingsJson getSettingsForUser(@NonNull User user) {
        UserSettings settings = userSettingsRepo.getOrCreateForUser(user);
        return toJson(settings);
    }

    private UserSettingsJson toJson(@NonNull UserSettings settings) {
        return new UserSettingsJson(settings.isOfflineCachingEnabled());
    }
}
