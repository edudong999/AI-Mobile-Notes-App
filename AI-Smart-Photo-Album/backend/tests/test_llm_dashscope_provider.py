import asyncio, os
import pytest

# 测试运行前确保 DASHSCOPE_API_KEY 已设（CI 也一样）
# 注意：pytestmark 只跳过真正调用 DashScope SDK 的 test_name，
# factory 测试用 monkeypatch 注入 fake key，必须无条件执行。

from app.services.llm.dashscope_provider import DashScopeProvider

@pytest.mark.skipif(
    not os.environ.get("DASHSCOPE_API_KEY"),
    reason="DASHSCOPE_API_KEY not set; skipping live API tests",
)
def test_name():
    assert DashScopeProvider().name == "dashscope"


def test_factory_returns_mock_when_no_key(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "mock")
    from app.services.llm import get_provider
    get_provider.cache_clear()
    p = get_provider()
    assert p.name == "mock"
    monkeypatch.undo()
    get_provider.cache_clear()

def test_factory_returns_dashscope_when_configured(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "dashscope")
    monkeypatch.setenv("DASHSCOPE_API_KEY", "sk-fake-key-for-test-only")
    from app.services.llm import get_provider
    get_provider.cache_clear()
    p = get_provider()
    assert p.name == "dashscope"
    monkeypatch.undo()
    get_provider.cache_clear()