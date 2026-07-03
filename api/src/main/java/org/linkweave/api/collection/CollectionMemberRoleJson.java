package org.linkweave.api.collection;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

/**
 * Request body for {@code PUT /collections/{id}/members/{userId}} — changes a
 * member's role. The OWNER role cannot be assigned here (the owner is fixed);
 * only transitions between {@link CollectionRole#MEMBER} and
 * {@link CollectionRole#ADMIN} are permitted.
 */
@Value
@JaxDTO
@AllArgsConstructor
public class CollectionMemberRoleJson {

    @NotNull @NonNull
    CollectionRole role;
}
