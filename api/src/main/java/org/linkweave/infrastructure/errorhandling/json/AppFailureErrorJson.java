package org.linkweave.infrastructure.errorhandling.json;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.linkweave.infrastructure.errorhandling.ExceptionId;
import org.linkweave.infrastructure.errorhandling.FailureType;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Getter
@JaxDTO
@Schema(hidden = false)  // Ensures it appears in documentation
public class AppFailureErrorJson extends AppErrorJson {

    @NonNull
    @NotNull
    @Valid
    @Schema(required = true)
    @IgnoreForIdClassTest
    private final ExceptionId id;

    @NonNull
    @NotNull
    @NotEmpty
    @Schema(required = true)
    private final String summary;

    public AppFailureErrorJson(@NonNull ExceptionId id, @NonNull String summary) {
        super(FailureType.FAILURE);
        this.id = id;
        this.summary = summary;
    }

    public AppFailureErrorJson(
        @NonNull ExceptionId id,
        @NonNull String summary,
        @NonNull String detail
    ) {
        super(FailureType.FAILURE);
        this.id = id;
        this.summary = summary + ", detail: " + detail;
    }
}
