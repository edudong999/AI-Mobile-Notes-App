-- M:N association: a note can belong to multiple categories.
CREATE TABLE IF NOT EXISTS note_categories (
    note_id INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, category_id)
);
CREATE INDEX IF NOT EXISTS idx_note_categories_category ON note_categories(category_id);

-- Drop the obsolete index that referenced folder_id (idempotent — IF EXISTS).
-- The column drop itself lives in 004_note_tables.sql where the table is
-- created, so freshly created notes table already excludes folder_id.
DROP INDEX IF EXISTS idx_notes_user_folder;