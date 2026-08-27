"""pydantic-settings 配置，从 .env 读取并缓存。"""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """应用配置项。"""
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    DATABASE_URL: str = "sqlite+aiosqlite:///./data/ai_album.db"
    JWT_SECRET: str = "change-me-in-production"
    JWT_EXPIRE_SECONDS: int = 7200
    DATA_DIR: str = "./data"
    STATIC_URL_PREFIX: str = "/static"
    MAX_UPLOAD_SIZE_MB: int = 20
    LOG_LEVEL: str = "INFO"
    llm_provider: str = "mock"
    dashscope_api_key: str = ""
    dashscope_ocr_model: str = "qwen-vl-max"
    dashscope_llm_model: str = "qwen-plus"
    dashscope_embed_model: str = "text-embedding-v3"
    llm_timeout_sec: int = 30


@lru_cache
def get_settings() -> Settings:
    """返回缓存的 Settings 单例。"""
    return Settings()


settings = get_settings()
