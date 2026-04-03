package org.chainlink.api.testutil.builder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class BookmarkBuilder {

    private final Bookmark bookmark;

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
    public static Bookmark build(Consumer<BookmarkBuilder> block) {
        BookmarkBuilder builder = new BookmarkBuilder();
        block.accept(builder);
        return builder.bookmark;
    }
}
