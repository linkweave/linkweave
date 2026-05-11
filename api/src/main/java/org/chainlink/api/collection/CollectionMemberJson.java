package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionMemberJson {

    @NotNull @NonNull @Schema(required = true) ID<User> userId;
    @NotNull @NonNull @Schema(required = true) String email;
    @NotNull @NonNull @Schema(required = true) String displayName;
    @NotNull @NonNull @Schema(required = true) CollectionRole role;
}
