package org.chainlink.api.testutil.builder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.collection.Collection;
import org.jspecify.annotations.NonNull;

public class TagBuilder {

    private final Tag tag;

    public TagBuilder() {
        this.tag = defaultTag();
    }

    @NonNull
    public static Tag defaultTag() {
        Tag t = new Tag();
        t.setCollection(CollectionBuilder.defaultCollection());
        t.setName("Test Tag");
        t.setColor("#FF0000");
        t.setBookmarks(new HashSet<>());
        return t;
    }

    @NonNull
    public TagBuilder withCollection(Collection collection) {
        tag.setCollection(collection);
        return this;
    }

    @NonNull
    public TagBuilder withName(String name) {
        tag.setName(name);
        return this;
    }

    @NonNull
    public TagBuilder withColor(String color) {
        tag.setColor(color);
        return this;
    }

    @NonNull
    public TagBuilder withBookmarks(Set<Bookmark> bookmarks) {
        tag.setBookmarks(bookmarks);
        return this;
    }

    @NonNull
    public static Tag build(Consumer<TagBuilder> block) {
        TagBuilder builder = new TagBuilder();
        block.accept(builder);
        return builder.tag;
    }
}
