-- M:N association: a note can belong to multiple categories.
CREATE TABLE IF NOT EXISTS note_categories (
    note_id INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, category_id)
);
CREATE INDEX IF NOT EXISTS idx_note_categories_category ON note_categories(category_id);

-- Drop the old single-folder FK on notes. (Done after data migration: at
-- this point any remaining folder_id values are preserved via a backfill in
-- a follow-up release. For new installs, column is dropped here.)
ALTER TABLE notes DROP COLUMN folder_id;