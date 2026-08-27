-- Rename notebook_folders → categories (idempotent).
-- Old PK was folder_id; new PK is category_id. The rename is done in two
-- steps: rename table, then rename PK column. SQLite table rename keeps
-- the original column names, so the column rename is what brings the DB
-- in line with the app model (which uses category_id).
--
-- Both steps are in this file but, for runs on fresh DBs, 004_note_tables.sql
-- already creates the table with category_id; this file is a no-op.

