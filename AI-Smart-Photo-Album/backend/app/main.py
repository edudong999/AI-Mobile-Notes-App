"""FastAPI 入口：lifespan 跑迁移 + 启 worker；注册异常处理、路由、静态文件。"""
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from app.config import settings
from app.database import engine, run_sql_file
from app.exceptions import BizException
from app.models.ai_task import start_worker, stop_worker
from app.routers import auth, categories, note_ai, note_files, note_image_cleanup, note_search, notes

logging.basicConfig(level=getattr(logging, settings.LOG_LEVEL, logging.INFO))
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：迁移、启停 worker。"""
    if Path("migrations/004_note_tables.sql").exists():
        await run_sql_file("migrations/004_note_tables.sql")
    if Path("migrations/005_mindmap.sql").exists():
        await run_sql_file("migrations/005_mindmap.sql")

    await start_worker()
    yield
    await stop_worker()
    await engine.dispose()


app = FastAPI(title="AI 智能相册后端", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(BizException)
async def biz_exception_handler(_: Request, exc: BizException):
    """业务异常处理：转 JSONResponse。"""
    return JSONResponse(status_code=exc.code, content={"code": exc.code, "message": exc.message, "data": None})


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, exc: RequestValidationError):
    """请求参数校验失败处理：返回 400 与聚合错误消息。"""
    msg = "; ".join(
        ".".join(str(p) for p in e["loc"][1:]) + ":" + e["msg"]
        for e in exc.errors()
    ) or "参数错误"
    return JSONResponse(status_code=400, content={"code": 400, "message": msg, "data": None})


@app.exception_handler(Exception)
async def fallback_handler(_: Request, exc: Exception):
    """兜底异常处理：记录日志并返回 500。"""
    log.exception("未捕获异常: %s", exc)
    return JSONResponse(status_code=500, content={"code": 500, "message": "服务器错误", "data": None})


Path(settings.DATA_DIR).mkdir(parents=True, exist_ok=True)
(Path(settings.DATA_DIR) / "note_files").mkdir(parents=True, exist_ok=True)
(Path(settings.DATA_DIR) / "note_thumbs").mkdir(parents=True, exist_ok=True)
(Path(settings.DATA_DIR) / "exports").mkdir(parents=True, exist_ok=True)
app.mount(settings.STATIC_URL_PREFIX, StaticFiles(directory=settings.DATA_DIR), name="static")
app.mount("/static/exports", StaticFiles(directory=Path(settings.DATA_DIR) / "exports"), name="exports")
app.mount("/static/note_files", StaticFiles(directory=Path(settings.DATA_DIR) / "note_files"), name="note_files")
app.mount("/static/note_thumbs", StaticFiles(directory=Path(settings.DATA_DIR) / "note_thumbs"), name="note_thumbs")

for r in (auth.router, categories.router, notes.router, note_search.router, note_files.router, note_ai.router, note_image_cleanup.router):
    app.include_router(r)


@app.get("/health")
async def health():
    """健康检查端点。"""
    return {"status": "ok"}
