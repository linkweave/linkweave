package org.chainlink.api.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@NoArgsConstructor(force = true)
@JaxDTO
public class CollectionCreateJson {

    @NotNull
    @NonNull
    @NotBlank
    @Schema(required = true)
    String name;
}
