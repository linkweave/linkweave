package org.linkweave.api.testutil.builder;

import java.util.function.Consumer;

import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FolderBuilder {

    private final Folder folder;

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
    public static Folder build(Consumer<FolderBuilder> block) {
        FolderBuilder builder = new FolderBuilder();
        block.accept(builder);
        return builder.folder;
    }
}
