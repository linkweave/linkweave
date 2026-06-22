package org.linkweave.api.collection;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class CollectionMemberListJson {

    @NotNull
    @NonNull
    List<CollectionMemberJson> members;
}
