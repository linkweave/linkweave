package org.linkweave.api.collection;

/**
 * Roles a user can hold on a collection, ordered by ascending privilege:
 * {@code MEMBER < ADMIN < OWNER}.
 *
 * <ul>
 *   <li>{@link #MEMBER} — can read/write bookmarks and use per-user settings.</li>
 *   <li>{@link #ADMIN} — member privileges plus managing members and collection
 *       configuration (favicon allowlist, preview toggle), but cannot rename or
 *       delete the collection and cannot promote/demote admins.</li>
 *   <li>{@link #OWNER} — full control, including deletion, rename and role changes.</li>
 * </ul>
 */
public enum CollectionRole {
    MEMBER,
    ADMIN,
    OWNER;

    /**
     * @return {@code true} if this role has at least the privilege of {@code required}.
     */
    public boolean isAtLeast(CollectionRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
