package org.chainlink.api.testutil.builder;

import java.util.function.Consumer;

import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.User;
import org.jspecify.annotations.NonNull;

public class CollectionAccessBuilder {

    private final CollectionAccess access;

    public CollectionAccessBuilder() {
        this.access = defaultCollectionAccess();
    }

    @NonNull
    public static CollectionAccess defaultCollectionAccess() {
        CollectionAccess a = new CollectionAccess();
        a.setCollection(CollectionBuilder.defaultCollection());
        a.setUser(UserBuilder.defaultUser());
        a.setRole(CollectionRole.OWNER);
        a.setDefault(false);
        return a;
    }

    @NonNull
    public CollectionAccessBuilder withCollection(Collection collection) {
        access.setCollection(collection);
        return this;
    }

    @NonNull
    public CollectionAccessBuilder withUser(User user) {
        access.setUser(user);
        return this;
    }

    @NonNull
    public CollectionAccessBuilder withRole(CollectionRole role) {
        access.setRole(role);
        return this;
    }

    @NonNull
    public CollectionAccessBuilder withDefault(boolean isDefault) {
        access.setDefault(isDefault);
        return this;
    }

    @NonNull
    public static CollectionAccess build(Consumer<CollectionAccessBuilder> block) {
        CollectionAccessBuilder builder = new CollectionAccessBuilder();
        block.accept(builder);
        return builder.access;
    }
}
