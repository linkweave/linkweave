-- V13 declared valueNumber as DECIMAL(19, 2). sqlite-jdbc reports a column
-- declared DECIMAL as Types#FLOAT, while Hibernate expects Types#NUMERIC for
-- the BigDecimal mapping — so schema validation logs an error on every boot.
-- Declaring the column NUMERIC(19, 2) makes sqlite-jdbc report Types#NUMERIC;
-- SQLite itself treats DECIMAL and NUMERIC identically (NUMERIC affinity), so
-- the data semantics are unchanged. SQLite cannot ALTER a column type, hence
-- the table rebuilds.

CREATE TABLE BookmarkPropertyValue_new (
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

INSERT INTO BookmarkPropertyValue_new (
    id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
    bookmark_id, propertyDefinition_id, valueText, valueNumber, valueBoolean
)
SELECT
    id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
    bookmark_id, propertyDefinition_id, valueText, valueNumber, valueBoolean
FROM BookmarkPropertyValue;

DROP TABLE BookmarkPropertyValue;
ALTER TABLE BookmarkPropertyValue_new RENAME TO BookmarkPropertyValue;

CREATE UNIQUE INDEX uc_bookmark_property_value_bookmark_definition ON BookmarkPropertyValue (bookmark_id, propertyDefinition_id);
CREATE INDEX ix_bookmark_property_value_definition_id ON BookmarkPropertyValue (propertyDefinition_id, bookmark_id, id);

CREATE TABLE BookmarkPropertyValue_AUD_new (
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

INSERT INTO BookmarkPropertyValue_AUD_new (
    id, REV, REVTYPE, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
    bookmark_id, propertyDefinition_id, valueText, valueNumber, valueBoolean
)
SELECT
    id, REV, REVTYPE, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
    bookmark_id, propertyDefinition_id, valueText, valueNumber, valueBoolean
FROM BookmarkPropertyValue_AUD;

DROP TABLE BookmarkPropertyValue_AUD;
ALTER TABLE BookmarkPropertyValue_AUD_new RENAME TO BookmarkPropertyValue_AUD;
