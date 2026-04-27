ALTER TABLE Bookmark ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE Bookmark_AUD ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX ix_bookmark_deleted_at ON Bookmark (deleted_at);

ALTER TABLE Folder ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE Folder_AUD ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX ix_folder_deleted_at ON Folder (deleted_at);
