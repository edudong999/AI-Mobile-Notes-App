# AI 随身图文笔记助手 — 设计文档

- 日期：2026-08-17
- 项目：项目四 · AI 随身图文笔记助手（大模型方向）
- 阶段：MVP 设计 / 待用户审阅

## 1. 背景与目标

把现有 `AI-Smart-Photo-Album`（智能相册方向）改造为 `AI-Note-Assistant`（智能笔记方向），按 `项目4.pdf` 实现全部 7 个核心功能：

1. 拍照 / 相册导入试卷、手写笔记、书本图片，批量上传解析
2. 多模态大模型识别图片文字，去除手写污渍、修正模糊文字
3. 自动提炼核心知识点，生成简短摘要笔记
4. 针对试卷内容生成配套练习题 + 答案解析
5. 纯文字笔记 AI 优化：润色 / 扩写 / 精简 / 中英文翻译
6. 自然语言检索笔记（如「高数极限知识点笔记」「上周英语错题」）
7. 笔记自定义分类（学习 / 工作 / 生活），支持笔记导出保存

**已锁定的关键决策**（来自 brainstorming）：

- **MVP 范围**：完整 7 功能，不分阶段
- **后端策略**：在现有 FastAPI 应用内改造复用，不新建工程；保留 User/JWT/auth/upload 基础设施
- **Android 存储**：新增 Room 本地数据库（按 PDF 要求），与现有 server-only 模式并存
- **LLM 集成**：`LLMProvider` 抽象 + `DashScopeProvider` 默认实现 + `MockProvider` 兜底；API key 走环境变量，**不进代码**
- **多用户**：沿用现有 JWT auth，所有笔记 / 文件夹 / 文件按 `user_id` 隔离
- **旧 Photo 模块**：代码保留、路由保留、Android 加「笔记 / 相册」模式切换；两套模块互不干扰

## 2. 范围与非范围

**范围**（MVP 必须实现）：

- 笔记 CRUD + 文件夹 CRUD + 笔记图片附件上传
- OCR + 摘要 + 出题 AI 链路（基于多模态 LLM）
- 文字 AI 优化 4 种动作（polish / expand / shorten / translate）
- 自然语言语义检索（embedding + 余弦相似度，MVP 纯内存）
- 关键词 LIKE 检索（兜底）
- 笔记导出（Markdown 文件 + 关联图片 zip）
- Room 本地缓存 + 同步
- Mock LLM 全链路可跑

**非范围**（明确不做）：

- 笔记富文本 / WYSIWYG 编辑（用 Markdown）
- 实时协同编辑
- 离线写入 + WorkManager 后台同步（只做离线只读）
- 思维导图可视化渲染（OCR/导出阶段可输出 Mermaid 文本，但不渲染）
- 本地 LLM 推理
- iOS / Web 端

## 3. 系统架构

### 3.1 整体

两个工程，**单进程后端 + 单 APK**：

```
┌──────────────────────────┐         ┌──────────────────────────────┐
│  Android (Kotlin + Java)  │         │   FastAPI (Python 3.11+)     │
│                          │         │                              │
│  - NotesFragment         │  HTTPS  │  - notes / folders / files   │
│  - NoteDetailFragment    │ ◄─────► │  - note-ai (worker)          │
│  - CaptureFragment       │         │  - note-search               │
│  - SearchFragment        │         │                              │
│  - FolderManageFragment  │         │  AIWorker (asyncio task)     │
│  - AiQueueFragment       │         │      │                       │
│                          │         │      ▼                       │
│  Room (本地缓存)         │         │  LLMProvider (abc)           │
│                          │         │      ├─ DashScopeProvider    │
│  Markwon (Markdown)      │         │      └─ MockProvider         │
└──────────────────────────┘         └──────────────────────────────┘
                                                │
                                                ▼
                                    SQLite (WAL) + .data/{origin,thumb,note_files}
```

### 3.2 后端模块

