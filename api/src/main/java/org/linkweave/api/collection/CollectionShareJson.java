package org.linkweave.api.collection;

import org.linkweave.api.types.emailaddress.EmailAddress;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionShareJson {

    @NotNull @NonNull
    EmailAddress email;

    /**
     * Optional role to assign to the invited user. Defaults to {@link CollectionRole#MEMBER}
     * when {@code null}. Only an owner may assign {@link CollectionRole#ADMIN}.
     */
    @Nullable
    CollectionRole role;
}
