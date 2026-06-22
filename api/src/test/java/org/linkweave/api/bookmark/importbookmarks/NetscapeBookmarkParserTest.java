package org.linkweave.api.bookmark.importbookmarks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.assertj.core.api.Assertions;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
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
            Assertions.assertThat(bookmarksBar.getBookmarks().get(0).getDescription()).isEqualTo("A sample description for the example bookmark");
            // Netscape ADD_DATE is Unix epoch seconds; the fixture uses 1234567890 → 2009-02-13T23:31:30Z.
            Assertions.assertThat(bookmarksBar.getBookmarks().get(0).getAddedAt()).isEqualTo(Instant.ofEpochSecond(1234567890L));

            Assertions.assertThat(bookmarksBar.getFolders()).hasSize(1);
            ParsedFolder subfolder = bookmarksBar.getFolders().get(0);
            Assertions.assertThat(subfolder.getName()).isEqualTo("Subfolder");
            Assertions.assertThat(subfolder.getBookmarks()).hasSize(1);
            Assertions.assertThat(subfolder.getBookmarks().get(0).getTitle()).isEqualTo("Another Title");
            Assertions.assertThat(subfolder.getBookmarks().get(0).getUrl()).isEqualTo("https://example.org");
            Assertions.assertThat(subfolder.getBookmarks().get(0).getDescription()).isEqualTo("Description for another title");
        }
    }

    @Test
    void shouldParseBookmarksWithRootLevelLinks() throws Exception {
        try (InputStream is = loadFixture("bookmarks-with-root.html")) {
            ParsedImportResult result = parser.parse(is);

            Assertions.assertThat(result.rootBookmarks()).hasSize(2);
            Assertions.assertThat(result.rootBookmarks().get(0).getTitle()).isEqualTo("Root Bookmark 1");
            Assertions.assertThat(result.rootBookmarks().get(0).getDescription()).isEqualTo("Root bookmark 1 description");
            Assertions.assertThat(result.rootBookmarks().get(1).getTitle()).isEqualTo("Root Bookmark 2");
            Assertions.assertThat(result.rootBookmarks().get(1).getDescription()).isNull();

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
    void shouldReturnNullAddedAt_whenAddDateMissingOrInvalid() throws Exception {
        String html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://no-date.example.com">No date here</A>
                <DT><A HREF="https://blank.example.com" ADD_DATE="">Blank date</A>
                <DT><A HREF="https://garbage.example.com" ADD_DATE="not-a-number">Garbage date</A>
                <DT><A HREF="https://zero.example.com" ADD_DATE="0">Zero date</A>
            </DL>
            """;
        ParsedImportResult result = parser.parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertThat(result.rootBookmarks())
            .extracting(ParsedBookmark::getAddedAt)
            .containsExactly(null, null, null, null);
    }

    @Test
    void shouldThrowValidationException_whenInvalidHtml() {
        InputStream is = new ByteArrayInputStream(new byte[]{0x00, 0x01, 0x02});
        Assertions.assertThatThrownBy(() -> parser.parse(is))
            .isInstanceOf(AppValidationException.class);
    }
}
