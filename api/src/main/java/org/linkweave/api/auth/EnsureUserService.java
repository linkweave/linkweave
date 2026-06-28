package org.linkweave.api.auth;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import org.linkweave.api.types.emailaddress.EmailAddress;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.auth.FachRolle;
import org.linkweave.api.shared.user.AuthProvider;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.shared.user.UserSettings;
import org.linkweave.api.shared.user.UserSettingsRepo;
import org.linkweave.api.shared.util.EnumSetUtil;
import org.linkweave.infrastructure.stereotypes.Service;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnsureUserService {

    private final SecurityIdentity identity;
    private final UserRepo userRepo;
    private final UserSettingsRepo userSettingsRepo;

    @Transactional(TxType.NOT_SUPPORTED)
    @NonNull
    public User ensureUserExists() {

        String email = identity.getPrincipal().getName();

        Optional<User> existingUserOpt = userRepo.findByEmail(
            EmailAddress.fromString(email));

        if (existingUserOpt.isPresent()) {
            LOG.debug("User already exists: {}", email);
            return existingUserOpt.get();
        }

        return createUserFromOIDC(email);
    }

    private @NonNull User createUserFromOIDC(String email) {

        LOG.info("Auto-provisioning new user from OIDC: {}", email);

        Map<String, Object> attributes = identity.getAttributes();
        String givenName = (String) attributes.get("given_name");
        String familyName = (String) attributes.get("family_name");

        String vorname = (givenName != null && !givenName.isBlank()) ? givenName : email.split("@")[0];
        String nachname = (familyName != null && !familyName.isBlank()) ? familyName : "";

        String keycloakId = (String) attributes.get("sub");

        User newUser = new User(
            keycloakId,
            EmailAddress.fromString(email),
            null,
            nachname,
            vorname,
            null,
            null,
            AuthProvider.OIDC,
            EnumSetUtil.toString(java.util.EnumSet.of(FachRolle.USER)),
            new HashSet<>(),
            true,
            null,
            email
        );

        try {
            userRepo.provisionNewUser(newUser);
            userSettingsRepo.provisionSettings(new UserSettings(newUser));
            LOG.info("Created new user: {}", newUser.getEmail());
            return newUser;
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                LOG.info("User already created concurrently: {}", email);
                return userRepo.findByEmail(EmailAddress.fromString(email))
                    .orElseThrow(() -> e);
            }
            throw e;
        }
    }
}
