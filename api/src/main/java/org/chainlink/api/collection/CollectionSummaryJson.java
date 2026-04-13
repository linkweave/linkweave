package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionSummaryJson {

    @NonNull ID<Collection> id;
    @NonNull String name;
    @JsonProperty("isDefault") boolean isDefault;
    @NonNull CollectionRole role;
}