| 模块              | 路由前缀                  | 文件                                       | 职责                                                            |
| ----------------- | ------------------------- | ------------------------------------------ | --------------------------------------------------------------- |
| `notes`           | `/api/v1/notes`           | `app/routers/notes.py`                     | 笔记 CRUD、按 folder/时间分页、归档                            |
| `folders`         | `/api/v1/folders`          | `app/routers/folders.py`                     | 文件夹 CRUD、排序、笔记计数                                     |
| `note_files`      | `/api/v1/note-files`      | `app/routers/note_files.py`                | 图片上传（multipart）、缩略图                                  |
| `note_ai`         | `/api/v1/note-ai`         | `app/routers/note_ai.py`                   | OCR/摘要/出题/润色/扩写/精简/翻译；状态查询 + 重试              |
| `note_search`     | `/api/v1/note-search`     | `app/routers/note_search.py`               | 关键词 LIKE + 语义检索；engine 字段标识                       |
| `llm`             | —                         | `app/services/llm/`                        | `provider.py` 抽象 + `dashscope_provider.py` + `mock_provider.py` |
| `note_embedding`  | —                         | `app/services/note_embedding.py`           | 调 embed、写入 `note_embeddings`、余弦检索                     |

**沿用现有**：`auth` / `users` / `categories`（保留不动，旧相册功能仍可用）/ `ai`（保留旧 photo AI，但加 `kind='photo'` 区分）/ `favorites` / `ai_worker` 基础设施（改造成兼容 note/photo 两种 job）。

### 3.3 Android 导航

底部 4 tab + 顶层跳转。模式（笔记 / 相册）由 `MainActivity` 持有一个 `AppMode` 单例（默认笔记），从 `ProfileFragment` 的 toggle 切换后重建 `BottomNavigationView` 的 menu。

| Tab          | Fragment                | 模式切到"相册"时替换为    |
| ------------ | ----------------------- | ------------------------- |
| 笔记         | `NotesFragment`         | `PhotoListFragment`       |
| 搜索         | `SearchFragment`        | `SearchFragment`（相册版） |
| AI 队列      | `AiQueueFragment`       | `AiQueueFragment`（相册版）|
| 我的         | `ProfileFragment`       | `ProfileFragment`（相册版）|

**新增 Fragment**：
- `NoteDetailFragment`：上半 `RecyclerView` 显示附图 + 下半 Markdown 编辑器（`Markwon`） + 浮动工具栏（润色/翻译/扩写/精简/出题）
- `CaptureFragment`：相机/相册 → 多选 → 上传 → 选文件夹 → 创建草稿
- `FolderManageFragment`：CRUD + 拖拽排序
- `QuestionBankFragment`：题目列表（按 note 聚合）+ 单题答题模式 + 答案/解析

**Room 实体**（`com.ai_photo.data.local` 包）：

| Entity              | 字段                                                            |
| ------------------- | --------------------------------------------------------------- |
| `FolderEntity`      | id / name / color / sortIndex                                   |
| `NoteEntity`        | id / folderId / title / textContent / summary / aiStatus / createdAt / updatedAt / dirty |
| `NoteFileEntity`    | id / noteId / localPath / remoteUrl / sortIndex                 |
| `AiJobEntity`       | id / noteId / kind / status / result / errorMessage             |
| `EmbeddingMetaEntity` | noteId / syncedAt                                              |

`EmbeddingMetaEntity` 只存同步标记，向量本身存服务端（避免 Room BLOB 同步复杂）。

## 4. 数据模型（数据库表）

复用现有 `users` / `ai_tasks` 表（`ai_tasks` 加 `kind` 字段：`'photo'|'note'`），新增 5 张表：

