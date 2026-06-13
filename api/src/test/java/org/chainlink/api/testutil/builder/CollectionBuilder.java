package org.chainlink.api.testutil.builder;

import java.util.function.Consumer;

import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.user.User;
import org.jspecify.annotations.NonNull;

public class CollectionBuilder {

    private final Collection collection;

    public CollectionBuilder() {
        this.collection = defaultCollection();
    }

    @NonNull
    public static Collection defaultCollection() {
        Collection c = new Collection();
        c.setName("Test Collection");
        c.setOwner(UserBuilder.defaultUser());
        return c;
    }

    @NonNull
    public CollectionBuilder withName(String name) {
        collection.setName(name);
        return this;
    }

    @NonNull
    public CollectionBuilder withOwner(User owner) {
        collection.setOwner(owner);
        return this;
    }

    @NonNull
    public CollectionBuilder withScreenshotEnabled(boolean enabled) {
        collection.setScreenshotEnabled(enabled);
        return this;
    }

    @NonNull
    public CollectionBuilder withBrowserFetchAllowlist(String browserFetchAllowlist) {
        collection.setBrowserFetchAllowlist(browserFetchAllowlist);
        return this;
    }

    @NonNull
    public static Collection build(Consumer<CollectionBuilder> block) {
        CollectionBuilder builder = new CollectionBuilder();
        block.accept(builder);
        return builder.collection;
    }
}
