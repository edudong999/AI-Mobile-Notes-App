from functools import lru_cache
from .provider import LLMProvider
from .errors import LLMError, LLMTimeoutError, LLMParseError, LLMAuthError

@lru_cache
def get_provider() -> LLMProvider:
    """根据 settings 自动选择实现，单例。
    启动期调用一次；后续 AIWorker 与同步路由都用此工厂。

    每次调用都重新实例化 Settings() 以反映最新环境变量（测试与
    运行时动态切换都需要），但 Provider 实例本身被 lru_cache 缓存。
    """
    from app.config import Settings
    settings = Settings()
    if settings.llm_provider == "dashscope" and settings.dashscope_api_key:
        from .dashscope_provider import DashScopeProvider
        return DashScopeProvider(
            api_key=settings.dashscope_api_key,
            ocr_model=settings.dashscope_ocr_model,
            llm_model=settings.dashscope_llm_model,
            embed_model=settings.dashscope_embed_model,
            timeout=settings.llm_timeout_sec,
        )
    from .mock_provider import MockProvider
    return MockProvider()

__all__ = ["LLMProvider", "LLMError", "LLMTimeoutError", "LLMParseError",
           "LLMAuthError", "get_provider"]