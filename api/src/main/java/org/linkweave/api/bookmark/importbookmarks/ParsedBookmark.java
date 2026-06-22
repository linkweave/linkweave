package org.linkweave.api.bookmark.importbookmarks;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParsedBookmark {

    private String title;
    private String url;
    @Nullable
    private String description;
    /**
     * Original creation date carried by the Netscape `ADD_DATE` attribute on
     * the imported `<A>` element. Null if the source file omitted it. Caller
     * decides whether to honor it or fall back to the import time.
     */
    @Nullable
    private Instant addedAt;
}
