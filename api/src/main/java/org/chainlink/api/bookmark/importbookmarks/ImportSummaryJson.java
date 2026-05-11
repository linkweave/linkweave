package org.chainlink.api.bookmark.importbookmarks;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Getter
@Setter
@RequiredArgsConstructor
@JaxDTO
public class ImportSummaryJson {

    @NotNull @NonNull @Schema(required = true)
    String importTag;

    @Schema(required = true)
    int foldersCreated;

    @Schema(required = true)
    int bookmarksCreated;

    @Schema(required = true)
    int bookmarksSkipped;

    public void incrementFoldersCreated() {
        foldersCreated++;
    }

    public void incrementBookmarksCreated() {
        bookmarksCreated++;
    }

    public void incrementBookmarksSkipped() {
        bookmarksSkipped++;
    }
}
