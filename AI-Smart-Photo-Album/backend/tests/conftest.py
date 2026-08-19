"""测试公用 fixtures。"""
import asyncio
import os
import shutil
import uuid
from pathlib import Path

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# 测试环境：覆盖 settings，再 import app
# 用文件型 SQLite 而非 :memory:，因为 :memory: 在多个 engine/connection 之间
# 各自独立（每个连接拿到的都是空库），文件型才能让 conftest 的 engine 与
# database.py 的全局 engine 看到同一份数据。
TEST_DB_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "sqlite+aiosqlite:///./tests/_data/album_test.db",
)
TEST_DATA_DIR = "./tests/_data"

# ⚠️ 必须在模块顶层设置 env，不能放到 fixture 里——否则 test 文件
# 顶层的 `from app.database import ...` 会先于 fixture 执行，拿到默认 DB，
# 导致 conftest 的 engine（test DB）与 app.database 的 engine（默认 DB）
# 指向不同文件，seed 与查询互相看不到。
os.environ["DATABASE_URL"] = TEST_DB_URL
os.environ["DATA_DIR"] = TEST_DATA_DIR
os.environ["JWT_SECRET"] = "test-secret"
# 强制走 mock provider：本地 .env 里写的是 dashscope + 真实 key，测试不能
# 触发真实网络调用，也不能让响应内容脱离预期。
os.environ["LLM_PROVIDER"] = "mock"
os.environ.pop("DASHSCOPE_API_KEY", None)
Path(TEST_DATA_DIR).mkdir(parents=True, exist_ok=True)


@pytest.fixture(scope="session", autouse=True)
def _setup_env():
    yield
    if Path(TEST_DATA_DIR).exists():
        shutil.rmtree(TEST_DATA_DIR, ignore_errors=True)


@pytest_asyncio.fixture(scope="session")
async def engine(_setup_env):
    eng = create_async_engine(TEST_DB_URL, echo=False)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture(autouse=True)
async def _prepare_db(engine):
    """每个测试前重置数据库（drop_all + create_all + seed）。

    SQLite + AUTORANDOM 会让 PK 在 DELETE 后继续递增，所以这里用 drop/create
    而不是 DELETE，确保 category_id 等关键字段始终是 1-60。
    """
    from app.database import Base, run_sql_file
    import app.models  # noqa: F401 触发模型注册
    from sqlalchemy import text

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    # categories 表由 SQL 迁移 001_schema.sql 创建（不在 SQLAlchemy 模型里），
    # 现代码库不再自动跑 001；如果表不存在直接跳过 seed，避免全测试套件被这条
    # 旧基础设施卡死。
    async with engine.connect() as conn:
        has_categories = (await conn.execute(
            text("SELECT name FROM sqlite_master WHERE type='table' AND name='categories'")
        )).first() is not None
        if has_categories:
            # 002_seed_categories.sql is the legacy photo-category seed; only
            # run when the table has the old `type` column. New note
            # `Category` uses `color` + `sort_index`.
            col_rows = (await conn.execute(
                text("PRAGMA table_info(categories)")
            )).all()
            col_names = {row[1] for row in col_rows}
            if "type" in col_names:
                await run_sql_file("migrations/002_seed_categories.sql")
    yield


@pytest_asyncio.fixture
async def db(engine):
    Session = async_sessionmaker(engine, expire_on_commit=False)
    async with Session() as s:
        yield s


@pytest_asyncio.fixture
async def client():
    from app.main import app
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest_asyncio.fixture
async def registered_user(client):
    """每次测试创建独立用户（用户名加 uuid 避免冲突）。"""
    suffix = uuid.uuid4().hex[:8]
    username = f"alice-{suffix}"
    email = f"alice-{suffix}@example.com"
    resp = await client.post("/api/v1/auth/register", json={
        "username": username,
        "password": "secret123",
        "email": email,
    })
    assert resp.status_code == 200, resp.text
    login = await client.post("/api/v1/auth/login", json={"username": username, "password": "secret123"})
    token = login.json()["data"]["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return {"token": token, "userId": login.json()["data"]["userId"], "username": username}