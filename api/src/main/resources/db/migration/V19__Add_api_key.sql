CREATE TABLE ApiKey (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    keyHash VARCHAR(64) NOT NULL,
    keyPrefix VARCHAR(8) NOT NULL,
    expiresAt TIMESTAMP,
    lastUsedAt TIMESTAMP,
    revokedAt TIMESTAMP,
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES User(id) ON DELETE CASCADE
);

CREATE INDEX ix_apikey_user_id ON ApiKey (user_id, id);
CREATE INDEX ix_apikey_key_hash ON ApiKey (keyHash);
