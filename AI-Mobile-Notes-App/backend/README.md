# AI 智能相册后端

## 启动

```bash
# 1. 安装依赖
pip install -e ".[dev]"

# 2. 复制环境变量
cp .env.example .env
# 编辑 .env，至少填入 DASHSCOPE_API_KEY（mock 模式可暂时留空）

# 3. 启动（首次会自动建表 + seed 60 个分类）
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

默认数据库是 **SQLite**（单文件 `./data/ai_album.db`，无需安装服务）。如需切换到 PostgreSQL，只需修改 `.env` 中的 `DATABASE_URL` 为 `postgresql+asyncpg://...` 并安装 `asyncpg`。

访问 http://localhost:8000/docs 查看 OpenAPI 文档。

## 测试

```bash
pytest -v
```

测试默认使用 **内存 SQLite**，无需任何外部依赖。

## 配置项

见 `.env.example`。关键变量：
- `DATABASE_URL`：默认 `sqlite+aiosqlite:///./data/ai_album.db`
- `AI_SERVICE`：mock / real
- `DASHSCOPE_API_KEY`：阿里云百炼 API Key（`AI_SERVICE=real` 时必填）
- `MAX_UPLOAD_SIZE_MB`：单文件大小上限

## AI Note Assistant (Tasks 1–20, completed 2026-08-17)

The original `AI-Smart-Photo-Album` was extended into a dual-mode assistant:

- **PHOTO mode** (preserved): smart photo album with AI categories.
- **NOTE mode** (new): Markdown-backed notes with folders, file upload, OCR, summary, polish/translate, question generation, keyword + semantic + hybrid search.

### 7 Core Features
1. **Folder CRUD** — `POST/GET/PATCH/DELETE /api/v1/folders` + Android `FolderManageFragment`.
2. **Note CRUD** — `POST/GET/PATCH/DELETE /api/v1/notes` + Android `NotesFragment` list + `CaptureFragment` create.
3. **Note-file upload** — multipart `POST /api/v1/note-files/upload` + Android upload from `CaptureFragment`. HEIC/HEIF magic-byte normalization.
4. **AI: OCR / Summary / Questions** — async AI worker dispatches `kind=note` jobs; sync `/note-ai/ocr|summary|questions` endpoints (60s timeout).
5. **AI: Polish / Translate** — sync `/note-ai/polish|translate` endpoints (30s timeout) with action param (polish/expand/shorten) and target_lang param (en/zh).
6. **Embeddings + Hybrid Search** — `note_embeddings` table, `_cosine` ranking; `POST /api/v1/note-search` with mode=keyword|semantic|hybrid.
7. **AppMode Toggle** — `ProfileFragment` switch in Android flips `AppMode` between PHOTO and NOTE; MainActivity swaps the bottom-nav menu.

### Backend modules
- `app/services/llm/` — `LLMProvider` ABC, `MockProvider`, `DashScopeProvider` (factory in `__init__.py`).
- `app/services/note_service.py` — note CRUD.
- `app/services/folder_service.py` — folder CRUD (422 on duplicate).
- `app/services/note_file_service.py` — file upload with HEIC magic-byte normalization.
- `app/services/note_ai_service.py` — `run_ocr / run_summary / run_questions / run_polish / run_translate`.
- `app/services/note_embedding_service.py` — chunked mean-pool + L2-normalize.
- `app/services/note_search_service.py` — keyword + semantic + hybrid fallback.
- `app/services/export_service.py` — `md / zip` export.
- `migrations/004_note_tables.sql` — 5 new tables + `ai_tasks` extension.
- `app/models/ai_task.py` — refactored to dispatch `kind=photo|note` (`sub_kind` for notes).

### Android module (`frontend/ai_photo/`)
- `util/AppMode.java` — enum + SharedPreferences persistence.
- `data/local/AppDatabase.java` — Room database (5 entities, 5 DAOs).
- `data/repo/NoteRepo` / `NoteAiRepo` / `NoteSearchRepo` — Retrofit + Room.
- `data/api/ApiService.java` — extended from 27 to 47 endpoints.
- `ui/notes/` — `NotesFragment`, `NotesAdapter`, `NoteDetailFragment`, `NoteAiActions`, `CaptureFragment`, `QuestionBankFragment`, `NoteSearchFragment`.
- `ui/folders/` — `FolderManageFragment`.
- `res/menu/bottom_nav_{note,photo}.xml` — split for mode-aware nav.
- `res/layout/fragment_notes.xml`, `fragment_note_detail.xml`, `fragment_capture.xml`, `fragment_folder_manage.xml`, `fragment_question_bank.xml`, `fragment_note_search.xml`, plus item_*, dialog_*.