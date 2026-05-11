package org.chainlink.api.collection;

import java.util.UUID;

import ch.dvbern.dvbstarter.types.id.ID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.querydsl.core.annotations.QueryProjection;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
public class CollectionSummaryJson {

    @NotNull @NonNull @Schema(required = true) ID<Collection> id;
    @NotNull @NonNull @Schema(required = true) String name;
    @JsonProperty("isDefault")
    @Schema(required = true)
    boolean isDefault;
    @NotNull @NonNull @Schema(required = true) CollectionRole role;
    @Schema(required = true) boolean shared;

    CollectionSummaryJson(
        @NotNull @NonNull ID<Collection> id,
        @NotNull @NonNull String name,
        boolean isDefault,
        @NotNull @NonNull CollectionRole role,
        boolean shared) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
        this.role = role;
        this.shared = shared;
    }

    @QueryProjection
    public CollectionSummaryJson(
        @NotNull @NonNull UUID id,
        @NotNull @NonNull String name,
        boolean isDefault,
        @NotNull @NonNull CollectionRole role,
        boolean shared) {
        this(ID.of(id, Collection.class), name, isDefault, role, shared);
    }
}
