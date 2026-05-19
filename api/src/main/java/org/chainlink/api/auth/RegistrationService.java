package org.chainlink.api.auth;

import java.util.EnumSet;
import java.util.HashSet;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.shared.auth.FachRolle;
import org.chainlink.api.shared.user.AuthProvider;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.shared.user.UserSettings;
import org.chainlink.api.shared.user.UserSettingsRepo;
import org.chainlink.api.shared.util.EnumSetUtil;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepo userRepo;
    private final UserSettingsRepo userSettingsRepo;

    @NonNull
    @Transactional(TxType.NOT_SUPPORTED)
    public User register(@NonNull String email, @NonNull String password, @NonNull String vorname, @NonNull String nachname) {
        EmailAddress emailAddress = EmailAddress.fromString(email);

        if (userRepo.findByEmail(emailAddress).isPresent()) {
            throw new AppValidationException(AppValidationMessage.emailAlreadyRegistered(email));
        }

        String hashedPassword = BcryptUtil.bcryptHash(password);

        User newUser = new User(
            null,
            emailAddress,
            null,
            nachname,
            vorname,
            null,
            hashedPassword,
            AuthProvider.FORM,
            EnumSetUtil.toString(EnumSet.of(FachRolle.USER)),
            HashSet.newHashSet(0),
            true,
            null,
            email
        );

        try {
            userRepo.provisionNewUser(newUser);
            userSettingsRepo.provisionSettings(new UserSettings(newUser));
            LOG.info("Registered new form user: {}", email);
            return newUser;
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
               throw new AppValidationException(AppValidationMessage.emailAlreadyRegistered(email));
            }
            throw e;
        }
    }
}
