package org.chainlink.api.bookmark.importbookmarks;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Getter
@Setter
@RequiredArgsConstructor
@JaxDTO
public class ImportSummaryJson {

    @NotNull
    @NonNull
    String importTag;

    int foldersCreated;

    int bookmarksCreated;

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
