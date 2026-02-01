-- Initial schema migration for Chainlink application
-- Converted from Liquibase changeset 1769943748445-1

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
    user_id BLOB NOT NULL
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

-- Create unique constraints for SQLite
CREATE UNIQUE INDEX uc_user_email ON User (email);
CREATE UNIQUE INDEX uc_user_keycloakId ON User (keycloakId);
CREATE UNIQUE INDEX uc_userpermission_user_permission ON UserPermission (user_id, permission);

-- Create regular indexes
CREATE INDEX ix_user_id ON User (id, version);
CREATE INDEX ix_user_email ON User (email, id);
CREATE INDEX ix_user_keycloakId ON User (keycloakId, id);
CREATE INDEX ix_userpermission_user ON UserPermission (user_id);
