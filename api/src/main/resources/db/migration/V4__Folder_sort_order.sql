-- UC-102 / FR-098: manual folder ordering.
ALTER TABLE Folder ADD COLUMN sortOrder BIGINT NOT NULL DEFAULT 0;
ALTER TABLE Folder_AUD ADD COLUMN sortOrder BIGINT;

-- Backfill: sparse rank (steps of 1000) within each sibling group, derived from
-- creation order so the visible order does not change (BR-186).
UPDATE Folder SET sortOrder = (
    SELECT ranked.rn * 1000
    FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY collection_id, parent_id
            ORDER BY timestampErstellt, id
        ) AS rn
        FROM Folder
    ) ranked
    WHERE ranked.id = Folder.id
) where sortOrder is 0;
