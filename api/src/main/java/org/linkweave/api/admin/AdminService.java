package org.linkweave.api.admin;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.auth.FachRolle;
import org.linkweave.api.shared.user.AuthProvider;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;

import io.quarkus.elytron.security.common.BcryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    /**
     * Visually unambiguous alphabet — no 0/O, 1/I/l. Keeps the relayed-over-the-phone
     * password legible while still well above the 8-char minimum enforced by the
     * registration validator.
     */
    private static final char[] PASSWORD_ALPHABET = (
        "ABCDEFGHJKLMNPQRSTUVWXYZ"
            + "abcdefghijkmnopqrstuvwxyz"
            + "23456789"
    ).toCharArray();

    private static final int PASSWORD_LENGTH = 16;

    private final UserRepo userRepo;
    private final SecureRandom random = new SecureRandom();

    @NonNull
    public AdminUserListJson listAllUsers() {
        List<AdminUserJson> users = userRepo.findAll().stream()
            .map(this::toAdminUserJson)
            .sorted(Comparator
                .comparing(AdminUserJson::getLastName, String::compareToIgnoreCase)
                .thenComparing(AdminUserJson::getFirstName, String::compareToIgnoreCase))
            .toList();
        return new AdminUserListJson(users);
    }

    @NonNull
    public PasswordResetResultJson resetUserPassword(@NonNull ID<User> userId) {
        if (userId.equals(User.getSystemAdminId())) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.ADMIN_CANNOT_RESET_SYSTEM_ADMIN"));
        }

        User user = userRepo.getById(userId);

        if (user.getAuthProvider() != null && user.getAuthProvider() != AuthProvider.FORM) {
            LOG.warn(
                "Resetting local password for user {} whose auth provider is {} — the actual login system is " +
                    "delegated, so this only affects form-based login if it is enabled.",
                user.getEmail(), user.getAuthProvider());
        }

        String newPassword = generateRandomPassword();
        user.setPassword(BcryptUtil.bcryptHash(newPassword));

        LOG.info("Support reset password for user {}", user.getEmail());

        return new PasswordResetResultJson(newPassword);
    }

    @NonNull
    private AdminUserJson toAdminUserJson(@NonNull User user) {
        Set<FachRolle> roles = user.getFachRollen();
        return new AdminUserJson(
            user.getId(),
            user.getEmail().toString(),
            user.getVorname(),
            user.getNachname(),
            user.isAktiv(),
            user.getAuthProvider(),
            roles
        );
    }

    @NonNull
    private String generateRandomPassword() {
        char[] buffer = new char[PASSWORD_LENGTH];
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            buffer[i] = PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)];
        }
        return new String(buffer);
    }
}
