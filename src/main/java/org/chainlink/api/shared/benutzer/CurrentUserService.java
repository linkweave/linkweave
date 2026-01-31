package org.chainlink.api.shared.benutzer;

import java.util.Optional;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;

public interface CurrentUserService {
    ID<Benutzer> currentUserID();

    EmailAddress currentUserName();

    Optional<Benutzer> findCurrentUser();

    Benutzer currentUser();

    Benutzer currentUserRef();
}
