package org.chainlink.api.testutil.builder;

import java.util.EnumSet;
import java.util.function.Consumer;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.elytron.security.common.BcryptUtil;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.auth.FachRolle;
import org.chainlink.api.shared.user.AuthProvider;
import org.chainlink.api.shared.user.User;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class UserBuilder {

    private final User user;

    public UserBuilder() {
        this.user = defaultUser();
    }

    @NonNull
    public static User defaultUser() {
        User user = new User();
        user.setEmail(EmailAddress.fromString("test@example.com"));
        user.setVorname("Test");
        user.setNachname("User");
        user.setAktiv(true);
        return user;
    }

    @NonNull
    public UserBuilder withEmail(String email) {
        user.setEmail(EmailAddress.fromString(email));
        return this;
    }

    @NonNull
    public UserBuilder withVorname(String vorname) {
        user.setVorname(vorname);
        return this;
    }

    @NonNull
    public UserBuilder withNachname(String nachname) {
        user.setNachname(nachname);
        return this;
    }

    @NonNull
    public UserBuilder withKeycloakId(String keycloakId) {
        user.setKeycloakId(keycloakId);
        return this;
    }

    @NonNull
    public UserBuilder withFachRollen(EnumSet<FachRolle> rollen) {
        user.setFachRollen(rollen);
        return this;
    }

    @NonNull
    public UserBuilder withAuthProvider(AuthProvider authProvider) {
        user.setAuthProvider(authProvider);
        return this;
    }

    @NonNull
    public UserBuilder withPlaintextPassword(String plaintextPassword) {
        user.setPassword(BcryptUtil.bcryptHash(plaintextPassword));
        return this;
    }

    @NonNull
    public UserBuilder withAktiv(boolean aktiv) {
        user.setAktiv(aktiv);
        return this;
    }

    @NonNull
    public static User build(Consumer<UserBuilder> block) {
        UserBuilder builder = new UserBuilder();
        block.accept(builder);
        return builder.user;
    }
}
