-- 004_note_tables.sql
-- 笔记模块 5 张表 + ai_tasks 扩展 kind/note_id/sub_kind。
-- 与 001/003 一致：SQLite，全 TEXT/INTEGER + CHECK + DEFAULT CURRENT_TIMESTAMP，
-- IF NOT EXISTS / ALTER TABLE ADD COLUMN 兼容已有库。

CREATE TABLE IF NOT EXISTS notes (
  note_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  folder_id      INTEGER REFERENCES notebook_folders(folder_id) ON DELETE SET NULL,
  title          TEXT NOT NULL DEFAULT '',
  text_content   TEXT NOT NULL DEFAULT '',
  summary        TEXT NOT NULL DEFAULT '',
  ai_status      TEXT NOT NULL DEFAULT 'pending'
                    CHECK (ai_status IN ('pending','processing','done','failed')),
  ocr_engine     TEXT,
  is_archived    INTEGER NOT NULL DEFAULT 0,
  created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at     TEXT
);
CREATE INDEX IF NOT EXISTS idx_notes_user_folder ON notes(user_id, folder_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_notes_user_updated ON notes(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS notebook_folders (
  folder_id   INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  color       TEXT NOT NULL DEFAULT '#4A90E2',
  sort_index  INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, name)
);

CREATE TABLE IF NOT EXISTS note_files (
  file_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  note_id        INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id        INTEGER NOT NULL,
  file_name      TEXT NOT NULL,
  original_path  TEXT NOT NULL,
  thumbnail_path TEXT,
  width          INTEGER,
  height         INTEGER,
  sort_index     INTEGER NOT NULL DEFAULT 0,
  created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_note_files_note ON note_files(note_id, sort_index);

CREATE TABLE IF NOT EXISTS note_questions (
  question_id    INTEGER PRIMARY KEY AUTOINCREMENT,
  note_id        INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id        INTEGER NOT NULL,
  question_type  TEXT NOT NULL,
  stem           TEXT NOT NULL,
  options_json   TEXT,
  answer         TEXT NOT NULL,
  explanation    TEXT NOT NULL,
  difficulty     TEXT,
  sort_index     INTEGER NOT NULL DEFAULT 0,
  created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_note_questions_note ON note_questions(note_id, sort_index);

CREATE TABLE IF NOT EXISTS note_embeddings (
  note_id     INTEGER PRIMARY KEY REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id     INTEGER NOT NULL,
  vector_json TEXT NOT NULL,
  dim         INTEGER NOT NULL,
  model       TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ai_tasks ADD COLUMN kind TEXT NOT NULL DEFAULT 'photo';
ALTER TABLE ai_tasks ADD COLUMN note_id INTEGER REFERENCES notes(note_id) ON DELETE CASCADE;
ALTER TABLE ai_tasks ADD COLUMN sub_kind TEXT;
CREATE INDEX IF NOT EXISTS idx_ai_tasks_kind ON ai_tasks(kind, status);
