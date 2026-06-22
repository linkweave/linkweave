-- LinkWeave initial baseline schema.
-- Consolidated from former migrations V1-V21 on 2026-06-22 (data was disposable at reset).
-- Generated from the live migrated schema and verified schema-equivalent.
-- Do NOT edit once applied (Flyway checksum).

CREATE TABLE ApiKey (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    keyHash VARCHAR(64) NOT NULL,
    keyPrefix VARCHAR(8) NOT NULL,
    expiresAt TIMESTAMP,
    lastUsedAt TIMESTAMP,
    revokedAt TIMESTAMP,
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES User(id) ON DELETE CASCADE
);
CREATE TABLE AutoTagRule (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    pattern VARCHAR(2000) NOT NULL,
    tagNames VARCHAR(2000) NOT NULL,
    description VARCHAR(255),
    enabled BOOLEAN NOT NULL,
    sortOrder INTEGER NOT NULL,
    CONSTRAINT fk_auto_tag_rule_collection FOREIGN KEY (collection_id) REFERENCES Collection(id)
);
CREATE TABLE AutoTagRule_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    collection_id VARCHAR(36),
    pattern VARCHAR(2000),
    tagNames VARCHAR(2000),
    description VARCHAR(255),
    enabled BOOLEAN,
    sortOrder INTEGER,
    PRIMARY KEY (REV, id)
);
CREATE TABLE Bookmark (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    description VARCHAR(5000),
    collection_id VARCHAR(36) NOT NULL,
    folder_id VARCHAR(36), click_count INTEGER NOT NULL DEFAULT 0, last_clicked_at TIMESTAMP, deleted_at TIMESTAMP, suggestion_dismissed_at TIMESTAMP, screenshot_captured_at TIMESTAMP,
    CONSTRAINT fk_bookmark_collection FOREIGN KEY (collection_id) REFERENCES Collection(id),
    CONSTRAINT fk_bookmark_folder FOREIGN KEY (folder_id) REFERENCES Folder(id)
);
CREATE TABLE "BookmarkPropertyValue" (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    bookmark_id VARCHAR(36) NOT NULL,
    propertyDefinition_id VARCHAR(36) NOT NULL,
    valueText VARCHAR(255),
    valueNumber NUMERIC(19, 2),
    valueBoolean BOOLEAN,
    CONSTRAINT fk_bookmark_property_value_bookmark FOREIGN KEY (bookmark_id) REFERENCES Bookmark(id),
    CONSTRAINT fk_bookmark_property_value_definition FOREIGN KEY (propertyDefinition_id) REFERENCES PropertyDefinition(id)
);
CREATE TABLE "BookmarkPropertyValue_AUD" (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    bookmark_id VARCHAR(36),
    propertyDefinition_id VARCHAR(36),
    valueText VARCHAR(255),
    valueNumber NUMERIC(19, 2),
    valueBoolean BOOLEAN,
    PRIMARY KEY (REV, id)
);
CREATE TABLE Bookmark_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    title VARCHAR(255),
    url VARCHAR(2000),
    description VARCHAR(5000),
    collection_id VARCHAR(36),
    folder_id VARCHAR(36), click_count INTEGER, last_clicked_at TIMESTAMP, deleted_at TIMESTAMP, suggestion_dismissed_at TIMESTAMP,
    PRIMARY KEY (REV, id)
);
CREATE TABLE Bookmark_Tag (
    bookmark_id VARCHAR(36) NOT NULL,
    tag_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (bookmark_id, tag_id),
    CONSTRAINT fk_bookmark_tag_bookmark FOREIGN KEY (bookmark_id) REFERENCES Bookmark(id),
    CONSTRAINT fk_bookmark_tag_tag FOREIGN KEY (tag_id) REFERENCES Tag(id)
);
CREATE TABLE Collection (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(36) NOT NULL, browserFetchAllowlist VARCHAR(2000), screenshotEnabled BOOLEAN NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES User(id)
);
CREATE TABLE CollectionAccess (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(30) NOT NULL,
    isDefault BOOLEAN NOT NULL DEFAULT 0, settings TEXT,
    CONSTRAINT fk_collectionaccess_collection FOREIGN KEY (collection_id) REFERENCES Collection(id),
    CONSTRAINT fk_collectionaccess_user FOREIGN KEY (user_id) REFERENCES User(id)
);
CREATE TABLE CollectionAccess_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    collection_id VARCHAR(36),
    user_id VARCHAR(36),
    role VARCHAR(30),
    isDefault BOOLEAN, settings TEXT,
    PRIMARY KEY (REV, id)
);
CREATE TABLE Collection_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    name VARCHAR(255),
    owner_id VARCHAR(36), browserFetchAllowlist VARCHAR(2000), screenshotEnabled BOOLEAN,
    PRIMARY KEY (REV, id)
);
CREATE TABLE Folder (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36), color VARCHAR(7), deleted_at TIMESTAMP,
    CONSTRAINT fk_folder_collection FOREIGN KEY (collection_id) REFERENCES Collection(id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES Folder(id)
);
CREATE TABLE Folder_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    name VARCHAR(255),
    collection_id VARCHAR(36),
    parent_id VARCHAR(36), color VARCHAR(7), deleted_at TIMESTAMP,
    PRIMARY KEY (REV, id)
);
CREATE TABLE PropertyDefinition (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    allowedValues VARCHAR(2000),
    sortOrder INTEGER NOT NULL,
    CONSTRAINT fk_property_definition_collection FOREIGN KEY (collection_id) REFERENCES Collection(id)
);
CREATE TABLE PropertyDefinition_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    collection_id VARCHAR(36),
    name VARCHAR(255),
    type VARCHAR(30),
    allowedValues VARCHAR(2000),
    sortOrder INTEGER,
    PRIMARY KEY (REV, id)
);
CREATE TABLE REVINFO (
    REV INTEGER PRIMARY KEY,
    REVTSTMP BIGINT
);
CREATE TABLE SavedSearch (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    query VARCHAR(2000) NOT NULL,
    CONSTRAINT fk_saved_search_collection FOREIGN KEY (collection_id) REFERENCES Collection(id),
    CONSTRAINT uc_saved_search_name_collection UNIQUE (name, collection_id)
);
CREATE TABLE SavedSearch_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    collection_id VARCHAR(36),
    name VARCHAR(255),
    query VARCHAR(2000),
    PRIMARY KEY (REV, id)
);
CREATE TABLE Tag (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(7) NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_tag_collection FOREIGN KEY (collection_id) REFERENCES Collection(id)
);
CREATE TABLE Tag_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    name VARCHAR(255),
    color VARCHAR(7),
    collection_id VARCHAR(36),
    PRIMARY KEY (REV, id)
);
CREATE TABLE User (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    aktiv BOOLEAN NOT NULL,
    email VARCHAR(255) NOT NULL,
    fachRollen VARCHAR(2000),
    keycloakId VARCHAR(255),
    lastUsedIp VARCHAR(255),
    lastUsedSessionId VARCHAR(255),
    lastUsedSessionTimestamp TIMESTAMP,
    nachname VARCHAR(255) NOT NULL,
    oidcIssuer VARCHAR(255),
    timestampLastManuallyActivated TIMESTAMP,
    titel VARCHAR(255),
    vorname VARCHAR(255) NOT NULL
, password VARCHAR(60), authProvider VARCHAR(30));
CREATE TABLE UserPermission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    permission VARCHAR(255) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_userpermission_user FOREIGN KEY (user_id) REFERENCES User(id)
);
CREATE TABLE UserPermission_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    permission VARCHAR(255),
    user_id VARCHAR(36),
    PRIMARY KEY (REV, id)
);
CREATE TABLE UserSettings (
    user_id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    offlineCachingEnabled BOOLEAN NOT NULL DEFAULT TRUE, savedSearchesEnabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_usersettings_user FOREIGN KEY (user_id) REFERENCES User(id)
);
CREATE TABLE UserSettings_AUD (
    user_id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    offlineCachingEnabled BOOLEAN, savedSearchesEnabled BOOLEAN,
    PRIMARY KEY (REV, user_id)
);
CREATE TABLE User_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    aktiv BOOLEAN,
    email VARCHAR(255),
    fachRollen VARCHAR(2000),
    keycloakId VARCHAR(255),
    lastUsedIp VARCHAR(255),
    lastUsedSessionId VARCHAR(255),
    lastUsedSessionTimestamp TIMESTAMP,
    nachname VARCHAR(255),
    oidcIssuer VARCHAR(255),
    timestampLastManuallyActivated TIMESTAMP,
    titel VARCHAR(255),
    vorname VARCHAR(255), password VARCHAR(60), authProvider VARCHAR(30),
    PRIMARY KEY (REV, id)
);
CREATE UNIQUE INDEX ix_apikey_key_hash ON ApiKey (keyHash);
CREATE INDEX ix_apikey_user_id ON ApiKey (user_id, id);
CREATE INDEX ix_auto_tag_rule_collection_id ON AutoTagRule (collection_id, sortOrder, id);
CREATE INDEX ix_bookmark_collection_id ON Bookmark (collection_id, id);
CREATE INDEX ix_bookmark_deleted_at ON Bookmark (deleted_at);
CREATE INDEX ix_bookmark_folder_id ON Bookmark (folder_id, id);
CREATE INDEX ix_bookmark_property_value_definition_id ON BookmarkPropertyValue (propertyDefinition_id, bookmark_id, id);
CREATE INDEX ix_bookmark_tag_bookmark_id ON Bookmark_Tag (bookmark_id, tag_id);
CREATE INDEX ix_bookmark_tag_tag_id ON Bookmark_Tag (tag_id, bookmark_id);
CREATE INDEX ix_collection_owner_id ON Collection (owner_id, id);
CREATE INDEX ix_collectionaccess_collection_id ON CollectionAccess (collection_id, id);
CREATE INDEX ix_collectionaccess_user_default ON CollectionAccess (user_id, isDefault);
CREATE INDEX ix_collectionaccess_user_id ON CollectionAccess (user_id, id);
CREATE INDEX ix_folder_collection_id ON Folder (collection_id, id);
CREATE INDEX ix_folder_deleted_at ON Folder (deleted_at);
CREATE INDEX ix_folder_parent_id ON Folder (parent_id, id);
CREATE INDEX ix_property_definition_collection_id ON PropertyDefinition (collection_id, sortOrder, id);
CREATE INDEX ix_saved_search_collection_id ON SavedSearch (collection_id, id);
CREATE INDEX ix_tag_collection_id ON Tag (collection_id, id);
CREATE INDEX ix_user_email ON User (email, id);
CREATE INDEX ix_user_id ON User (id, version);
CREATE INDEX ix_user_keycloakId ON User (keycloakId, id);
CREATE INDEX ix_userpermission_user_id ON UserPermission (user_id, id);
CREATE INDEX ix_usersettings_id ON UserSettings (user_id, version);
CREATE UNIQUE INDEX uc_bookmark_property_value_bookmark_definition ON BookmarkPropertyValue (bookmark_id, propertyDefinition_id);
CREATE UNIQUE INDEX uc_collectionaccess_collection_user ON CollectionAccess (collection_id, user_id);
CREATE UNIQUE INDEX uc_property_definition_name_collection ON PropertyDefinition (name, collection_id);
CREATE UNIQUE INDEX uc_tag_name_collection ON Tag(name, collection_id);
CREATE UNIQUE INDEX uc_user_email ON User (email);
CREATE UNIQUE INDEX uc_user_keycloakId ON User (keycloakId);
CREATE UNIQUE INDEX uc_userpermission_user_permission ON UserPermission (user_id, permission);
CREATE UNIQUE INDEX ux_collectionaccess_one_default_per_user
    ON CollectionAccess (user_id)
    WHERE isDefault = 1;
