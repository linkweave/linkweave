package org.linkweave.api.collection;

import org.linkweave.api.types.emailaddress.EmailAddress;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionShareJson {

    @NotNull @NonNull
    EmailAddress email;
}
