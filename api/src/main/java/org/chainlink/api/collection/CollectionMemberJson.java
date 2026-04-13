package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionMemberJson {

    @NotNull @NonNull ID<User> userId;
    @NotNull @NonNull String email;
    @NotNull @NonNull String displayName;
    @NotNull @NonNull CollectionRole role;
}
