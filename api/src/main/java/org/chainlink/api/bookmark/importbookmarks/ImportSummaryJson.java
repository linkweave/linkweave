package org.chainlink.api.bookmark.importbookmarks;

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

    @NonNull
    String importTag;

    int foldersCreated;

    int bookmarksCreated;

    public void incrementFoldersCreated() {
        foldersCreated++;
    }

    public void incrementBookmarksCreated() {
        bookmarksCreated++;
    }
}
