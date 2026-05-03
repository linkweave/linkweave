package org.chainlink.api.bookmark.json;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class AutoTagRuleListJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    List<AutoTagRuleJson> rules;
}
