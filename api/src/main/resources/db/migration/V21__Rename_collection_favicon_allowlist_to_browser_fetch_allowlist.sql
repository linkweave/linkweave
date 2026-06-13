-- The per-collection list named "faviconAllowlist" actually governs which hosts
-- the browser fetches directly (favicons) AND which the backend must skip
-- (favicons + screenshots). Rename it to reflect that broader meaning.
ALTER TABLE Collection RENAME COLUMN faviconAllowlist TO browserFetchAllowlist;
ALTER TABLE Collection_AUD RENAME COLUMN faviconAllowlist TO browserFetchAllowlist;
