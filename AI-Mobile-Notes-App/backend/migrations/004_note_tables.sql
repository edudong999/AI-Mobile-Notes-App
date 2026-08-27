-- 004_note_tables.sql
-- 笔记模块 5 张表 + users（替代旧 photo 模式删除的 001）+ ai_tasks 重建。
-- 与 001/003 一致：SQLite，全 TEXT/INTEGER + CHECK + DEFAULT CURRENT_TIMESTAMP，
-- IF NOT EXISTS 兼容已有库。

-- users：注册/登录依赖
CREATE TABLE IF NOT EXISTS users (
  user_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  username       TEXT NOT NULL UNIQUE,
  password_hash  TEXT NOT NULL,
  email          TEXT UNIQUE,
  avatar_url     TEXT,
  status         INTEGER NOT NULL DEFAULT 1,
  last_login_at  TEXT,
  created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notes (
  note_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
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
-- idx_notes_user_folder dropped: folder_id was dropped by 005_note_categories.
-- 005_note_categories.sql drops the index if it still exists.
CREATE INDEX IF NOT EXISTS idx_notes_user_updated ON notes(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS categories (
  category_id INTEGER PRIMARY KEY AUTOINCREMENT,
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

-- ai_tasks：笔记 AI 任务队列（替代旧 photo 任务的统一队列）
CREATE TABLE IF NOT EXISTS ai_tasks (
  task_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  note_id        INTEGER REFERENCES notes(note_id) ON DELETE CASCADE,
  kind           TEXT NOT NULL DEFAULT 'note',
  sub_kind       TEXT,
  status         TEXT NOT NULL DEFAULT 'queued'
                   CHECK (status IN ('queued','processing','succeeded','failed')),
  error_message  TEXT,
  created_at     TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retry_count    INTEGER NOT NULL DEFAULT 0,
  max_retries    INTEGER NOT NULL DEFAULT 3,
  next_retry_at  TEXT,
  claimed_at     TEXT,
  claimed_by     TEXT,
  heartbeat_at   TEXT,
  finished_at    TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_tasks_note ON ai_tasks(note_id);
CREATE INDEX IF NOT EXISTS idx_ai_tasks_status ON ai_tasks(status, next_retry_at);
