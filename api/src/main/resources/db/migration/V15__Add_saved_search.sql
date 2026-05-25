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

CREATE INDEX ix_saved_search_collection_id ON SavedSearch (collection_id, id);
