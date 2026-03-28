package org.chainlink.api.shared.user;

import java.util.Optional;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;

public interface CurrentUserService {
    ID<User> currentUserID();

    EmailAddress currentUserName();

    Optional<User> findCurrentUser();

    User currentUser();

    User currentUserRef();
}
