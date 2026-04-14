-- Enforce at most one default collection per user at the database level.
CREATE UNIQUE INDEX ux_collectionaccess_one_default_per_user
    ON CollectionAccess (user_id)
    WHERE isDefault = 1;
