-- Envers auditing was silently inactive for every entity except User,
-- UserSettings and UserPermission: @Audited sat only on the @MappedSuperclass
-- AbstractEntity, and org.hibernate.envers.Audited is NOT @Inherited, so no
-- subclass was ever audited. The _AUD tables below were written by hand in V1
-- and stayed empty. @Audited is now declared on each entity class; this
-- migration closes the resulting schema gaps.

-- Bookmark gained screenshot_captured_at without the matching _AUD column
-- (V4/V5 did mirror their columns, this one was missed).
ALTER TABLE Bookmark_AUD ADD COLUMN screenshot_captured_at TIMESTAMP;

-- ApiKey was added after V1's _AUD tables were generated, so it never got one.
CREATE TABLE ApiKey_AUD (
    id VARCHAR(36) NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    timestampErstellt TIMESTAMP,
    timestampMutiert TIMESTAMP,
    userErstellt VARCHAR(255),
    userMutiert VARCHAR(255),
    version BIGINT,
    user_id VARCHAR(36),
    name VARCHAR(100),
    keyHash VARCHAR(64),
    keyPrefix VARCHAR(8),
    expiresAt TIMESTAMP,
    lastUsedAt TIMESTAMP,
    revokedAt TIMESTAMP,
    PRIMARY KEY (REV, id)
);

-- Bookmark.tags is an owning @ManyToMany; Envers audits the join table itself,
-- which needs its own _AUD (REVTYPE tracks the add/remove of each pairing).
CREATE TABLE Bookmark_Tag_AUD (
    REV INTEGER NOT NULL,
    bookmark_id VARCHAR(36) NOT NULL,
    tag_id VARCHAR(36) NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (REV, bookmark_id, tag_id)
);
