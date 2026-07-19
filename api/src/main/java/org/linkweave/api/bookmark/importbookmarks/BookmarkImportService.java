package org.linkweave.api.bookmark.importbookmarks;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;

import org.linkweave.infrastructure.clock.AppClock;
import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.bookmark.property.BookmarkPropertyValue;
import org.linkweave.api.bookmark.property.BookmarkPropertyValueRepo;
import org.linkweave.api.bookmark.property.PropertyDefinition;
import org.linkweave.api.bookmark.property.PropertyDefinitionRepo;
import org.linkweave.api.bookmark.property.PropertyType;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.sortorder.SortOrderAllocator;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class BookmarkImportService {

    static final String IMPORT_SOURCE_PROPERTY_NAME = "import-source";

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;
    private final PropertyDefinitionRepo propertyDefinitionRepo;
    private final BookmarkPropertyValueRepo bookmarkPropertyValueRepo;
    private final ConfigService configService;
    private final AppClock appClock;

    @NonNull
    public ImportSummaryJson importBookmarks(
        @NonNull ID<Collection> collectionId,
        @NonNull InputStream inputStream,
        @Nullable String fileName
    ) {
        Collection collection = collectionRepo.referenceById(collectionId);

        NetscapeBookmarkParser parser = new NetscapeBookmarkParser();
        ParsedImportResult importDTO = parser.parse(inputStream);

        ImportSummaryJson summary = new ImportSummaryJson();

        PropertyDefinition importSourceDef = configService.isBookmarkPropertiesEnabled()
            ? findOrCreateImportSourceDefinition(collection)
            : null;

        SortOrderAllocator<ID<Folder>> bookmarkSortOrders = new SortOrderAllocator<>(
            folderId -> bookmarkRepo.findMaxSortOrderOfSiblings(collection.getId(), folderId));
        for (ParsedBookmark rootBookmark : importDTO.rootBookmarks()) {
            if (createBookmark(rootBookmark, collection, null, importSourceDef, fileName, bookmarkSortOrders)) {
                summary.incrementBookmarksCreated();
            } else {
                summary.incrementBookmarksSkipped();
            }
        }

        SortOrderAllocator<ID<Folder>> sortOrders = new SortOrderAllocator<>(
            parentId -> folderRepo.findMaxSortOrderOfSiblings(collection.getId(), parentId));
        for (ParsedFolder parsedFolder : importDTO.rootFolders()) {
            importFolderRecursive(
                parsedFolder, collection, null, summary, importSourceDef, fileName, sortOrders, bookmarkSortOrders);
        }

        return summary;
    }

    /**
     * Finds an existing "import-source" TEXT property definition for the collection,
     * or creates one if none exists yet.
     */
    private PropertyDefinition findOrCreateImportSourceDefinition(@NonNull Collection collection) {
        var existing = propertyDefinitionRepo.findByCollection(collection.getId()).stream()
            .filter(d -> IMPORT_SOURCE_PROPERTY_NAME.equals(d.getName()))
            .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        int sortOrder = propertyDefinitionRepo.findByCollection(collection.getId()).size();
        PropertyDefinition def = new PropertyDefinition(
            collection,
            IMPORT_SOURCE_PROPERTY_NAME,
            PropertyType.TEXT,
            null,
            sortOrder
        );
        propertyDefinitionRepo.persistAndFlush(def);
        return def;
    }

    private void importFolderRecursive(
        @NonNull ParsedFolder parsedFolder,
        @NonNull Collection collection,
        @Nullable Folder parent,
        @NonNull ImportSummaryJson summary,
        @Nullable PropertyDefinition importSourceDef,
        @Nullable String fileName,
        @NonNull SortOrderAllocator<ID<Folder>> sortOrders,
        @NonNull SortOrderAllocator<ID<Folder>> bookmarkSortOrders
    ) {
        ID<Folder> parentId = parent == null ? null : parent.getId();
        Folder folder = new Folder(collection, parent, parsedFolder.getName(), null, sortOrders.next(parentId), null);
        folderRepo.persist(folder);
        summary.incrementFoldersCreated();

        for (ParsedBookmark parsedBookmark : parsedFolder.getBookmarks()) {
            if (createBookmark(parsedBookmark, collection, folder, importSourceDef, fileName, bookmarkSortOrders)) {
                summary.incrementBookmarksCreated();
            } else {
                summary.incrementBookmarksSkipped();
            }
        }

        for (ParsedFolder child : parsedFolder.getFolders()) {
            importFolderRecursive(
                child, collection, folder, summary, importSourceDef, fileName, sortOrders, bookmarkSortOrders);
        }
    }

    /**
     * @return true if the bookmark was created, false if it was skipped due to an invalid URL
     */
    private boolean createBookmark(
        @NonNull ParsedBookmark parsed,
        @NonNull Collection collection,
        @Nullable Folder folder,
        @Nullable PropertyDefinition importSourceDef,
        @Nullable String fileName,
        @NonNull SortOrderAllocator<ID<Folder>> bookmarkSortOrders
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
            new HashSet<>(),
            new HashSet<>(),
            bookmarkSortOrders.next(folder == null ? null : folder.getId()),
            0,
            null,
            null,
            null,
            null
        );
        OffsetDateTime importedAddedAt = resolveImportedAddedAt(parsed.getAddedAt());
        if (importedAddedAt != null) {
            // Preserve the original creation date from the imported file.
            // AbstractEntityListener fills timestampErstellt only when null,
            bookmark.setTimestampErstellt(importedAddedAt);
        }
        bookmarkRepo.persist(bookmark);

        if (importSourceDef != null && fileName != null) {
            BookmarkPropertyValue value = new BookmarkPropertyValue(
                bookmark,
                importSourceDef,
                fileName,
                null,
                null
            );
            bookmarkPropertyValueRepo.persist(value);
        }

        return true;
    }

    /**
     * Honor the Netscape `ADD_DATE` only if it is in a sane range — strictly in
     * the past and after the Unix epoch. Future-dated values from a malformed
     * or malicious file are dropped so the import time is used instead.
     */
    @Nullable
    private OffsetDateTime resolveImportedAddedAt(@Nullable Instant addedAt) {
        if (addedAt == null) return null;
        Instant now = appClock.instant().now();
        if (addedAt.isAfter(now) || addedAt.getEpochSecond() <= 0) return null;
        return OffsetDateTime.ofInstant(addedAt, ZoneOffset.UTC);
    }

    private Optional<URL> parseUrl(@NonNull String urlString) {
        try {
            return Optional.of(URI.create(urlString).toURL());
        } catch (Exception _) {
            return Optional.empty();
        }
    }
}
