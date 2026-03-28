package org.chainlink.infrastructure.errorhandling.json;

import java.util.Collection;
import java.util.List;

import org.chainlink.infrastructure.errorhandling.FailureType;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Value
@JaxDTO
@EqualsAndHashCode(callSuper = true)
@Schema(hidden = false)  // Ensures it appears in documentation
public class AppValidationErrorJson extends AppErrorJson {

    @NonNull
    @NotNull
    @Schema(required = true)
    private final List<@Valid ViolationJson> violations;

    public AppValidationErrorJson(@NonNull FailureType type, @NonNull List<@Valid ViolationJson> violations) {
        super(type);
        this.violations = violations;
    }

    public AppValidationErrorJson(@NonNull Collection<ViolationJson> violations) {
        super(FailureType.VALIDATION);
        this.violations = List.copyOf(violations);
    }
}
