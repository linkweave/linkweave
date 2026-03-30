-- Create Collection table
CREATE TABLE Collection (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES User(id)
);

-- Create Collection_AUD table for Hibernate Envers
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

-- Create CollectionAccess table
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
    FOREIGN KEY (collection_id) REFERENCES Collection(id),
    FOREIGN KEY (user_id) REFERENCES User(id)
);

-- Create CollectionAccess_AUD table for Hibernate Envers
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

-- Create unique constraint: one access entry per user-collection pair
CREATE UNIQUE INDEX uc_collectionaccess_collection_user ON CollectionAccess (collection_id, user_id);

-- Create indexes
CREATE INDEX ix_collection_owner ON Collection (owner_id);
CREATE INDEX ix_collectionaccess_user ON CollectionAccess (user_id);
CREATE INDEX ix_collectionaccess_collection ON CollectionAccess (collection_id);
CREATE INDEX ix_collectionaccess_user_default ON CollectionAccess (user_id, isDefault);
