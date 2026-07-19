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
