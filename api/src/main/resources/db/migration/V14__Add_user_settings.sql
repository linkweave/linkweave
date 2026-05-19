CREATE TABLE UserSettings (
    user_id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    offlineCachingEnabled BOOLEAN NOT NULL DEFAULT TRUE,
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
    offlineCachingEnabled BOOLEAN,
    PRIMARY KEY (REV, user_id)
);

CREATE INDEX ix_usersettings_id ON UserSettings (user_id, version);

INSERT INTO UserSettings (user_id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, offlineCachingEnabled)
SELECT id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, TRUE
FROM User;
