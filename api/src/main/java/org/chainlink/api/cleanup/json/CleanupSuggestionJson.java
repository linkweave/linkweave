package org.chainlink.api.cleanup.json;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class CleanupSuggestionJson {

    @NotNull
    @NonNull
    String id;

    @NotNull
    @NonNull
    String title;

    @NotNull
    @NonNull
    String url;

    @Nullable
    String folderName;

    int clickCount;

    @Nullable
    OffsetDateTime lastClickedAt;

    @NotNull
    @NonNull
    OffsetDateTime createdAt;

    boolean neverClicked;
}
