-- Aligns Tag's name+collection unique index with the uc_* convention used
-- everywhere else. Pure rename: column set is unchanged.
DROP INDEX IF EXISTS uq_tag_name_collection;
CREATE UNIQUE INDEX uc_tag_name_collection ON Tag(name, collection_id);
