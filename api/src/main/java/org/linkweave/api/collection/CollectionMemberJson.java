package org.linkweave.api.collection;

import org.linkweave.api.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
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
