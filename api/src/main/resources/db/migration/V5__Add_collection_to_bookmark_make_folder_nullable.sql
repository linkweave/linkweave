-- Add collection_id to Bookmark table
-- SQLite does not support ADD CONSTRAINT; FK is not enforced via ALTER TABLE but we add the column
ALTER TABLE Bookmark ADD COLUMN collection_id VARCHAR(36) NOT NULL DEFAULT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' REFERENCES Collection(id);

-- Update Bookmark_AUD table
ALTER TABLE Bookmark_AUD ADD COLUMN collection_id VARCHAR(36);

-- Create index
CREATE INDEX ix_bookmark_collection ON Bookmark (collection_id);

-- Make folder_id nullable by recreating the Bookmark table
-- SQLite does not support ALTER COLUMN, so we must recreate the table

-- Step 1: Create new Bookmark table with nullable folder_id
CREATE TABLE Bookmark_new (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    notes VARCHAR(5000),
    collection_id VARCHAR(36) NOT NULL REFERENCES Collection(id),
    folder_id VARCHAR(36) REFERENCES Folder(id)
);

-- Step 2: Copy data from old table to new table
INSERT INTO Bookmark_new (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, title, url, notes, collection_id, folder_id)
SELECT id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, title, url, notes, collection_id, folder_id FROM Bookmark;

-- Step 3: Drop old table
DROP TABLE Bookmark;

-- Step 4: Rename new table to original name
ALTER TABLE Bookmark_new RENAME TO Bookmark;

-- Recreate indexes
CREATE INDEX ix_bookmark_title ON Bookmark (title);
CREATE INDEX ix_bookmark_folder_id ON Bookmark (folder_id);
CREATE INDEX ix_bookmark_collection ON Bookmark (collection_id);
