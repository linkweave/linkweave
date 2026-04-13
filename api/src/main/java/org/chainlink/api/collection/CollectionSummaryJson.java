package org.chainlink.api.collection;

import java.util.UUID;

import ch.dvbern.dvbstarter.types.id.ID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
public class CollectionSummaryJson {

    @NonNull ID<Collection> id;
    @NonNull String name;
    @JsonProperty("isDefault")
    boolean isDefault;
    @NonNull CollectionRole role;
    boolean shared;

    CollectionSummaryJson(
        @NonNull ID<Collection> id,
        @NonNull String name,
        boolean isDefault,
        @NonNull CollectionRole role,
        boolean shared) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
        this.role = role;
        this.shared = shared;
    }

    @QueryProjection
    public CollectionSummaryJson(
        @NonNull UUID id,
        @NonNull String name,
        boolean isDefault,
        @NonNull CollectionRole role,
        boolean shared) {
        this(ID.of(id, Collection.class), name, isDefault, role, shared);
    }
}
