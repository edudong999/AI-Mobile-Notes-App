# AI 随身图文笔记助手 — 接口文档

> 项目: `AI-Mobile-Notes-App` (前身 `AI-Smart-Photo-Album`,已停维护)
> Base URL: `http://<host>:8000`
> 协议: HTTP + JSON(部分接口 `multipart/form-data`)
> 鉴权: 除 `register` / `login` / `health` 外,所有接口需 Header `Authorization: Bearer <token>`
> 字符编码: UTF-8
> 文档版本: v1.0  |  更新日期: 2026-08-27
> 后端源码: `backend/app/`,启动入口 `app.main:app`(uvicorn)

---

## 0. 通用约定

### 0.1 统一响应格式

所有接口都通过 `app.response.ok()` 包成下面这个壳:

```json
{
  "code": 200,
  "message": "ok",
  "data": { /* 业务数据,无业务数据时为 null */ }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码;HTTP 状态码与之保持一致 |
| message | string | 提示信息;前端可直接 toast |
| data | object / null | 业务负载;失败时为 `null` |

### 0.2 错误码

业务异常通过 `BizException(code, message)` 抛出,由 `main.py` 的 `biz_exception_handler` 转 JSONResponse。

| code | 含义 | 典型场景 |
|------|------|----------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | 表单校验失败、用户名/邮箱重复、分类名重复 |
| 401 | 未登录 / Token 失效 | 缺 Header、Token 无效或过期 |
| 403 | 无权限 | 用户被禁用 |
| 404 | 资源不存在 | 笔记/分类/文件不属于当前用户 |
| 500 | 服务器内部错误 | 未捕获异常 |
| 504 | LLM 调用超时 | 同步 AI 接口超过 30s/60s |

### 0.3 鉴权

- 注册 / 登录成功后由后端签发 JWT(默认有效期 30 天,见 `JWT_EXPIRE_SECONDS`)。
- 客户端需把 `token` 写入 `Authorization: Bearer <token>` 请求头;后端 `app.deps.get_current_user` 校验。
- `logout` 服务端不存状态,客户端丢弃 token 即可。

### 0.4 静态文件

启动时挂载以下目录到 `/static/*`:

| URL 前缀 | 物理目录 | 内容 |
|----------|----------|------|
| `/static/note_files/` | `data/note_files/` | 笔记原图 |
| `/static/note_thumbs/` | `data/note_thumbs/` | 笔记缩略图(WebP) |
| `/static/exports/` | `data/exports/` | 导出文件(md/zip) |

### 0.5 接口清单(28 个)

| 模块 | 方法 | 路径 |
|------|------|------|
| **Health** | GET | `/health` |
| **Auth** | POST | `/api/v1/auth/register` |
| | POST | `/api/v1/auth/login` |
| | POST | `/api/v1/auth/logout` |
| **Categories** | GET | `/api/v1/categories` |
| | POST | `/api/v1/categories` |
| | PATCH | `/api/v1/categories/{category_id}` |
| | DELETE | `/api/v1/categories/{category_id}` |
| | POST | `/api/v1/categories/reorder` |
| | GET | `/api/v1/categories/{category_id}/notes` |
| **Notes** | GET | `/api/v1/notes` |
| | GET | `/api/v1/notes/{note_id}` |
| | POST | `/api/v1/notes` |
| | PATCH | `/api/v1/notes/{note_id}` |
| | POST | `/api/v1/notes/{note_id}/categories` |
| | DELETE | `/api/v1/notes/{note_id}` |
| | POST | `/api/v1/notes/{note_id}/export` |
| **Note AI** | POST | `/api/v1/note-ai/ocr` |
| | POST | `/api/v1/note-ai/summary` |
| | POST | `/api/v1/note-ai/questions` |
| | POST | `/api/v1/note-ai/polish` |
| | POST | `/api/v1/note-ai/translate` |
| | POST | `/api/v1/note-ai/mindmap` |
| | POST | `/api/v1/note-ai/retry` |
| **Note Files** | POST | `/api/v1/note-files/upload` |
| | DELETE | `/api/v1/note-files/{file_id}` |
| **Image Cleanup** | POST | `/api/v1/note-image-cleanup` |
| **Search** | POST | `/api/v1/note-search` |

---

## 1. Health

### 1.1 健康检查

```
GET /health
```

无需鉴权。

**Response:**
```json
{ "status": "ok" }
```

---

## 2. Auth — `/api/v1/auth`

> 源码: `backend/app/routers/auth.py`
> Schemas: `backend/app/schemas/auth.py`

### 2.1 注册

```
POST /api/v1/auth/register
```

**Request:**
```json
{
  "username": "alice",
  "password": "alice123",
  "email": "alice@example.com"
}
```

| 字段 | 必填 | 约束 |
|------|------|------|
| username | 是 | 3–50 字符,唯一 |
| password | 是 | 6–128 字符 |
| email | 否 | 合法邮箱,唯一 |

**Response 200:**
```json
{
  "code": 200,
  "message": "注册成功",
  "data": { "userId": 1, "username": "alice" }
}
```

**错误:**
- `400 用户名已被占用`
- `400 邮箱已被占用`

**curl:**
```bash
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123","email":"alice@example.com"}'
```

---

### 2.2 登录

```
POST /api/v1/auth/login
```

**Request:**
```json
{ "username": "alice", "password": "alice123" }
```

**Response 200:**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "alice",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 2592000
  }
}
```

**错误:**
- `400 用户名或密码错误`
- `403 用户已被禁用`

**curl:**
```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}'
```

---

### 2.3 登出

```
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

服务端无状态,只返回成功提示,客户端丢弃 token 即可。

**Response 200:**
```json
{ "code": 200, "message": "登出成功", "data": null }
```

---

## 3. Categories — `/api/v1/categories`

> 源码: `backend/app/routers/categories.py`
> Schemas: `backend/app/schemas/category.py`
> 说明: 用户的笔记分类(M:N 关联,见 `note_categories` 表);旧的 `notebook_folders` 已迁移到 `categories`。

### 3.1 列出分类

```
GET /api/v1/categories
```

返回当前用户的所有分类,含每类下的笔记数。

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "list": [
      { "categoryId": 1, "name": "数学", "color": "#4A90E2", "sortIndex": 0, "noteCount": 12 },
      { "categoryId": 2, "name": "英语", "color": "#E27A4A", "sortIndex": 1, "noteCount": 7 }
    ]
  }
}
```

---

### 3.2 新建分类

```
POST /api/v1/categories
```

**Request:**
```json
{ "name": "物理", "color": "#22C55E" }
```

| 字段 | 必填 | 说明 |
|------|------|------|
| name | 是 | 同一用户下唯一 |
| color | 否 | 16 进制色值,默认 `#4A90E2` |

**Response 200:**
```json
{ "code": 200, "message": "ok", "data": { "categoryId": 3 } }
```

**错误:** `400 分类名已存在`

---

### 3.3 修改分类

```
PATCH /api/v1/categories/{category_id}
```

**Request(任选字段):**
```json
{ "name": "高数", "color": "#22C55E", "sortIndex": 2 }
```

---

### 3.4 删除分类

```
DELETE /api/v1/categories/{category_id}
```

关联的 `note_categories` 行会级联删除,不影响笔记本身。

---

### 3.5 排序

```
POST /api/v1/categories/reorder
```

**Request:**
```json
{ "orderedIds": [3, 1, 2] }
```

按传入顺序更新每条分类的 `sort_index`。

---

### 3.6 分类下的笔记

```
GET /api/v1/categories/{category_id}/notes
```

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "list": [
      {
        "noteId": 42,
        "title": "极限定义",
        "summary": "...",
        "aiStatus": "done",
        "categories": [1],
        "thumbCount": 2,
        "thumbUrl": "/static/note_thumbs/xxx.webp",
        "updatedAt": "2026-08-19T03:14:15Z"
      }
    ]
  }
}
```

字段形态与 `GET /api/v1/notes` 列表项一致(见 4.1)。

---

## 4. Notes — `/api/v1/notes`

> 源码: `backend/app/routers/notes.py`
> Schemas: `backend/app/schemas/note.py`

### 4.1 分页列表

```
GET /api/v1/notes?page=1&pageSize=20&archived=false&categoryId=1
```

| Query | 类型 | 必填 | 说明 |
|-------|------|------|------|
| page | int | 否 | 默认 1,≥ 1 |
| pageSize | int | 否 | 默认 20,1–100 |
| archived | bool | 否 | true 仅已归档,false 仅未归档,缺省不过滤 |
| categoryId | int | 否 | 按分类过滤 |

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "list": [
      {
        "noteId": 42,
        "title": "极限定义",
        "summary": "...",
        "aiStatus": "done",
        "categories": [1],
        "thumbCount": 2,
        "thumbUrl": "/static/note_thumbs/xxx.webp",
        "updatedAt": "2026-08-19T03:14:15Z"
      }
    ],
    "total": 38,
    "page": 1,
    "pageSize": 20
  }
}
```

`aiStatus` 取值:`pending` | `processing` | `done` | `failed`。

---

### 4.2 笔记详情

```
GET /api/v1/notes/{note_id}
```

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "noteId": 42,
    "categories": [1],
    "title": "极限定义",
    "textContent": "...",
    "summary": "...",
    "aiStatus": "done",
    "ocrEngine": "qwen-vl-plus",
    "files": [
      {
        "fileId": 100,
        "url": "/static/note_files/42_xxx.webp",
        "thumbUrl": "/static/note_thumbs/42_xxx.webp",
        "width": 1920,
        "height": 1080,
        "sortIndex": 0,
        "kind": "original",
        "parentFileId": null
      }
    ],
    "questions": [
      {
        "questionId": 7,
        "questionType": "choice",
        "stem": "下列关于极限的描述正确的是?",
        "options": ["A...", "B...", "C...", "D..."],
        "answer": "A",
        "explanation": "...",
        "difficulty": "easy"
      }
    ],
    "isArchived": false,
    "createdAt": "2026-08-19T03:14:15Z",
    "updatedAt": "2026-08-19T03:14:15Z"
  }
}
```

`kind` 取值:`original`(用户上传) | `cleaned`(AI 清理后的产物)。
`parentFileId`:仅 `cleaned` 类型的文件有值,指向原始文件。

---

### 4.3 创建笔记

```
POST /api/v1/notes
```

**Request:**
```json
{ "title": "新笔记", "textContent": "正文内容..." }
```

**Response 200:**
```json
{ "code": 200, "message": "ok", "data": { "noteId": 42 } }
```

---

### 4.4 增量更新

```
PATCH /api/v1/notes/{note_id}
```

**Request(任选字段):**
```json
{
  "title": "改后标题",
  "textContent": "改后正文",
  "isArchived": false,
  "categories": [1, 2]
}
```

| 字段 | 语义 |
|------|------|
| title / textContent / isArchived | 缺省不修改 |
| categories | 整体替换;`null` 不修改,`[]` 清空,非空校验后替换 |

---

### 4.5 设置分类

```
POST /api/v1/notes/{note_id}/categories
```

**Request:**
```json
{ "categoryIds": [1, 2] }
```

**Response 200:**
```json
{ "code": 200, "message": "ok", "data": { "categoryIds": [1, 2] } }
```

---

### 4.6 删除笔记

```
DELETE /api/v1/notes/{note_id}
```

软删除(`notes` 表里实际未删除,后续可恢复;但当前接口未暴露 restore)。

---

### 4.7 导出

```
POST /api/v1/notes/{note_id}/export
```

**Request:**
```json
{ "format": "md" }   // "md" | "zip"
```

**Response 200:**
```json
{ "code": 200, "message": "ok", "data": { "downloadUrl": "/static/exports/note_42.md" } }
```

`zip` 含图片 + Markdown;`md` 仅文本。

---

## 5. Note AI — `/api/v1/note-ai`

> 源码: `backend/app/routers/note_ai.py`
> Schemas: `backend/app/schemas/note_ai.py`
> 异步模式: `/ocr`、`/summary` 入 `ai_tasks` 队列,worker 后台执行;客户端通过轮询 `GET /api/v1/notes/{id}` 看 `aiStatus` 是否进入 `done` / `failed`。
> 同步模式: `/questions`、`/polish`、`/translate`、`/mindmap` 直接调用 LLM,带超时控制。
> `/retry` 把失败任务重置回 `queued` 并唤醒 worker。

### 5.1 OCR(异步)

```
POST /api/v1/note-ai/ocr
```

**Request:**
```json
{ "noteId": 42 }
```

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": { "queuedCount": 1, "jobIds": [101], "message": "已入队" }
}
```

OCR 完成会自动串联入队 `summary` + `embed`。

---

### 5.2 摘要(异步)

```
POST /api/v1/note-ai/summary
```

请求体同 5.1。

---

### 5.3 生成题目(同步,60s 超时)

```
POST /api/v1/note-ai/questions
```

**Request:**
```json
{
  "noteId": 42,
  "count": 5,
  "types": ["choice", "truefalse", "short"]
}
```

| 字段 | 默认 | 说明 |
|------|------|------|
| count | 5 | 生成数量 |
| types | null(全部) | 限定题型:`choice` / `truefalse` / `short` |

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "questions": [
      {
        "questionType": "choice",
        "stem": "...",
        "options": ["A", "B", "C", "D"],
        "answer": "A",
        "explanation": "...",
        "difficulty": "easy"
      }
    ]
  }
}
```

**错误:** `504 LLM 生成题目超时`

---

### 5.4 润色(同步,30s 超时)

```
POST /api/v1/note-ai/polish
```

**Request:**
```json
{ "noteId": 42, "action": "polish", "text": "原始文本..." }
```

`action` 取值:`polish`(润色)| `expand`(扩写)| `shorten`(精简)。

**Response 200:**
```json
{ "code": 200, "message": "ok", "data": { "result": "润色后文本..." } }
```

---

### 5.5 翻译(同步,30s 超时)

```
POST /api/v1/note-ai/translate
```

**Request:**
```json
{ "noteId": 42, "targetLang": "en", "text": "可选,不传则翻译整篇" }
```

`targetLang` 取值:`en` | `zh`。

---

### 5.6 思维导图(同步,60s 超时)

```
POST /api/v1/note-ai/mindmap
```

**Request:**
```json
{ "noteId": 42, "maxDepth": 3 }
```

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "noteId": 42,
    "tree": {
      "label": "极限",
      "children": [
        { "label": "定义", "children": [{ "label": "ε-δ", "children": [] }] },
        { "label": "运算", "children": [] }
      ]
    }
  }
}
```

落库到 `notes.mindmap_json`。

---

### 5.7 重试失败任务

```
POST /api/v1/note-ai/retry
```

**Request:**
```json
{ "jobIds": [101, 102] }
```

把指定 `jobIds` 的 `ai_tasks` 行重置为 `queued`,清空 `retry_count` / `error_message`,唤醒 worker。

---

## 6. Note Files — `/api/v1/note-files`

> 源码: `backend/app/routers/note_files.py`
> Schemas: `backend/app/schemas/note_file.py`
> 上传后会自动入队 `ocr` 任务。

### 6.1 上传

```
POST /api/v1/note-files/upload
Content-Type: multipart/form-data
```

| Form 字段 | 必填 | 说明 |
|-----------|------|------|
| noteId | 否 | 已有笔记 id;空则自动创建新笔记 |
| files | 是 | 一个或多个文件(`files[]`),支持 HEIC/HEIF(自动归一) |

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "files": [
      {
        "fileId": 100,
        "url": "/static/note_files/42_xxx.jpg",
        "thumbUrl": "/static/note_thumbs/42_xxx.webp",
        "width": 4032,
        "height": 3024,
        "sortIndex": 0
      }
    ],
    "noteId": 42
  }
}
```

**curl:**
```bash
curl -X POST http://localhost:8000/api/v1/note-files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "noteId=42" \
  -F "files=@./photo1.jpg" \
  -F "files=@./photo2.jpg"
```

---

### 6.2 删除文件

```
DELETE /api/v1/note-files/{file_id}
```

非本用户或不存在时幂等返回 ok。

---

## 7. Note Image Cleanup — `/api/v1/note-image-cleanup`

> 源码: `backend/app/routers/note_image_cleanup.py`
> Schemas: `backend/app/schemas/note_image_cleanup.py`
> 调 `qwen-image-edit` 做去涂鸦 / 锐化,产物作为新的 `NoteFile` 落库,`kind='cleaned'`、`parent_file_id` 指向原图。

### 7.1 清理图片

```
POST /api/v1/note-image-cleanup
```

**Request:**
```json
{
  "noteId": 42,
  "fileId": 100,
  "mode": "commit_insert"
}
```

| 字段 | 取值 |
|------|------|
| mode | `commit_insert`(插入到原图旁边)/ `commit_new`(另存为新图) |

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "cleanedFileId": 101,
    "cleanedUrl": "/static/note_files/42_cleaned_xxx.webp",
    "cleanedThumbUrl": "/static/note_thumbs/42_cleaned_xxx.webp",
    "kind": "cleaned",
    "parentFileId": 100
  }
}
```

**错误:** `404 图片不存在` / `504 图像清理超时,请重试`

---

## 8. Note Search — `/api/v1/note-search`

> 源码: `backend/app/routers/note_search.py`
> Schemas: `backend/app/schemas/note_search.py`
> 三种模式:`auto`(默认)/ `semantic` / `keyword`。`semantic` 失败时回退到 `keyword`,`auto` 模式下响应里 `mode` 会标 `fallback`。

### 8.1 搜索

```
POST /api/v1/note-search
```

**Request:**
```json
{
  "query": "上周英语错题",
  "mode": "auto",
  "topK": 20,
  "folderId": null
}
```

**Response 200:**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "hits": [
      {
        "noteId": 42,
        "title": "完形填空错题",
        "score": 0.87,
        "snippet": "...时态与语态...",
        "matchedSnippet": "...<em>时态</em>与<em>语态</em>..."
      }
    ],
    "mode": "semantic"
  }
}
```

`mode` 取值:`semantic` | `keyword` | `fallback`。

---

## 9. 数据库表

> ORM 源码: `backend/app/models/`
> 默认数据库: SQLite 单文件 `./data/ai_album.db`,可通过 `DATABASE_URL` 切到 PostgreSQL(`postgresql+asyncpg://...`)。
> 启动时自动跑 `migrations/004_note_tables.sql` 等若干迁移脚本。

### 9.1 表清单(8 张)

| # | 表名 | 说明 |
|---|------|------|
| 1 | `users` | 用户 |
| 2 | `categories` | 分类字典(从 `notebook_folders` 改名) |
| 3 | `notes` | 笔记主表 |
| 4 | `note_categories` | 笔记-分类关联(M:N) |
| 5 | `note_files` | 笔记关联文件(图) |
| 6 | `note_questions` | AI 生成的题目 |
| 7 | `note_embeddings` | 笔记向量(每条一行) |
| 8 | `ai_tasks` | AI 后台任务队列(笔记 OCR/摘要/嵌入) |

### 9.2 ER 简图

```
users (1) ─────< (N) notes (1) ────── (1) note_embeddings
   │                │
   │                ├──< (N) note_files
   │                │        └──< self-ref(parent_file_id, "cleaned")
   │                ├──< (N) note_questions
   │                └──< (N) ai_tasks
   │
   └──< (N) categories >─────< (N) notes
              (via note_categories)
```

| 关系 | 类型 | 说明 |
|------|------|------|
| users → notes | 1 : N | 一个用户拥有多条笔记 |
| users → categories | 1 : N | 每个用户独立分类 |
| notes ↔ categories | N : N | 通过 `note_categories` |
| notes → note_files | 1 : N | 笔记可关联多张图(含 cleaned 派生) |
| notes → note_questions | 1 : N | AI 生成的题目 |
| notes → note_embeddings | 1 : 1 | 每条笔记一条向量 |
| notes → ai_tasks | 1 : N | 一条笔记可多次入队 |

### 9.3 关键字段速览

**`users`** — `user_id` / `username`(唯一)/ `password_hash` / `email`(可选,唯一)/ `avatar_url` / `status`(1=正常)/ `last_login_at` / `created_at` / `updated_at`

**`categories`** — `category_id` / `user_id` / `name` / `color`(默认 `#4A90E2`)/ `sort_index`
唯一约束:`(user_id, name)`

**`notes`** — `note_id` / `user_id` / `title` / `text_content` / `summary` / `ai_status`(enum: `pending`/`processing`/`done`/`failed`)/ `ocr_engine` / `mindmap_json` / `is_archived` / `created_at` / `updated_at` / `deleted_at`(软删)

**`note_categories`** — `(note_id, category_id)` 联合主键

**`note_files`** — `file_id` / `note_id` / `user_id` / `file_name` / `original_path` / `thumbnail_path` / `width` / `height` / `sort_index` / `kind`(默认 `original`)/ `parent_file_id`(自引用,清理图用)

**`note_questions`** — `question_id` / `note_id` / `user_id` / `question_type` / `stem` / `options_json` / `answer` / `explanation` / `difficulty` / `sort_index`

**`note_embeddings`** — `note_id`(主键)/ `user_id` / `vector_json` / `dim` / `model`

**`ai_tasks`** — `task_id` / `note_id` / `kind`(枚举,当前固定 `note`)/ `sub_kind`(`ocr`/`summary`/`questions`/`polish`/`translate`/`embed`)/ `status`(`queued`/`processing`/`succeeded`/`failed`)/ `error_message` / `retry_count`(默认 0,上限 3)/ `next_retry_at` / `claimed_at` / `claimed_by` / `heartbeat_at` / `finished_at`

---

## 10. 配置项(`.env`)

| Key | 默认 | 说明 |
|-----|------|------|
| `DATABASE_URL` | `sqlite+aiosqlite:///./data/ai_album.db` | 数据库连接串 |
| `JWT_SECRET` | (必填) | JWT 签名密钥 |
| `JWT_EXPIRE_SECONDS` | `2592000` | Token 有效期(秒) |
| `LLM_PROVIDER` | `dashscope` | `mock` / `dashscope` |
| `DATA_DIR` | `./data` | 文件存储根目录 |
| `STATIC_URL_PREFIX` | `/static` | 静态文件 URL 前缀 |
| `MAX_UPLOAD_SIZE_MB` | `20` | 单文件上传上限(MB) |
| `LOG_LEVEL` | `INFO` | 日志级别 |
| `DASHSCOPE_API_KEY` | (mock 模式可空) | 阿里云百炼 API Key |
| `DASHSCOPE_WORKSPACE_ID` | 空 | 业务空间 ID |
| `DASHSCOPE_REGION` | `cn-beijing` | `cn-beijing` / `cn-shanghai` / `cn-shenzhen` / `cn-hangzhou` |
| `DASHSCOPE_BASE_URL` | 空 | 可选:完整 base_url |
| `DASHSCOPE_MODEL` | `qwen3-vl-plus` | 多模态模型名 |
| `DASHSCOPE_TIMEOUT` | `30` | LLM 单次调用超时(秒) |

---

## 11. 调试:OpenAPI / Swagger

启动后端后:

- OpenAPI JSON: `http://localhost:8000/openapi.json`
- Swagger UI:   `http://localhost:8000/docs`
- ReDoc:        `http://localhost:8000/redoc`

三者均由 FastAPI 自动从 router + Pydantic schema 生成,可作为本文件的权威补充。

---

## 12. 完整 curl 流程示例

```bash
# 1. 注册
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}'

# 2. 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"alice123"}' \
  | python -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")

AUTH="Authorization: Bearer $TOKEN"

# 3. 建分类
curl -X POST http://localhost:8000/api/v1/categories \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"数学","color":"#4A90E2"}'

# 4. 建笔记
NOTE_ID=$(curl -s -X POST http://localhost:8000/api/v1/notes \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"极限定义","textContent":""}' \
  | python -c "import json,sys; print(json.load(sys.stdin)['data']['noteId'])")

# 5. 上传图片(自动入队 OCR)
curl -X POST http://localhost:8000/api/v1/note-files/upload \
  -H "$AUTH" \
  -F "noteId=$NOTE_ID" \
  -F "files=@./lecture.jpg"

# 6. 触发摘要
curl -X POST http://localhost:8000/api/v1/note-ai/summary \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"noteId\":$NOTE_ID}"

# 7. 同步生成题目
curl -X POST http://localhost:8000/api/v1/note-ai/questions \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"noteId\":$NOTE_ID,\"count\":5,\"types\":[\"choice\",\"truefalse\"]}"

# 8. 同步润色
curl -X POST http://localhost:8000/api/v1/note-ai/polish \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"noteId\":$NOTE_ID,\"action\":\"polish\",\"text\":\"原文...\"}"

# 9. 同步翻译
curl -X POST http://localhost:8000/api/v1/note-ai/translate \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"noteId\":$NOTE_ID,\"targetLang\":\"en\"}"

# 10. 导出
curl -X POST http://localhost:8000/api/v1/notes/$NOTE_ID/export \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"format":"md"}'

# 11. 搜索
curl -X POST http://localhost:8000/api/v1/note-search \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"query":"极限","mode":"auto","topK":10}'
```