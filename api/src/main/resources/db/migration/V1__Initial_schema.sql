-- Initial schema for Chainlink application
-- All tables with named FK constraints and composite FK indexes

-- Create REVINFO table for Hibernate Envers
CREATE TABLE REVINFO (
    REV INTEGER PRIMARY KEY,
    REVTSTMP BIGINT
);

-- ============================================================
-- User
-- ============================================================
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
    vorname VARCHAR(255),
    PRIMARY KEY (REV, id)
);

-- ============================================================
-- UserPermission
-- ============================================================
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

-- ============================================================
-- Collection
-- ============================================================
CREATE TABLE Collection (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES User(id)
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
    owner_id VARCHAR(36),
    PRIMARY KEY (REV, id)
);

-- ============================================================
-- CollectionAccess
-- ============================================================
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
    isDefault BOOLEAN NOT NULL DEFAULT 0,
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
    isDefault BOOLEAN,
    PRIMARY KEY (REV, id)
);

-- ============================================================
-- Folder
-- ============================================================
CREATE TABLE Folder (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    collection_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36),
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
    parent_id VARCHAR(36),
    PRIMARY KEY (REV, id)
);

-- ============================================================
-- Tag
-- ============================================================
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

-- ============================================================
-- Bookmark
-- ============================================================
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
    folder_id VARCHAR(36),
    CONSTRAINT fk_bookmark_collection FOREIGN KEY (collection_id) REFERENCES Collection(id),
    CONSTRAINT fk_bookmark_folder FOREIGN KEY (folder_id) REFERENCES Folder(id)
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
    folder_id VARCHAR(36),
    PRIMARY KEY (REV, id)
);

-- ============================================================
-- Bookmark_Tag join table (ManyToMany)
-- ============================================================
CREATE TABLE Bookmark_Tag (
    bookmark_id VARCHAR(36) NOT NULL,
    tag_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (bookmark_id, tag_id),
    CONSTRAINT fk_bookmark_tag_bookmark FOREIGN KEY (bookmark_id) REFERENCES Bookmark(id),
    CONSTRAINT fk_bookmark_tag_tag FOREIGN KEY (tag_id) REFERENCES Tag(id)
);

-- ============================================================
-- Unique constraints
-- ============================================================
CREATE UNIQUE INDEX uc_user_email ON User (email);
CREATE UNIQUE INDEX uc_user_keycloakId ON User (keycloakId);
CREATE UNIQUE INDEX uc_userpermission_user_permission ON UserPermission (user_id, permission);
CREATE UNIQUE INDEX uc_collectionaccess_collection_user ON CollectionAccess (collection_id, user_id);
CREATE UNIQUE INDEX uq_tag_name_collection ON Tag (name, collection_id);

-- ============================================================
-- FK indexes (composite: FK column + id)
-- ============================================================
CREATE INDEX ix_userpermission_user_id ON UserPermission (user_id, id);
CREATE INDEX ix_collection_owner_id ON Collection (owner_id, id);
CREATE INDEX ix_collectionaccess_user_id ON CollectionAccess (user_id, id);
CREATE INDEX ix_collectionaccess_collection_id ON CollectionAccess (collection_id, id);
CREATE INDEX ix_folder_collection_id ON Folder (collection_id, id);
CREATE INDEX ix_folder_parent_id ON Folder (parent_id, id);
CREATE INDEX ix_tag_collection_id ON Tag (collection_id, id);
CREATE INDEX ix_bookmark_collection_id ON Bookmark (collection_id, id);
CREATE INDEX ix_bookmark_folder_id ON Bookmark (folder_id, id);

-- ============================================================
-- Join table indexes
-- ============================================================
CREATE INDEX ix_bookmark_tag_bookmark_id ON Bookmark_Tag (bookmark_id, tag_id);
CREATE INDEX ix_bookmark_tag_tag_id ON Bookmark_Tag (tag_id, bookmark_id);

-- ============================================================
-- Additional indexes (non-FK)
-- ============================================================
CREATE INDEX ix_user_id ON User (id, version);
CREATE INDEX ix_user_email ON User (email, id);
CREATE INDEX ix_user_keycloakId ON User (keycloakId, id);
CREATE INDEX ix_collectionaccess_user_default ON CollectionAccess (user_id, isDefault);
