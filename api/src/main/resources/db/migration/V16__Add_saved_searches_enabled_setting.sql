ALTER TABLE UserSettings ADD COLUMN savedSearchesEnabled BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE UserSettings_AUD ADD COLUMN savedSearchesEnabled BOOLEAN;
UPDATE UserSettings SET savedSearchesEnabled = 1 WHERE savedSearchesEnabled IS NULL OR savedSearchesEnabled = 0;
