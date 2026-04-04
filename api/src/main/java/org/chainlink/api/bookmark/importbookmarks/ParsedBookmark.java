package org.chainlink.api.bookmark.importbookmarks;

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
}
