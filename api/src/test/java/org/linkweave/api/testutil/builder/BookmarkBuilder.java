package org.linkweave.api.testutil.builder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class BookmarkBuilder {

    private final Bookmark bookmark;
    private boolean sortOrderSet;

    public BookmarkBuilder() {
        this.bookmark = defaultBookmark();
    }

    @NonNull
    public static Bookmark defaultBookmark() {
        Bookmark b = new Bookmark();
        try {
            b.setCollection(CollectionBuilder.defaultCollection());
            b.setFolder(null);
            b.setTitle("Test Bookmark");
            b.setUrl(new URI("https://example.com").toURL());
            b.setDescription("Test description");
            b.setTags(new HashSet<>());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return b;
    }

    @NonNull
    public BookmarkBuilder withCollection(Collection collection) {
        bookmark.setCollection(collection);
        return this;
    }

    @NonNull
    public BookmarkBuilder withFolder(@Nullable Folder folder) {
        bookmark.setFolder(folder);
        return this;
    }

    @NonNull
    public BookmarkBuilder withTitle(String title) {
        bookmark.setTitle(title);
        return this;
    }

    @NonNull
    public BookmarkBuilder withUrl(String url) {
        try {
            bookmark.setUrl(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @NonNull
    public BookmarkBuilder withDescription(String description) {
        bookmark.setDescription(description);
        return this;
    }

    @NonNull
    public BookmarkBuilder withTags(Set<Tag> tags) {
        bookmark.setTags(tags);
        return this;
    }

    @NonNull
    public BookmarkBuilder withScreenshotCapturedAt(@Nullable OffsetDateTime at) {
        bookmark.setScreenshotCapturedAt(at);
        return this;
    }

    @NonNull
    public BookmarkBuilder withLastClickedAt(@Nullable OffsetDateTime at) {
        bookmark.setLastClickedAt(at);
        return this;
    }

    @NonNull
    public BookmarkBuilder withSortOrder(long sortOrder) {
        bookmark.setSortOrder(sortOrder);
        sortOrderSet = true;
        return this;
    }

    /**
     * Whether the test picked a sort order explicitly. Tracked separately because the
     * entity's field is a primitive {@code long}: 0 is a legitimate stored value (see
     * {@code SparseSortOrder.between}, whose head slot returns 0 and then goes negative),
     * so it cannot double as an "unset" sentinel.
     */
    public boolean isSortOrderSet() {
        return sortOrderSet;
    }

    @NonNull
    public Bookmark bookmark() {
        return bookmark;
    }

    @NonNull
    public static Bookmark build(Consumer<BookmarkBuilder> block) {
        return configured(block).bookmark;
    }

    @NonNull
    public static BookmarkBuilder configured(Consumer<BookmarkBuilder> block) {
        BookmarkBuilder builder = new BookmarkBuilder();
        block.accept(builder);
        return builder;
    }
}
