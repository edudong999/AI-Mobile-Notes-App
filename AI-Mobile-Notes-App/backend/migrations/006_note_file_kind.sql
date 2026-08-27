-- note_files: kind + parent_file_id columns
ALTER TABLE note_files ADD COLUMN kind TEXT NOT NULL DEFAULT 'original';
ALTER TABLE note_files ADD COLUMN parent_file_id INTEGER
    REFERENCES note_files(file_id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_note_files_parent ON note_files(parent_file_id);