### `notes`
```sql
CREATE TABLE notes (
  note_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  folder_id      INTEGER REFERENCES notebook_folders(folder_id) ON DELETE SET NULL,
  title          TEXT NOT NULL DEFAULT '',
  text_content   TEXT NOT NULL DEFAULT '',
  summary        TEXT NOT NULL DEFAULT '',
  ai_status      TEXT NOT NULL DEFAULT 'pending',  -- pending/processing/done/failed
  ocr_engine     TEXT,                             -- 'qwen-vl-max' / 'mock'
  is_archived    INTEGER NOT NULL DEFAULT 0,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at     TIMESTAMP
);
CREATE INDEX idx_notes_user_folder ON notes(user_id, folder_id, deleted_at);
CREATE INDEX idx_notes_user_updated ON notes(user_id, updated_at DESC);
```

### `notebook_folders`
```sql
CREATE TABLE notebook_folders (
  folder_id   INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  color       TEXT NOT NULL DEFAULT '#4A90E2',
  sort_index  INTEGER NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, name)
);
```

### `note_files`（笔记附图）
```sql
CREATE TABLE note_files (
  file_id      INTEGER PRIMARY KEY AUTOINCREMENT,
  note_id      INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id      INTEGER NOT NULL,
  file_name    TEXT NOT NULL,
  original_path TEXT NOT NULL,
  thumbnail_path TEXT,
  width        INTEGER, height INTEGER,
  sort_index   INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_note_files_note ON note_files(note_id, sort_index);
```

### `note_questions`（题目）
```sql
CREATE TABLE note_questions (
  question_id   INTEGER PRIMARY KEY AUTOINCREMENT,
  note_id       INTEGER NOT NULL REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id       INTEGER NOT NULL,
  question_type TEXT NOT NULL,  -- 'choice' / 'fill' / 'short_answer'
  stem          TEXT NOT NULL,
  options_json  TEXT,           -- 选择题 [{A,B,C,D}]
  answer        TEXT NOT NULL,
  explanation   TEXT NOT NULL,
  difficulty    TEXT,           -- 'easy' / 'medium' / 'hard'
  sort_index    INTEGER NOT NULL DEFAULT 0,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_note_questions_note ON note_questions(note_id, sort_index);
```

