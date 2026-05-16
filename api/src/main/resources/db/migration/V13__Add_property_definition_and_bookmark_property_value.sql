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

CREATE TABLE BookmarkPropertyValue (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    bookmark_id VARCHAR(36) NOT NULL,
    propertyDefinition_id VARCHAR(36) NOT NULL,
    valueText VARCHAR(255),
    valueNumber DECIMAL(19, 2),
    valueBoolean BOOLEAN,
    CONSTRAINT fk_bookmark_property_value_bookmark FOREIGN KEY (bookmark_id) REFERENCES Bookmark(id),
    CONSTRAINT fk_bookmark_property_value_definition FOREIGN KEY (propertyDefinition_id) REFERENCES PropertyDefinition(id)
);

CREATE TABLE BookmarkPropertyValue_AUD (
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
    valueNumber DECIMAL(19, 2),
    valueBoolean BOOLEAN,
    PRIMARY KEY (REV, id)
);

CREATE UNIQUE INDEX uc_property_definition_name_collection ON PropertyDefinition (name, collection_id);
CREATE INDEX ix_property_definition_collection_id ON PropertyDefinition (collection_id, sortOrder, id);

CREATE UNIQUE INDEX uc_bookmark_property_value_bookmark_definition ON BookmarkPropertyValue (bookmark_id, propertyDefinition_id);
CREATE INDEX ix_bookmark_property_value_definition_id ON BookmarkPropertyValue (propertyDefinition_id, bookmark_id, id);
