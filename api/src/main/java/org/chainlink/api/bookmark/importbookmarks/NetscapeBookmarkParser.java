package org.chainlink.api.bookmark.importbookmarks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.jspecify.annotations.NonNull;


public class NetscapeBookmarkParser {

    @NonNull
    public ParsedImportResult parse(@NonNull InputStream inputStream) {
        try {
            Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");

            Element firstDl = doc.selectFirst("dl");
            if (firstDl == null) {
                throw new AppValidationException(
                    AppValidationMessage.uploadProblem("Invalid bookmark file format. Please export from your browser and try again."));
            }

            List<ParsedFolder> rootFolders = new ArrayList<>();
            List<ParsedBookmark> rootBookmarks = new ArrayList<>();

            parseChildren(firstDl, rootFolders, rootBookmarks);

            return new ParsedImportResult(rootFolders, rootBookmarks);
        } catch (AppValidationException e) {
            throw e;
        } catch (Exception _) {
            throw new AppValidationException(
                AppValidationMessage.uploadProblem("Invalid bookmark file format. Please export from your browser and try again."));
        }
    }

    private void parseChildren(Element dl, List<ParsedFolder> folders, List<ParsedBookmark> bookmarks) {
        Elements dts = dl.select("> dt");

        for (Element dt : dts) {
            Element firstChild = dt.firstElementChild();
            if (firstChild != null && firstChild.tagName().equals("h3")) {
                ParsedFolder folder = new ParsedFolder(firstChild.text());

                Element nestedDl = dt.select("> dl").first();
                if (nestedDl != null) {
                    parseChildren(nestedDl, folder.getFolders(), folder.getBookmarks());
                }

                folders.add(folder);
            } else if (firstChild != null && firstChild.tagName().equals("a")) {
                String href = firstChild.attr("href");
                String title = firstChild.text();
                bookmarks.add(new ParsedBookmark(title, href));
            }
        }
    }
}
