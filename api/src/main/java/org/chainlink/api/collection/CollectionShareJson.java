package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionShareJson {

    @NonNull
    EmailAddress email;
}
