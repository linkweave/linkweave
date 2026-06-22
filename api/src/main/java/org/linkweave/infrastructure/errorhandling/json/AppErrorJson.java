package org.linkweave.infrastructure.errorhandling.json;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import org.linkweave.infrastructure.errorhandling.FailureType;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@ToString
public abstract class AppErrorJson {

    @NonNull
    @NotNull
    @Schema(required = true)
    private final FailureType type;

    protected AppErrorJson(@NonNull FailureType type) {
        this.type = type;
    }

}
