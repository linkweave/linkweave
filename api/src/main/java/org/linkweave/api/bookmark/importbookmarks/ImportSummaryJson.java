package org.linkweave.api.bookmark.importbookmarks;

import lombok.Getter;
import lombok.Setter;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@JaxDTO
public class ImportSummaryJson {

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
