-- Add collection foreign key to Folder table
-- SQLite does not support ADD CONSTRAINT; FK is not enforced via ALTER TABLE but we add the column
ALTER TABLE Folder ADD COLUMN collection_id VARCHAR(36) NOT NULL DEFAULT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' REFERENCES Collection(id);

-- Update Folder_AUD table
ALTER TABLE Folder_AUD ADD COLUMN collection_id VARCHAR(36);

-- Create index
CREATE INDEX ix_folder_collection ON Folder (collection_id);