### `note_embeddings`
```sql
CREATE TABLE note_embeddings (
  note_id     INTEGER PRIMARY KEY REFERENCES notes(note_id) ON DELETE CASCADE,
  user_id     INTEGER NOT NULL,
  vector_json TEXT NOT NULL,    -- JSON 数组 [v0, v1, ...]
  dim         INTEGER NOT NULL,
  model       TEXT NOT NULL,    -- 'text-embedding-v3' / 'mock'
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### `ai_tasks` 改造
```sql
ALTER TABLE ai_tasks ADD COLUMN kind TEXT NOT NULL DEFAULT 'photo';  -- 'photo' | 'note'
ALTER TABLE ai_tasks ADD COLUMN note_id INTEGER REFERENCES notes(note_id) ON DELETE CASCADE;
ALTER TABLE ai_tasks ADD COLUMN sub_kind TEXT;  -- 'ocr' | 'summary' | 'questions' | 'polish' | 'translate' | 'embed'
```

`note_id` 与 `photo_id` 二选一必填其一，worker claim 时按 `kind` 分流到不同处理函数。

## 5. API 设计

所有路由需 `Authorization: Bearer <token>`；请求 / 响应壳沿用现有 `ok(code, message, data)`。

### 5.1 文件夹（`/api/v1/folders`）

| Method | Path                  | Body / Query               | 响应                                |
| ------ | --------------------- | -------------------------- | ----------------------------------- |
| GET    | `/folders`            | —                          | `{list: [{folderId, name, color, sortIndex, noteCount}]}` |
| POST   | `/folders`            | `{name, color?}`           | `{folderId}`                        |
| PATCH  | `/folders/{id}`       | `{name?, color?, sortIndex?}` | `{ok}`                            |
| DELETE | `/folders/{id}`       | —                          | `{ok}`，note.folder_id → NULL       |
| POST   | `/folders/reorder`    | `{orderedIds: [...]}`      | `{ok}`                              |

### 5.2 笔记（`/api/v1/notes`）

| Method | Path                       | Body / Query                                     | 响应                                                |
| ------ | -------------------------- | ------------------------------------------------ | --------------------------------------------------- |
| GET    | `/notes`                   | `folderId?, page=1, pageSize=20, archived?`      | `{list: [{noteId, title, summary, aiStatus, folderId, thumbCount, updatedAt}], total, page, pageSize}` |
| GET    | `/notes/{id}`              | —                                                | `{noteId, folderId, title, textContent, summary, aiStatus, files: [{fileId, url, thumbUrl, width, height}], questions: [...]}` |
| POST   | `/notes`                   | `{folderId?, title?, textContent?}`（纯文字笔记）| `{noteId}`                                          |
| PATCH  | `/notes/{id}`              | `{title?, textContent?, folderId?, isArchived?}` | `{ok}`                                              |
| DELETE | `/notes/{id}`              | —                                                | `{ok}`（软删）                                       |
| POST   | `/notes/{id}/export`       | `{format: 'md'\|'zip'}`                          | `{downloadUrl}`（zip = md + 原图）                  |

### 5.3 笔记图片（`/api/v1/note-files`）

| Method | Path                       | Body / Query               | 响应                                |
| ------ | -------------------------- | -------------------------- | ----------------------------------- |
| POST   | `/note-files/upload`       | multipart `files[]` + `noteId?` | `{files: [{fileId, url, thumbUrl, width, height}], noteId}` |
|                        | 若 `noteId` 缺则新建草稿笔记（folderId 默认"未分类"） |                                          |
| DELETE | `/note-files/{id}`         | —                          | `{ok}`                              |

### 5.4 AI（`/api/v1/note-ai`）

| Method | Path                       | Body / Query                                     | 响应                                                |
| ------ | -------------------------- | ------------------------------------------------ | --------------------------------------------------- |
| POST   | `/note-ai/ocr`             | `{noteId}`                                       | `{queuedCount, jobIds}`，触发 note_files → OCR      |
| POST   | `/note-ai/summary`         | `{noteId}`                                       | `{queuedCount}`（OCR 完成后自动串行触发）            |
| POST   | `/note-ai/questions`       | `{noteId, count?=5, types?=['choice','fill']}`  | `{questions: [...]}`（同步返回，落 `note_questions`，**服务端 60s 超时**，客户端 30s 超时 + 降级 toast）|
| POST   | `/note-ai/polish`          | `{noteId, action: 'polish'\|'expand'\|'shorten', text}` | `{result}`（同步，**不写回**）                  |
| POST   | `/note-ai/translate`       | `{noteId, targetLang: 'en'\|'zh'}`               | `{result}`（同步）                                  |
| GET    | `/note-ai/status`          | —                                                | `{total, pending, processing, done, failed, progress}` |
| GET    | `/note-ai/queue`           | —                                                | `{pending, processing, failed, done: [...]}`（同 photo queue 形态）|
| POST   | `/note-ai/retry`           | `{jobIds: [...]}`                                | `{queuedCount}`                                     |

### 5.5 检索（`/api/v1/note-search`）

| Method | Path                       | Body / Query                                     | 响应                                                |
| ------ | -------------------------- | ------------------------------------------------ | --------------------------------------------------- |
| POST   | `/note-search`             | `{query, mode: 'semantic'\|'keyword'\|'auto', topK?=20, folderId?}` | `{list: [{noteId, score, snippet, matchedSnippet}], engine}` |
|                                  | `engine` ∈ `'semantic' \| 'keyword' \| 'fallback'`                  |

## 6. AI Provider 抽象

`app/services/llm/provider.py`：

```python
class LLMProvider(ABC):
    name: str

    async def analyze_image(self, image_url: str, prompt: str) -> dict: ...
    async def summarize(self, text: str, max_words: int = 120) -> str: ...
    async def extract_key_points(self, text: str, max_points: int = 5) -> list[str]: ...
    async def generate_questions(
        self, text: str, count: int = 5, types: list[str] = None,
    ) -> list[dict]: ...
    async def polish(self, text: str, action: str) -> str: ...   # polish/expand/shorten
    async def translate(self, text: str, target_lang: str) -> str: ...
    async def embed(self, text: str) -> list[float]: ...
