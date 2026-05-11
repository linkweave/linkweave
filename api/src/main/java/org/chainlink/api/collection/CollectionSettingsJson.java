package org.chainlink.api.collection;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Value
@NonFinal
@JaxDTO
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CollectionSettingsJson {

    @Nullable @Schema(required = false) String layout;
}
