-- UC-103 / FR-099: manual bookmark ordering within a folder group.
ALTER TABLE Bookmark ADD COLUMN sortOrder BIGINT NOT NULL DEFAULT 0;
ALTER TABLE Bookmark_AUD ADD COLUMN sortOrder BIGINT;

-- Backfill: sparse rank (steps of 1000) within each folder group (folder_id
-- NULL = unfiled), derived from creation order so the visible order does not
-- change (BR-192).
UPDATE Bookmark SET sortOrder = (
    SELECT ranked.rn * 1000
    FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY collection_id, folder_id
            ORDER BY timestampErstellt, id
        ) AS rn
        FROM Bookmark
    ) ranked
    WHERE ranked.id = Bookmark.id
)
-- Matches every row (ADD COLUMN ... DEFAULT 0 just set them all); kept as a guard so
-- re-running the backfill by hand cannot clobber orders users have since arranged.
WHERE sortOrder = 0;

-- UC-102/UC-103: the sibling queries filter one folder group and order by the
-- manual sort order (WHERE collection_id = ? AND folder_id/parent_id = ?
-- ORDER BY sortOrder, ...). Cover the filter + ORDER BY prefix so large
-- groups don't sort in memory.
CREATE INDEX ix_bookmark_group_sort ON Bookmark (collection_id, folder_id, sortOrder, id);
CREATE INDEX ix_folder_group_sort ON Folder (collection_id, parent_id, sortOrder, id);
