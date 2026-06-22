package org.linkweave.api.bookmark.importbookmarks;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ParsedFolder {

    private String name;
    private List<ParsedFolder> folders = new ArrayList<>();
    private List<ParsedBookmark> bookmarks = new ArrayList<>();

    public ParsedFolder(String name) {
        this.name = name;
    }
}
