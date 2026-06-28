package org.linkweave.api.bookmark.importbookmarks;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.linkweave.infrastructure.clock.AppClock;
import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import org.linkweave.infrastructure.db.DbConst;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;

/**
 * Two-phase reviewed import (UC-096): {@link #preview} parses the uploaded file
 * into a manifest and flags duplicates already in the destination collection;
 * {@link #commit} writes only the folders/bookmarks the user kept, merging
 * folders by path. Newly created bookmarks are picked up by the screenshot
 * capture poller automatically (no explicit enqueue — same as UC-031).
 */
@Service
@RequiredArgsConstructor
public class ImportReviewService {

    /**
     * Hard cap on import-tree nesting. A browser export is shallow; anything
     * deeper is a malformed or hostile payload and is rejected before it can
     * blow the request thread's stack (aligns with the C-017 bounding rationale).
     */
    private static final int MAX_DEPTH = 100;

    /** Fallback when an imported folder has no usable name (Folder.name is @NotBlank). */
    private static final String DEFAULT_FOLDER_NAME = "Imported";

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;
    private final PropertyDefinitionRepo propertyDefinitionRepo;
    private final BookmarkPropertyValueRepo bookmarkPropertyValueRepo;
    private final ConfigService configService;
    private final AppClock appClock;

    // --- Preview --------------------------------------------------------------

    @NonNull
    public ImportPreviewJson preview(
        @NonNull ID<Collection> collectionId,
        @NonNull InputStream inputStream
    ) {
        ParsedImportResult parsed = new NetscapeBookmarkParser().parse(inputStream);

        Set<String> existing = normalizedExistingUrls(collectionId);
        NodeIds ids = new NodeIds();
        Totals totals = new Totals();

        List<ImportNodeJson> tree = new ArrayList<>();
        for (ParsedBookmark b : parsed.rootBookmarks()) {
            ImportNodeJson node = bookmarkNode(b, ids, totals, existing);
            if (node != null) tree.add(node);
        }
        for (ParsedFolder f : parsed.rootFolders()) {
            tree.add(folderNode(f, ids, totals, existing, 0));
        }

        return new ImportPreviewJson(
            tree, totals.bookmarks, totals.folders, totals.duplicates, totals.unsupported);
    }

    private ImportNodeJson folderNode(
        @NonNull ParsedFolder folder,
        @NonNull NodeIds ids,
        @NonNull Totals totals,
        @NonNull Set<String> existing,
        int depth
    ) {
        requireDepth(depth);
        totals.folders++;
        String id = ids.nextFolderId();
        List<ImportNodeJson> children = new ArrayList<>();
        for (ParsedBookmark b : folder.getBookmarks()) {
            ImportNodeJson node = bookmarkNode(b, ids, totals, existing);
            if (node != null) children.add(node);
        }
        for (ParsedFolder child : folder.getFolders()) {
            children.add(folderNode(child, ids, totals, existing, depth + 1));
        }
        return new ImportNodeJson(id, ImportNodeType.FOLDER, folderName(folder.getName()), null, null, false, children);
    }

    /**
     * @return the bookmark node, or {@code null} when its URL can't be stored
     *     (e.g. {@code chrome://…}) — such nodes are excluded from the manifest
     *     and counted in {@code unsupported} so they never inflate the
     *     "N will be imported" promise.
     */
    @Nullable
    private ImportNodeJson bookmarkNode(
        @NonNull ParsedBookmark bookmark,
        @NonNull NodeIds ids,
        @NonNull Totals totals,
        @NonNull Set<String> existing
    ) {
        if (parseUrl(bookmark.getUrl()).isEmpty()) {
            totals.unsupported++;
            return null;
        }
        totals.bookmarks++;
        String id = ids.nextBookmarkId();
        boolean duplicate = existing.contains(ImportUrlNormalizer.normalize(bookmark.getUrl()));
        if (duplicate) {
            totals.duplicates++;
        }
        Long addDate = extractAddedDate(bookmark);
        return new ImportNodeJson(
            id, ImportNodeType.BOOKMARK, bookmarkTitle(bookmark.getTitle(), bookmark.getUrl()),
            bookmark.getUrl(), addDate, duplicate, null);
    }

    @Nullable
    private static Long extractAddedDate(@NonNull ParsedBookmark bookmark) {
        Instant addedAt = bookmark.getAddedAt();
        return addedAt == null ? null : addedAt.toEpochMilli();
    }

