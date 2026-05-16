package org.chainlink.api.bookmark.importbookmarks;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagColorPalette;
import org.chainlink.api.bookmark.TagRepo;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class BookmarkImportService {

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;
    private final TagRepo tagRepo;
    private final AppClock appClock;

    @NonNull
    public ImportSummaryJson importBookmarks(@NonNull ID<Collection> collectionId, @NonNull InputStream inputStream) {
        Collection collection = collectionRepo.referenceById(collectionId);

        NetscapeBookmarkParser parser = new NetscapeBookmarkParser();
        ParsedImportResult importDTO = parser.parse(inputStream);

        Tag importTag = createImportTag(collection);

        ImportSummaryJson summary = new ImportSummaryJson(importTag.getName());

        for (ParsedBookmark rootBookmark : importDTO.rootBookmarks()) {
            if (createBookmark(rootBookmark, collection, null, importTag)) {
                summary.incrementBookmarksCreated();
            } else {
                summary.incrementBookmarksSkipped();
            }
        }

        for (ParsedFolder parsedFolder : importDTO.rootFolders()) {
            importFolderRecursive(parsedFolder, collection, null, importTag, summary);
        }

        return summary;
    }

    private Tag createImportTag(@NonNull Collection collection) {
        String datePart = appClock.getToday().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String prefix = "imported=" + datePart + "_";

        int existingCount = tagRepo.findByCollection(collection.getId()).size();
        long runNumber = tagRepo.findByCollection(collection.getId()).stream()
            .filter(t -> t.getName().startsWith(prefix))
            .count() + 1;

        String tagName = prefix + runNumber;

        Tag tag = new Tag(
            collection,
            tagName,
            TagColorPalette.autoAssignColor(existingCount),
            Collections.emptySet()
        );
        tagRepo.persistAndFlush(tag);
        return tag;
    }

    private void importFolderRecursive(
        @NonNull ParsedFolder parsedFolder,
        @NonNull Collection collection,
        @Nullable Folder parent,
        @NonNull Tag importTag,
        @NonNull ImportSummaryJson summary
    ) {
        Folder folder = new Folder(collection, parent, parsedFolder.getName(), null, null);
        folderRepo.persist(folder);
        summary.incrementFoldersCreated();

        for (ParsedBookmark parsedBookmark : parsedFolder.getBookmarks()) {
            if (createBookmark(parsedBookmark, collection, folder, importTag)) {
                summary.incrementBookmarksCreated();
            } else {
                summary.incrementBookmarksSkipped();
            }
        }

        for (ParsedFolder child : parsedFolder.getFolders()) {
            importFolderRecursive(child, collection, folder, importTag, summary);
        }
    }

    /**
     * @return true if the bookmark was created, false if it was skipped due to an invalid URL
     */
    private boolean createBookmark(
        @NonNull ParsedBookmark parsed,
        @NonNull Collection collection,
        @Nullable Folder folder,
        @NonNull Tag importTag
    ) {
        Optional<URL> url = parseUrl(parsed.getUrl());
        if (url.isEmpty()) {
            return false;
        }
        Bookmark bookmark = new Bookmark(
            collection,
            folder,
            parsed.getTitle(),
            url.get(),
            parsed.getDescription(),
            new HashSet<>(Set.of(importTag)),
            new HashSet<>(),
            0,
            null,
            null,
            null
        );
        bookmarkRepo.persist(bookmark);
        return true;
    }

    private Optional<URL> parseUrl(@NonNull String urlString) {
        try {
            return Optional.of(URI.create(urlString).toURL());
        } catch (Exception _) {
            return Optional.empty();
        }
    }
}
