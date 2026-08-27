-- 005_mindmap.sql
-- 给 notes 表加 mindmap_json 列，存最近一次生成的思维导图树（JSON 字符串）。
-- 兼容已有库：ALTER TABLE ... ADD COLUMN 在 SQLite IF NOT EXISTS 不可用，
-- 用 PRAGMA table_info 先查，存在则跳过。

ALTER TABLE notes ADD COLUMN mindmap_json TEXT;