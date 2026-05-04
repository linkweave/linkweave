package org.chainlink.api.cleanup.json;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class MoveToTrashJson {

    @NotNull
    @NonNull
    String collectionId;

    @NotEmpty
    @NonNull
    List<String> bookmarkIds;
}
