package org.chainlink.api.bookmark.importbookmarks;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ParsedBookmark {

    private String title;
    private String url;
}
