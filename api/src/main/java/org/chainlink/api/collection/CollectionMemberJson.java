package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionMemberJson {

    @NonNull ID<User> userId;
    @NonNull String email;
    @NonNull String displayName;
    @NonNull CollectionRole role;
}
