package org.linkweave.api.bookmark.importbookmarks;

import java.util.List;

public record ParsedImportResult(
    List<ParsedFolder> rootFolders,
    List<ParsedBookmark> rootBookmarks
) {
}
