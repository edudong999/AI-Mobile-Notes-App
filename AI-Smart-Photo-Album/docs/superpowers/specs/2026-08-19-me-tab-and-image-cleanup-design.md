# Me Tab + Image Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "folders" bottom-nav tab with a "我的 (Me)" tab containing account info, stats, settings, category management, and about page; add a multimodal image-cleanup feature (qwen-image-edit) that removes handwriting stains and sharpens blurry text, exposed as a button in the photo preview screen.

**Architecture:**
- Bottom-nav: 3 tabs (notes / search / me). Me is a card-style Fragment with 4 cards (account / stats / settings list / about) and child Fragments for Settings, Categories, About.
- Backend rename: `notebook_folders` → `categories`; replace `Note.folder_id` (1:1) with `note_categories` (M:N) so a note can belong to multiple categories (subject + "错题集").
- Image cleanup: new `POST /api/v1/note-image-cleanup` router + new `LLMProvider.cleanup_image(image_url) -> bytes` abstract method implemented in `DashScopeProvider` calling qwen-image-edit. Cleanup result is a new `NoteFile` with `kind='cleaned'` and `parent_file_id` pointing to the original; user chooses "insert next to original" (pass `parent_file_id`) or "save as new" (don't pass).
- `PhotoPreviewActivity` gets a "清理图片" FloatingActionButton. After cleanup, side-by-side compare view lets user pick how to save.

**Tech Stack:** Android (Java + Navigation Component + Glide + Material Chips), FastAPI + SQLAlchemy async + DashScope SDK + qwen-image-edit, pytest.

---

## Part 1 — Data model & backend

### 1.1 Files to touch

**Rename (file + class + import site):**
- `backend/app/models/notebook_folder.py` → `backend/app/models/category.py` (`NotebookFolder` → `Category`, `folder_id` → `category_id`)
- `backend/app/services/folder_service.py` → `backend/app/services/category_service.py` (all functions `folder` → `category`)
- `backend/app/routers/folders.py` → `backend/app/routers/categories.py` (prefix `/api/v1/folders` → `/api/v1/categories`)
- `backend/app/schemas/folder.py` → `backend/app/schemas/category.py` (`FolderCreate`/`FolderUpdate`/`FolderItem` → `CategoryCreate`/`CategoryUpdate`/`CategoryItem`)
- `backend/app/main.py` — remove `folders` router import, add `categories`

**New:**
- `backend/app/models/note_category.py` — M:N association table ORM
- `backend/app/routers/note_image_cleanup.py` — `POST /api/v1/note-image-cleanup`
- `backend/migrations/004_rename_folders_to_categories.sql`
- `backend/migrations/005_note_categories.sql` — M:N table + drop `notes.folder_id`
- `backend/migrations/006_note_file_kind.sql` — `note_files.kind`, `note_files.parent_file_id`
- `backend/tests/test_categories.py`
- `backend/tests/test_note_image_cleanup.py`

**Modify:**
- `backend/app/models/note.py` — drop `folder_id`, add `categories` M:N relationship
- `backend/app/models/note_file.py` — add `kind`, `parent_file_id` columns
- `backend/app/services/note_service.py` — `get_note` response includes `kind`/`parent_file_id`; new `set_note_categories(note_id, user_id, category_ids)`
- `backend/app/services/note_file_service.py` — `create_file` accepts `parent_file_id` + `kind`
- `backend/app/services/llm/provider.py` — add `async def cleanup_image(self, image_url: str) -> bytes`
- `backend/app/services/llm/dashscope_provider.py` — implement `cleanup_image` using `AioMultiModalConversation` with `model="qwen-image-edit"`, return decoded PNG bytes
- `backend/app/services/llm/mock_provider.py` — add stub `cleanup_image` returning a 1×1 PNG
- `backend/app/schemas/note.py` — `NoteFileItem` adds `kind`, `parentFileId`

---

## Part 2 — Android frontend

### 2.1 Files to touch

**Delete:**
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/folders/FolderManageFragment.java`
- `frontend/ai_photo/src/main/res/layout/fragment_folder_manage.xml`
- `frontend/ai_photo/src/main/res/layout/item_folder.xml`
- `frontend/ai_photo/src/main/res/layout/dialog_folder_edit.xml`

**Rename:**
- `frontend/ai_photo/src/main/java/com/ai_photo/data/api/FolderApi.java` → `CategoryApi.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/data/repo/FolderRepo.java` → `CategoryRepo.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/data/model/folder/*` → `data/model/category/*` (FolderCreate, FolderUpdate, FolderItem → CategoryCreate, CategoryUpdate, CategoryItem)

**New:**
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/me/MeFragment.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/me/SettingsFragment.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/me/CategoriesFragment.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/me/AboutFragment.java`
- `frontend/ai_photo/src/main/java/com/ai_photo/data/api/ImageCleanupApi.java`
- `frontend/ai_photo/src/main/res/layout/fragment_me.xml`
- `frontend/ai_photo/src/main/res/layout/fragment_settings.xml`
- `frontend/ai_photo/src/main/res/layout/fragment_categories.xml`
- `frontend/ai_photo/src/main/res/layout/fragment_about.xml`
- `frontend/ai_photo/src/main/res/layout/item_category.xml`
- `frontend/ai_photo/src/main/res/layout/dialog_image_cleanup.xml` (compare view)
- `frontend/ai_photo/src/main/res/menu/bottom_nav_note_v2.xml` (or edit `bottom_nav_note.xml` in place)

**Modify:**
- `frontend/ai_photo/src/main/res/navigation/nav_graph.xml` — remove `folderManageFragment` destination, add `meFragment`/`settingsFragment`/`categoriesFragment`/`aboutFragment`, add `categoryId` arg to `notesFragment`
- `frontend/ai_photo/src/main/res/layout/activity_main.xml` — point `bottom_nav` at new menu
- `frontend/ai_photo/src/main/res/menu/bottom_nav_note.xml` — replace `folderManageFragment` with `meFragment`
- `frontend/ai_photo/src/main/res/values/strings.xml` — drop `nav_folders`/`folder_*`, add `nav_me`/`me_*`/`category_*`/`cleanup_*`
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java` — add categories ChipGroup + handler
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NotesFragment.java` — handle `categoryId` nav arg, load filtered notes
- `frontend/ai_photo/src/main/java/com/ai_photo/ui/common/PhotoPreviewActivity.java` — add "清理图片" FAB + cleanup flow
- `frontend/ai_photo/src/main/java/com/ai_photo/data/local/NoteEntity.java` — add `categories: List<Category>` (or `categoryIds: String` JSON-encoded)
- `frontend/ai_photo/src/main/java/com/ai_photo/data/local/NoteFileEntity.java` — add `kind`, `parentFileId`

---

## Part 3 — API contracts

### 3.1 Categories (replaces folders)

**GET `/api/v1/categories`** — list user's categories
Response: `{"data": {"list": [{"categoryId": 1, "name": "数学", "color": "#4A90E2", "sortIndex": 0, "noteCount": 5}, ...]}}`

**POST `/api/v1/categories`** body `{name, color?}` → `{data: {categoryId: 1}}`
- `color` default `#4A90E2`. 400 if name already exists.

**PATCH `/api/v1/categories/{id}`** body `{name?, color?, sortIndex?}` → `{message: "已保存"}`

**DELETE `/api/v1/categories/{id}`** → `{message: "已删除"}` (cascades through `note_categories`)

**POST `/api/v1/categories/reorder`** body `{orderedIds: [1, 3, 2]}` → `{message: "已排序"}`

**POST `/api/v1/notes/{noteId}/categories`** body `{categoryIds: [1, 2]}` → `{data: {categoryIds: [1, 2]}}`
- Replaces the note's full category set. Empty array = remove all.

**GET `/api/v1/categories/{id}/notes`** → list notes in that category
Response: `{"data": {"list": [NoteListItem, ...]}}` — reuses existing note-list shape

### 3.2 Image cleanup

**POST `/api/v1/note-image-cleanup`** body `{noteId, fileId, parentFileId?: int}`
- `fileId`: which image to clean up (must belong to noteId).
- `parentFileId`: if provided, the resulting cleaned file is inserted with `kind='cleaned'` and `parent_file_id=parentFileId`. If omitted, `kind='original'` and `parent_file_id=null` ("save as new").
Response: `{data: {cleanedFileId, cleanedUrl, kind, parentFileId}}`
Errors: 404 (note/file not found), 400 (image too large / wrong format), 502 (LLM auth/balance), 504 (timeout).

---

## Part 4 — Behavioral rules

### 4.1 Cleanup flow

1. User in PhotoPreviewActivity taps "清理图片" FAB → loading overlay.
2. Frontend POSTs `/api/v1/note-image-cleanup` with `{noteId, fileId, parentFileId=<currentFileId>}` to get the preview (always treat as "insert next to original" for the preview step).
3. Backend calls LLM, stores a *temporary* preview file, returns `cleanedUrl`.
4. Frontend dismisses overlay, opens compare dialog (left = original, right = cleaned).
5. User picks one of two save modes:
   - "插入到原图旁边" → POST `/api/v1/note-image-cleanup` again with the same `parentFileId` and a `commit: true` flag (the second call commits with `kind='cleaned'`); OR
   - "另存为新图" → POST again without `parentFileId` (commits with `kind='original'`, `parent_file_id=null`).

Implementation simplification: a single endpoint call commits. Use a `mode` field in the request: `"preview" | "commit_insert" | "commit_new"`. Default in this spec is `commit_insert` when the FAB is tapped directly; the compare dialog just confirms the save. **Final shape:**
- Request: `{noteId, fileId, mode: "commit_insert" | "commit_new"}` — `mode="commit_insert"` saves with `kind='cleaned'` + `parent_file_id=fileId`; `mode="commit_new"` saves with `kind='original'` + `parent_file_id=null`.
- The endpoint always returns `cleanedFileId` + `cleanedUrl`.
- Preview step: front-end does a *throwaway* GET to a temporary URL the backend generates but does not commit. To keep things simple and ship-ready: always commit on first call (mode=commit_insert), but keep the file hidden in the UI until user confirms via compare dialog. If user picks "cancel" in compare, delete the just-created cleaned file via `DELETE /api/v1/note-files/{cleanedFileId}`. Add this DELETE if not already present in `note_files.py` router.

### 4.2 Stats card

`GET /api/v1/note-ai/status` already provides total/done/pending/processing/failed counts. Add `GET /api/v1/categories/stats` returning `{totalCategories, notesByCategory: [{categoryId, name, noteCount}]}` to populate the categories count line. (If time-constrained, fall back to deriving from existing `GET /api/v1/categories` which already has `noteCount`.)

### 4.3 NotesFragment filtering

- `notesFragment` nav graph arg: `categoryId: long = -1L` (default).
- When `categoryId > 0`, fragment calls `GET /api/v1/categories/{id}/notes`.
- Top toolbar shows breadcrumb "笔记 / 数学"; clicking "数学" pops back to me tab.

### 4.4 SettingsFragment

- Fields: server URL (reuse `ServerPrefs`), logout button.
- "保存" calls `ServerPrefs.setBaseUrl`, then `RetrofitClient.invalidate()`, then `getActivity().recreate()` to refetch.
- "退出登录" calls `Session.logout()`, then start `LoginActivity` and finish.

### 4.5 NoteDetailFragment category chips

- New ChipGroup between meta and summary sections, ID `@+id/categories_chips`.
- "Add" chip (+) opens `CategoriesPickerBottomSheet` (a `BottomSheetDialogFragment` listing all categories with checkboxes).
- Save triggers debounced (500ms) POST `/api/v1/notes/{noteId}/categories`.
- Loading state: chip alpha=0.5 while save in flight.
- Selection changes also stored in `NoteEntity.categories` locally.

### 4.6 PhotoPreviewActivity cleanup button

- FAB at bottom-end; text "清理图片"; hidden while image still loading.
- On click: show `overlay_ai_loading.xml` with label "正在清理图片…", POST request.
- On success: open `dialog_image_cleanup.xml` showing side-by-side (two `TouchImageView`s); two positive buttons "插入到原图旁边" and "另存为新图", one negative "取消".
- On negative (cancel): call DELETE on cleaned file to remove from server; dismiss dialog.
- On "插入到原图旁边": toast "已插入到原图旁边", refresh `NoteDetailFragment.filesStrip` if it's the parent.
- On "另存为新图": toast "已保存为新图片".
- On error: standard toast + server-settings dialog if network error.

### 4.7 MeFragment layout

```
ScrollView
├─ AccountCard (LinearLayout vertical, padding 16dp, white, rounded)
│   ├─ Avatar ImageView (40dp circle)
│   ├─ Username TextView (subtitle1, bold)
│   └─ ServerUrl TextView (caption, gray) + "修改" Button
├─ StatsCard
│   ├─ 总笔记数: N
│   ├─ AI 已完成: N
│   └─ 分类总数: N
├─ MenuCard (LinearLayout vertical)
│   ├─ Settings row (icon + text + chevron) → SettingsFragment
│   └─ Categories row → CategoriesFragment
└─ AboutCard
    └─ 关于 → AboutFragment
```

### 4.8 CategoriesFragment

- RecyclerView of categories with `name`, `color`, `noteCount`, sort order.
- Top-right FAB "+ 新建分类".
- Click item → rename dialog.
- Long-press item → delete confirm.
- Click row right-side count → navigate to `notesFragment` with `categoryId=...`.

---

## Part 5 — Error handling

### 5.1 Backend

| Error | HTTP | Message |
|------|------|---------|
| LLM timeout (>30s) | 504 | `图像清理超时,请重试` |
| LLM auth / balance | 502 | `图像编辑服务不可用` |
| Image format / size rejected by dashscope | 400 | `图片格式不支持,请使用 JPEG/PNG,不超过 10MB` |
| noteId not owned by user | 404 | `笔记不存在` |
| fileId not in noteId | 404 | `图片不存在` |
| category name duplicate | 400 | `分类名已存在` |

### 5.2 Frontend

- Network errors → existing `Result.Network` handling + server-settings dialog.
- LLM 502/504 → toast with backend message, retry button in compare dialog.
- Image > 10MB → client-side downsample via Glide before send (limit).
- Loading overlay stays if `getView() != null`; view destruction in flight → reset `isCleanupRunning = false` in `onDestroyView` (mirror existing `isAiRunning` fix pattern).

---

## Part 6 — Testing

### 6.1 Backend (pytest, each ≥3 cases)

`tests/test_categories.py`:
- create + list (verify noteCount=0)
- create duplicate name → 400
- delete → cascades to `note_categories`
- rename + reorder

`tests/test_note_categories.py`:
- set note categories, replace, empty clears all
- read categories from note response

`tests/test_note_image_cleanup.py`:
- commit_insert: returns cleaned file with kind='cleaned', parent_file_id=fileId
- commit_new: returns cleaned file with kind='original', parent_file_id=null
- wrong fileId → 404
- LLM timeout (monkeypatch provider.cleanup_image → asyncio.sleep + raise) → 504

`tests/test_dashscope_provider_cleanup_image.py`:
- happy path (mock dashscope returns 200 + base64 PNG)
- non-200 → LLMAuthError
- timeout → LLMTimeoutError

### 6.2 Frontend (manual smoke test)

- Me tab 4 cards render and routes navigate correctly.
- Create / rename / delete category; NotesFragment filtered view shows correct notes.
- Note detail multi-select categories; persists after refresh.
- PhotoPreviewActivity cleanup: normal path returns compare view; network failure shows server-settings dialog.
- Cancel button in compare removes cleaned file from server.
- Repeated cleanup taps don't double-process (button disabled during request).

---

## Part 7 — Out of scope (YAGNI)

- Category sharing / collaboration
- Category CSV import/export
- Watermarking cleaned images
- Offline local LLM
- User-defined hex colors (8-preset palette only)
- Custom cleanup prompts (avoid injection)
- Android instrumentation tests (no existing androidTest suite; smoke-test instead)

---

## Self-review notes

- No "TBD" / "TODO" placeholders. ✅
- Field names consistent across schema/model/JSON (`category_id`, `categoryId`, `cleanedFileId`, `parentFileId`). ✅
- Single endpoint for image cleanup, not two — simpler client + single source of truth. ✅
- Cancellation handled by DELETE on the cleaned file (existing `note_files.py` DELETE if not present, add it). ✅
- M:N table cleans up old `folder_id` data via migration `005` (drop column after data is migrated). ✅