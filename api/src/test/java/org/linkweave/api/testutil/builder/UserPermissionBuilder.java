package org.linkweave.api.testutil.builder;

import java.util.function.Consumer;

import org.linkweave.api.shared.auth.Permission;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.shared.user.UserPermission;
import org.jspecify.annotations.NonNull;

public class UserPermissionBuilder {

    private final UserPermission permission;

    public UserPermissionBuilder() {
        this.permission = defaultUserPermission();
    }

    @NonNull
    public static UserPermission defaultUserPermission() {
        UserPermission p = new UserPermission();
        p.setUser(UserBuilder.defaultUser());
        p.setPermission(Permission.BOOKMARK_READ);
        return p;
    }

    @NonNull
    public UserPermissionBuilder withUser(User user) {
        permission.setUser(user);
        return this;
    }

    @NonNull
    public UserPermissionBuilder withPermission(Permission permission) {
        this.permission.setPermission(permission);
        return this;
    }

    @NonNull
    public static UserPermission build(Consumer<UserPermissionBuilder> block) {
        UserPermissionBuilder builder = new UserPermissionBuilder();
        block.accept(builder);
        return builder.permission;
    }
}
