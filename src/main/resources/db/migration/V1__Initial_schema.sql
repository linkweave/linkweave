-- Initial schema migration for Chainlink application
-- Includes User, Bookmark, Tag, and Folder entities with Envers audit tables

-- Create REVINFO table for Hibernate Envers
CREATE TABLE REVINFO (
    REV INTEGER PRIMARY KEY,
    REVTSTMP BIGINT
);

-- Create User table
CREATE TABLE User (
    id BLOB NOT NULL PRIMARY KEY,
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
    oidcIssuer VARCHAR(255),
    nachname VARCHAR(255) NOT NULL,
    timestampLastManuallyActivated TIMESTAMP,
    titel VARCHAR(255),
    vorname VARCHAR(255) NOT NULL
);

-- Create User_AUD table for Hibernate Envers
CREATE TABLE User_AUD (
    id BLOB NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    aktiv BOOLEAN,
    email VARCHAR(255),
    fachRollen VARCHAR(2000),
    keycloakId VARCHAR(255),
    lastUsedIp VARCHAR(255),
    lastUsedSessionId VARCHAR(255),
    lastUsedSessionTimestamp TIMESTAMP,
    oidcIssuer VARCHAR(255),
    nachname VARCHAR(255),
    timestampLastManuallyActivated TIMESTAMP,
    titel VARCHAR(255),
    vorname VARCHAR(255),
    PRIMARY KEY (REV, id)
);

-- Create UserPermission table
CREATE TABLE UserPermission (
    id BLOB NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    permission VARCHAR(255) NOT NULL CHECK (permission IN ('SUPPORT','SYSTEM_ADMIN','BOOKMARK_READ','BOOKMARK_WRITE')),
    user_id BLOB NOT NULL,
    FOREIGN KEY (user_id) REFERENCES User(id)
);

-- Create UserPermission_AUD table for Hibernate Envers
CREATE TABLE UserPermission_AUD (
    id BLOB NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    permission VARCHAR(255) CHECK (permission IN ('SUPPORT','SYSTEM_ADMIN','BOOKMARK_READ','BOOKMARK_WRITE')),
    user_id BLOB,
    PRIMARY KEY (REV, id)
);

-- Create Folder table
CREATE TABLE Folder (
    id BLOB NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL
);

-- Create Folder_AUD table for Hibernate Envers
CREATE TABLE Folder_AUD (
    id BLOB NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    name VARCHAR(255),
    PRIMARY KEY (REV, id)
);

-- Create Tag table
CREATE TABLE Tag (
    id BLOB NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL
);

-- Create Tag_AUD table for Hibernate Envers
CREATE TABLE Tag_AUD (
    id BLOB NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    name VARCHAR(255),
    PRIMARY KEY (REV, id)
);

-- Create Bookmark table
CREATE TABLE Bookmark (
    id BLOB NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    notes VARCHAR(5000),
    folder_id BLOB NOT NULL,
    CONSTRAINT fk_bookmark_folder
        FOREIGN KEY (folder_id) REFERENCES Folder(id)
);

-- Create Bookmark_AUD table for Hibernate Envers
CREATE TABLE Bookmark_AUD (
    id BLOB NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    title VARCHAR(255),
    url VARCHAR(2000),
    notes VARCHAR(5000),
    folder_id BLOB,
    PRIMARY KEY (REV, id)
);

-- Create Bookmark_Tag join table for ManyToMany relationship
CREATE TABLE Bookmark_Tag (
    bookmarks_id BLOB NOT NULL,
    tags_id BLOB NOT NULL,
    PRIMARY KEY (bookmarks_id, tags_id),
    CONSTRAINT fk_bookmark_tag_bookmark
        FOREIGN KEY (bookmarks_id) REFERENCES Bookmark(id),
    CONSTRAINT fk_bookmark_tag_tag
        FOREIGN KEY (tags_id) REFERENCES Tag(id)
);


-- create foreign key constraints
-- (SQLite requires FKs to be declared in CREATE TABLE; no ALTER TABLE ... ADD FOREIGN KEY)

-- Create unique constraints
CREATE UNIQUE INDEX uc_user_email ON User (email);
CREATE UNIQUE INDEX uc_user_keycloakId ON User (keycloakId);
CREATE UNIQUE INDEX uc_userpermission_user_permission ON UserPermission (user_id, permission);

-- Create regular indexes
CREATE INDEX ix_user_id ON User (id, version);
CREATE INDEX ix_user_email ON User (email, id);
CREATE INDEX ix_user_keycloakId ON User (keycloakId, id);
CREATE INDEX ix_userpermission_user ON UserPermission (user_id);
CREATE INDEX ix_folder_name ON Folder (name);
CREATE INDEX ix_tag_name ON Tag (name);
CREATE INDEX ix_bookmark_title ON Bookmark (title);
CREATE INDEX ix_bookmark_folder_id ON Bookmark (folder_id);
