package org.linkweave.api.shared.user;

import java.util.Optional;

import org.linkweave.api.types.emailaddress.EmailAddress;
import org.linkweave.api.types.id.ID;

public interface CurrentUserService {
    ID<User> currentUserID();

    EmailAddress currentUserName();

    Optional<User> findCurrentUser();

    User currentUser();

    User currentUserRef();
}
