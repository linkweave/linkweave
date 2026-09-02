-- UC-112 / FR-105: per-collection opt-out for LLM tag suggestions.
--
-- DEFAULT 1 is the point of the migration, not an incidental choice: LLM
-- auto-tagging (FR-095) is already active in every collection, so defaulting to
-- 0 would silently withdraw a working feature from every existing user at
-- deploy time. Members opt out; they do not have to opt back in. This is the
-- opposite default from screenshotEnabled, which was a new and costly feature
-- and therefore opt-in (UC-054 BR-115).
ALTER TABLE Collection ADD COLUMN aiTaggingEnabled BOOLEAN NOT NULL DEFAULT 1;

-- Collection is @Audited, and an _AUD column that is missing makes Envers fail
-- at write time rather than at startup. Nullable and without a default, as every
-- other _AUD column is: a revision row records what the value was, and rows
-- written before this column existed genuinely have no value for it.
ALTER TABLE Collection_AUD ADD COLUMN aiTaggingEnabled BOOLEAN;