```

**`DashScopeProvider`**：默认实现，用 `dashscope` SDK + 读 `DASHSCOPE_API_KEY` 环境变量；调用失败抛 `LLMError`。

**`MockProvider`**：返回固定但合理的假数据，用于无 key 演示：

| 方法            | mock 输出                                                                 |
| --------------- | ------------------------------------------------------------------------- |
| `analyze_image` | `{ocr_text: "（mock）这是测试文本\n第二段...\n第三段...", confidence: 0.9}` |
| `summarize`     | `text` 前 80 字 + "（mock摘要，请配置 DASHSCOPE_API_KEY 启用真实 AI）"     |
| `key_points`    | 每段首句截前 30 字，凑 N 条                                              |
| `generate_questions` | 按 `text` 行数生成 N 道选择题，选项 A/B/C/D 抽 4 行文本   |
| `polish/expand/shorten` | 返回原文 + `[${action}]` 前缀                                  |
| `translate`     | 英文：原文 + `\n\n[Translated to English (mock)]`；中文同理                |
| `embed`         | 按 note_id 的 hash 产生 768 维伪向量（让 cos 检索能区分）               |

工厂：`get_provider() -> LLMProvider` 根据 `LLM_PROVIDER` env（`dashscope` / `mock`）和 `DASHSCOPE_API_KEY` 是否存在自动选择；启动期 fail-fast 校验。

**embedding 批处理**：长文本（>2000 字符）按 800 字符切块，每块调一次 `embed()`，最后平均池化；写入 `note_embeddings.vector_json` 存最终向量。检索时同样将 `query` embed 后比对。

## 7. AI Worker 改造

`app/models/ai_task.py` 的 `AIWorker._process_one` 改为按 `task.kind` 分发：

```python
if task.kind == 'photo':  await _process_photo(task)
elif task.kind == 'note': await _process_note(task)
```

`_process_note(task)`：

- `sub_kind='ocr'`：调 `provider.analyze_image(note_files.first.original_path, OCR_PROMPT)`，写回 `notes.text_content`
- `sub_kind='summary'`：OCR done 后自动 enqueue；调 `provider.summarize + extract_key_points`，写回 `notes.summary`，触发 embed enqueue
- `sub_kind='embed'`：调 `provider.embed(summary+text[:2000])`，写 `note_embeddings`
- `sub_kind='questions'`：调 `provider.generate_questions`，落 `note_questions`

**OCR→summary→embed 自动串联**：OCR 完成时由 `_on_success` 一次性 enqueue summary + embed 两个 job；summary 完成时再 enqueue embed（保证最新摘要进索引）。

## 8. 同步策略（Android Room）

| 场景         | 策略                                                          |
| ------------ | ------------------------------------------------------------- |
| App 启动     | 拉 `/folders` + `/notes?folderId=null&pageSize=50` 写 Room    |
| 用户写笔记   | 先本地写 Room（`dirty=1`），调服务端，成功后 `dirty=0`        |
| 上传图片     | 走服务端，建 note 后 pull → 写 Room                           |
| AI 状态      | 3 秒轮询 `/note-ai/queue`，更新 Room 中 `aiStatus`           |
| 离线         | 仅读 Room 缓存；UI 显示"离线模式"角标                          |

MVP **不做**离线写入 + WorkManager 后台同步（scope 之外）。

## 9. 错误处理

| 故障                       | 行为                                                                |
| -------------------------- | ------------------------------------------------------------------- |
| LLM 调用超时               | 服务端 30s 超时 → `LLMError` → worker 重试 3 次 → `ai_status=failed` |
| LLM JSON 解析失败          | fallback 到 `MockProvider` 同一调用，返回结构化降级结果 + log warning |
| embedding 服务挂           | 自动降级到 LIKE 检索，响应 `engine=fallback`                        |
| 上传文件超 20MB            | HTTP 413，客户端 toast                                             |
| 上传非图片（pdf 等）       | 拒绝 415                                                           |
| 文件夹重名                 | 422 + 错误码 `FOLDER_NAME_DUPLICATE`                                |
| 跨用户访问 note/folder     | 404，不暴露存在性                                                  |
| 删除有笔记的文件夹         | folder.folder_id → NULL（笔记保留），不级联删除                     |

## 10. 测试与验收

### 单元 / 集成（后端）

| 范围                       | 测试                                                              |
| -------------------------- | ----------------------------------------------------------------- |
| `note_service` CRUD        | pytest，SQLite 内存库，跨用户隔离                                |
| `folder_service` CRUD      | 同上，重名、删除后 note 处理                                      |
| `note_files.upload`        | multipart，HEIC 嗅探，缩略图                                       |
| `note_ai.*` 路由           | MockProvider 注入；同步（polish/translate/questions）+ 异步（ocr/summary/embed） |
| `note_search`              | 语义命中排序、LIKE 降级、`engine` 字段                              |
| `AIWorker` note 分支       | OCR→summary→embed 串联；失败重试；orphan 恢复                     |
| `LLMProvider` 抽象         | MockProvider 单测覆盖 6 个方法                                    |
| **跨用户**                 | alice 的 note id 拿不到 bob                                       |

### Android

| 范围                  | 测试                                                              |
| --------------------- | ----------------------------------------------------------------- |
| Room DAO              | androidx.room-testing in-memory                                   |
| Repository 同步       | 注入 mock ApiService，验证 Room 写入                              |
| NoteDetailFragment    | 1 个 Espresso：加载 mock 数据，工具栏点击不闪退                   |

### 验收（end-to-end，部署后真机）

- 上传 3 张测试图（试卷 + 手写笔记 + 书本），30s 内全部 OCR + 摘要完
- 5 条不同 query（"高数极限"、"英语错题"、"上周"）→ 至少 3 条命中正确笔记
- 1 条 polish、1 条 translate 同步返回合理结果
- 1 张试卷 → 出 5 题 → 在题库列表里显示
- 导出 1 篇笔记为 md → 文件能下载，markdown 渲染正确
- 切换到"相册模式"→ 旧 photo 功能正常

## 11. 实施顺序（high level）

1. **后端基础设施**：`llm/` provider 抽象 + Mock + DashScope；新建 5 张表的 migration
2. **后端 CRUD**：`folders` / `notes` / `note_files` 路由 + service + 单测
3. **后端 AI 链路**：`note_ai` 路由 + AIWorker `kind='note'` 分支 + MockProvider 跑通
4. **后端语义检索**：`note_embedding` + `note_search` + 降级
5. **Android Room**：5 个 entity + DAO + Repository
6. **Android Fragment 改造**：NotesFragment / NoteDetailFragment / CaptureFragment
7. **Android AI 工具栏**：润色/翻译/扩写/精简/出题按钮 + 短轮询
8. **Android 模式切换**：Profile 加 toggle，后端路由按 `?mode=` 或路径切分
9. **测试 + 验收**

## 12. 未决项（留给后续 plan 阶段）

- OCR prompt 模板的具体措辞（不同模型对 prompt 敏感）
- 出题 prompt 的难度控制策略（是否让 LLM 难度自评）
- 笔记导出 zip 的命名与目录结构（`note_<id>/note.md + images/...`）
- LLM 调用限流与并发（worker 是否需要按 note 串行）
- 大文件上传：分片 / 断点续传（scope 之外）