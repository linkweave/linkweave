-- Add collection_id and color to Tag table
-- SQLite does not support ADD CONSTRAINT; FK is not enforced via ALTER TABLE but we add the column
ALTER TABLE Tag ADD COLUMN collection_id VARCHAR(36) NOT NULL DEFAULT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' REFERENCES Collection(id);

-- Add color column with default
ALTER TABLE Tag ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#646464';

-- Update Tag_AUD table
ALTER TABLE Tag_AUD ADD COLUMN collection_id VARCHAR(36);
ALTER TABLE Tag_AUD ADD COLUMN color VARCHAR(7);

-- Create index
CREATE INDEX ix_tag_collection ON Tag (collection_id);

-- Rename Bookmark.notes to description and change length from 5000 to 1000
-- SQLite does not support ALTER COLUMN, so we must recreate the table

-- Step 1: Create new Bookmark table with correct column name and length
CREATE TABLE Bookmark_new (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestampErstellt TIMESTAMP NOT NULL,
    timestampMutiert TIMESTAMP NOT NULL,
    userErstellt VARCHAR(255) NOT NULL,
    userMutiert VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    description VARCHAR(1000),
    collection_id VARCHAR(36) NOT NULL REFERENCES Collection(id),
    folder_id VARCHAR(36) REFERENCES Folder(id)
);

-- Step 2: Copy data from old table to new table (rename notes to description)
INSERT INTO Bookmark_new (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, title, url, description, collection_id, folder_id)
SELECT id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, title, url, notes, collection_id, folder_id FROM Bookmark;

-- Step 3: Drop old table
DROP TABLE Bookmark;

-- Step 4: Rename new table to original name
ALTER TABLE Bookmark_new RENAME TO Bookmark;

-- Recreate indexes
CREATE INDEX ix_bookmark_title ON Bookmark (title);
CREATE INDEX ix_bookmark_folder_id ON Bookmark (folder_id);
CREATE INDEX ix_bookmark_collection ON Bookmark (collection_id);

-- Update Bookmark_AUD table
-- SQLite doesn't support renaming columns easily, so we add the new column and drop the old
-- For audit tables, we just add the new column
ALTER TABLE Bookmark_AUD ADD COLUMN description VARCHAR(1000);
