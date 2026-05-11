package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionShareJson {

    @NotNull @NonNull @Schema(required = true)
    EmailAddress email;
}
