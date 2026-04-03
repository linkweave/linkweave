package org.chainlink.api.bookmark.importbookmarks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.junit.jupiter.api.Test;

class NetscapeBookmarkParserTest {

    private final NetscapeBookmarkParser parser = new NetscapeBookmarkParser();

    private InputStream loadFixture(String name) throws Exception {
        return Files.newInputStream(Path.of(
            getClass().getClassLoader().getResource("__files/" + name).toURI()));
    }

    @Test
    void shouldParseSampleBookmarks() throws Exception {
        try (InputStream is = loadFixture("bookmarks-sample.html")) {
            ParsedImportResult result = parser.parse(is);

            Assertions.assertThat(result.rootFolders()).hasSize(1);
            Assertions.assertThat(result.rootBookmarks()).isEmpty();

            ParsedFolder bookmarksBar = result.rootFolders().get(0);
            Assertions.assertThat(bookmarksBar.getName()).isEqualTo("Bookmarks Bar");
            Assertions.assertThat(bookmarksBar.getBookmarks()).hasSize(1);
            Assertions.assertThat(bookmarksBar.getBookmarks().get(0).getTitle()).isEqualTo("Example Title");
            Assertions.assertThat(bookmarksBar.getBookmarks().get(0).getUrl()).isEqualTo("https://example.com/");

            Assertions.assertThat(bookmarksBar.getFolders()).hasSize(1);
            ParsedFolder subfolder = bookmarksBar.getFolders().get(0);
            Assertions.assertThat(subfolder.getName()).isEqualTo("Subfolder");
            Assertions.assertThat(subfolder.getBookmarks()).hasSize(1);
            Assertions.assertThat(subfolder.getBookmarks().get(0).getTitle()).isEqualTo("Another Title");
            Assertions.assertThat(subfolder.getBookmarks().get(0).getUrl()).isEqualTo("https://example.org");
        }
    }

    @Test
    void shouldParseBookmarksWithRootLevelLinks() throws Exception {
        try (InputStream is = loadFixture("bookmarks-with-root.html")) {
            ParsedImportResult result = parser.parse(is);

            Assertions.assertThat(result.rootBookmarks()).hasSize(2);
            Assertions.assertThat(result.rootBookmarks().get(0).getTitle()).isEqualTo("Root Bookmark 1");
            Assertions.assertThat(result.rootBookmarks().get(1).getTitle()).isEqualTo("Root Bookmark 2");

            Assertions.assertThat(result.rootFolders()).hasSize(1);
            Assertions.assertThat(result.rootFolders().get(0).getName()).isEqualTo("Folder A");
            Assertions.assertThat(result.rootFolders().get(0).getBookmarks()).hasSize(1);
        }
    }

    @Test
    void shouldParseEmptyBookmarksFile() throws Exception {
        try (InputStream is = loadFixture("bookmarks-empty.html")) {
            ParsedImportResult result = parser.parse(is);

            Assertions.assertThat(result.rootFolders()).isEmpty();
            Assertions.assertThat(result.rootBookmarks()).isEmpty();
        }
    }

    @Test
    void shouldThrowValidationException_whenNoDlElement() {
        String html = "<html><body><p>No bookmarks here</p></body></html>";
        InputStream is = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));

        Assertions.assertThatThrownBy(() -> parser.parse(is))
            .isInstanceOf(AppValidationException.class);
    }

    @Test
    void shouldThrowValidationException_whenInvalidHtml() {
        InputStream is = new ByteArrayInputStream(new byte[]{0x00, 0x01, 0x02});
        Assertions.assertThatThrownBy(() -> parser.parse(is))
            .isInstanceOf(AppValidationException.class);
    }
}