    /**
     * Coerce an imported bookmark title to satisfy {@code Bookmark.title}
     * (@NotBlank, @Size ≤ {@link DbConst#DB_DEFAULT_MAX_LENGTH}). A messy export —
     * or a hand-crafted commit — can carry a blank or over-long title; rather
     * than 500 on persist we fall back to the URL and clamp the length, mirroring
     * the defensive URL handling.
     */
    @NonNull
    private static String bookmarkTitle(@Nullable String name, @NonNull String url) {
        String title = name == null ? "" : name.strip();
        return clampLength(title.isEmpty() ? url : title);
    }

    /** As {@link #bookmarkTitle} but for {@code Folder.name} (no URL fallback). */
    @NonNull
    private static String folderName(@Nullable String name) {
        String n = name == null ? "" : name.strip();
        return clampLength(n.isEmpty() ? DEFAULT_FOLDER_NAME : n);
    }

    @NonNull
    private static String clampLength(@NonNull String s) {
        return s.length() <= DbConst.DB_DEFAULT_MAX_LENGTH
            ? s
            : s.substring(0, DbConst.DB_DEFAULT_MAX_LENGTH);
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new AppValidationException(
                AppValidationMessage.uploadProblem("Bookmark folder nesting is too deep to import."));
        }
    }

    // --- Commit ---------------------------------------------------------------

    @NonNull
    public ImportCommitResultJson commit(
        @NonNull ID<Collection> collectionId,
        @NonNull ImportCommitRequestJson request
    ) {
        Collection collection = collectionRepo.referenceById(collectionId);

        Folder destination = resolveDestination(collectionId, request.destinationFolderId());
        // NOTE: dedup is against the destination library only — two same-URL
        // bookmarks within the same import are NOT collapsed against each other
        // (matches the preview, which also checks only the library). Both get
        // created. Within-file de-duplication is intentionally out of scope.
        Set<String> existing = request.skipDuplicates()
            ? normalizedExistingUrls(collectionId)
            : Set.of();

        // (parentFolderId-or-"" for root, lowercased name) -> existing folder, so
        // recreated paths merge into an existing same-named folder (BR-183).
        Map<String, Folder> folderIndex = buildFolderIndex(collectionId);

        // import-source provenance property (parity with UC-031), only when
        // bookmark properties are enabled and the client supplied the file name.
        PropertyDefinition importSourceDef =
            configService.isBookmarkPropertiesEnabled() && request.fileName() != null
                ? findOrCreateImportSourceDefinition(collection)
                : null;

        CommitCtx ctx = new CommitCtx(
            collection, folderIndex, existing, importSourceDef, request.fileName(), new Counts());
        for (ImportNodeJson node : request.nodes()) {
            writeNode(node, destination, ctx, 0);
        }
        return new ImportCommitResultJson(
            ctx.counts.imported, ctx.counts.foldersCreated, ctx.counts.duplicatesSkipped);
    }

    private void writeNode(@NonNull ImportNodeJson node, @Nullable Folder parent, @NonNull CommitCtx ctx, int depth) {
        requireDepth(depth);
        if (node.type() == ImportNodeType.FOLDER) {
            Folder folder = mergeOrCreateFolder(parent, node.name(), ctx);
            for (ImportNodeJson child : safeChildren(node)) {
                writeNode(child, folder, ctx, depth + 1);
            }
        } else {
            createBookmark(node, parent, ctx);
        }
    }

    @NonNull
    private Folder mergeOrCreateFolder(@Nullable Folder parent, @NonNull String rawName, @NonNull CommitCtx ctx) {
        String name = folderName(rawName);
        String key = folderKey(parent == null ? null : parent.getId(), name);
        Folder existing = ctx.folderIndex.get(key);
        if (existing != null) {
            return existing;
        }
        Folder folder = new Folder(ctx.collection, parent, name, null, null);
        folderRepo.persist(folder);
        ctx.folderIndex.put(key, folder);
        ctx.counts.foldersCreated++;
        return folder;
    }

    private void createBookmark(@NonNull ImportNodeJson node, @Nullable Folder folder, @NonNull CommitCtx ctx) {
        String rawUrl = node.url();
        Optional<URL> url = parseUrl(rawUrl);
        if (url.isEmpty()) {
            // Unreachable via the UI (preview excludes these), but a hand-crafted
            // request could still carry one — drop it rather than 500.
            return;
        }
        if (!ctx.existing.isEmpty()
            && ctx.existing.contains(ImportUrlNormalizer.normalize(Objects.requireNonNull(rawUrl)))) {
            ctx.counts.duplicatesSkipped++;
            return;
        }
        Bookmark bookmark = new Bookmark(
            ctx.collection, folder, bookmarkTitle(node.name(), Objects.requireNonNull(rawUrl)), url.get(), null,
            new HashSet<>(), new HashSet<>(), 0, null, null, null, null
        );
        OffsetDateTime addedAt = resolveImportedAddedAt(node.addDate());
        if (addedAt != null) {
            bookmark.setTimestampErstellt(addedAt);
        }
        bookmarkRepo.persist(bookmark);
        if (ctx.importSourceDef != null && ctx.fileName != null) {
            bookmarkPropertyValueRepo.persist(
                new BookmarkPropertyValue(bookmark, ctx.importSourceDef, ctx.fileName, null, null));
        }
        ctx.counts.imported++;
    }

    /**
     * Finds the collection's {@code import-source} TEXT property definition, or
     * creates it. Mirrors {@link BookmarkImportService} so both import paths
     * record provenance identically.
     */
    @NonNull
    private PropertyDefinition findOrCreateImportSourceDefinition(@NonNull Collection collection) {
        var defs = propertyDefinitionRepo.findByCollection(collection.getId());
        return defs.stream()
            .filter(d -> BookmarkImportService.IMPORT_SOURCE_PROPERTY_NAME.equals(d.getName()))
            .findFirst()
            .orElseGet(() -> {
                PropertyDefinition def = new PropertyDefinition(
                    collection, BookmarkImportService.IMPORT_SOURCE_PROPERTY_NAME,
                    PropertyType.TEXT, null, defs.size());
                propertyDefinitionRepo.persistAndFlush(def);
                return def;
            });
    }

    /** Per-commit scratch state, so the recursion needn't thread six parameters. */
    private record CommitCtx(
        Collection collection,
        Map<String, Folder> folderIndex,
        Set<String> existing,
        @Nullable PropertyDefinition importSourceDef,
        @Nullable String fileName,
        Counts counts
    ) {}

    // --- Helpers --------------------------------------------------------------

    @NonNull
    private Set<String> normalizedExistingUrls(@NonNull ID<Collection> collectionId) {
        Set<String> set = new HashSet<>();
        for (URL url : bookmarkRepo.findActiveUrlsByCollection(collectionId)) {
            set.add(ImportUrlNormalizer.normalize(url.toString()));
        }
        return set;
    }

    @Nullable
    private Folder resolveDestination(
        @NonNull ID<Collection> collectionId,
        @Nullable ID<Folder> destinationFolderId
    ) {
        if (destinationFolderId == null) {
            return null;
        }
        // findById (not referenceById): a missing id must be a clean 400, not a
        // lazy-proxy EntityNotFoundException surfacing as 500.
        Folder folder = folderRepo.findById(destinationFolderId)
            .orElseThrow(() -> new AppValidationException(
                AppValidationMessage.uploadProblem("Destination folder does not exist.")));
        if (!folder.getCollectionId().equals(collectionId)) {
            throw new AppValidationException(
                AppValidationMessage.uploadProblem("Destination folder does not belong to this collection."));
        }
        return folder;
    }

    @NonNull
    private Map<String, Folder> buildFolderIndex(@NonNull ID<Collection> collectionId) {
        Map<String, Folder> index = new HashMap<>();
        for (Folder folder : folderRepo.findByCollection(collectionId)) {
            Folder parent = folder.getParent();
            index.putIfAbsent(folderKey(parent == null ? null : parent.getId(), folder.getName()), folder);
        }
        return index;
    }

    @NonNull
    private static String folderKey(@Nullable ID<Folder> parentId, @NonNull String name) {
        return (parentId == null ? "" : parentId.getUUID().toString()) + name.toLowerCase();
    }

    @NonNull
    private static List<ImportNodeJson> safeChildren(@NonNull ImportNodeJson node) {
        List<ImportNodeJson> children = node.children();
        return children == null ? List.of() : children;
    }

    @Nullable
    private OffsetDateTime resolveImportedAddedAt(@Nullable Long addDateMillis) {
        if (addDateMillis == null) {
            return null;
        }
        Instant addedAt = Instant.ofEpochMilli(addDateMillis);
        Instant now = appClock.instant().now();
        if (addedAt.isAfter(now) || addedAt.getEpochSecond() <= 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(addedAt, ZoneOffset.UTC);
    }

    @NonNull
    private Optional<URL> parseUrl(@Nullable String urlString) {
        if (urlString == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(urlString).toURL());
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    /** Generates the stable per-node ids ("b0", "f0", …) used during preview. */
    private static final class NodeIds {
        private int bookmarkSeq;
        private int folderSeq;

        String nextBookmarkId() {
            return "b" + bookmarkSeq++;
        }

        String nextFolderId() {
            return "f" + folderSeq++;
        }
    }

    private static final class Totals {
        int bookmarks;
        int folders;
        int duplicates;
        int unsupported;
    }

    private static final class Counts {
        int imported;
        int foldersCreated;
        int duplicatesSkipped;
    }
}
