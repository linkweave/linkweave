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

CREATE INDEX ix_auto_tag_rule_collection_id ON AutoTagRule (collection_id, sortOrder, id);
