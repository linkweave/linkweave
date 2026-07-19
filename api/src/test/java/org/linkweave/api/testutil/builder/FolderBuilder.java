package org.linkweave.api.testutil.builder;

import java.util.function.Consumer;

import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FolderBuilder {

    private final Folder folder;
    private boolean sortOrderSet;

    public FolderBuilder() {
        this.folder = defaultFolder();
    }

    @NonNull
    public static Folder defaultFolder() {
        Folder f = new Folder();
        f.setCollection(CollectionBuilder.defaultCollection());
        f.setName("Test Folder");
        f.setParent(null);
        return f;
    }

    @NonNull
    public FolderBuilder withCollection(Collection collection) {
        folder.setCollection(collection);
        return this;
    }

    @NonNull
    public FolderBuilder withName(String name) {
        folder.setName(name);
        return this;
    }

    @NonNull
    public FolderBuilder withParent(@Nullable Folder parent) {
        folder.setParent(parent);
        return this;
    }

    @NonNull
    public FolderBuilder withSortOrder(long sortOrder) {
        folder.setSortOrder(sortOrder);
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
    public Folder folder() {
        return folder;
    }

    @NonNull
    public static Folder build(Consumer<FolderBuilder> block) {
        return configured(block).folder;
    }

    /**
     * As {@link #build}, but hands back the builder so callers can still see which
     * fields were set explicitly.
     */
    @NonNull
    public static FolderBuilder configured(Consumer<FolderBuilder> block) {
        FolderBuilder builder = new FolderBuilder();
        block.accept(builder);
        return builder;
    }
}
