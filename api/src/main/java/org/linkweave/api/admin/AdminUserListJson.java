package org.linkweave.api.admin;

import java.util.List;

import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@JaxDTO
@AllArgsConstructor
public class AdminUserListJson {

    @NotNull
    @NonNull
    List<AdminUserJson> users;
}
