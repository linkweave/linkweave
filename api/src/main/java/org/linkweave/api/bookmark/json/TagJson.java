package org.linkweave.api.bookmark.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class TagJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Tag> id;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    EntityInfoJson entityInfo;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    TagSaveJson data;
}
