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