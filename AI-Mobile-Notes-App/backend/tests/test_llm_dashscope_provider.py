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


def test_instantiation_with_fake_key_does_not_call_api():
    """Verify provider can be constructed and async wrappers exist (no network)."""
    p = DashScopeProvider(api_key="sk-fake-for-test-only", timeout=5)
    assert p.name == "dashscope"
    assert hasattr(p, "_call_with_timeout")
    assert asyncio.iscoroutinefunction(p.analyze_image)
    assert asyncio.iscoroutinefunction(p.summarize)
    # Verify the async machinery itself runs (timeout on a 5s sleep = no error)
    asyncio.run(p._call_with_timeout(asyncio.sleep(0.001)))


def test_cleanup_image_non_200_raises_auth(monkeypatch):
    """non-200 from DashScope image-edit endpoint → LLMAuthError."""
    import httpx
    from app.services.llm.errors import LLMAuthError

    p = DashScopeProvider(api_key="sk-fake", timeout=2)

    class _FakeResp:
        status_code = 500
        text = "boom"

        def json(self): return {}

    class _FakeClient:
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *a, **kw): return _FakeResp()
        async def get(self, *a, **kw): return _FakeResp()

    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **kw: _FakeClient())

    with pytest.raises(LLMAuthError):
        asyncio.run(p.cleanup_image("data:image/png;base64,AAA"))


def test_cleanup_image_data_uri_response(monkeypatch):
    """When the response already contains a data: URI, return decoded bytes."""
    import httpx
    import base64

    PNG_BYTES = b"\x89PNG\r\n\x1a\n" + b"\x00" * 10
    B64 = base64.b64encode(PNG_BYTES).decode("ascii")
    DATA_URI = f"data:image/png;base64,{B64}"

    p = DashScopeProvider(api_key="sk-fake", timeout=2)

    class _FakeResp:
        status_code = 200

        def json(self):
            return {"output": {"results": [{"url": DATA_URI}]}}

    class _FakeClient:
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *a, **kw): return _FakeResp()
        async def get(self, *a, **kw): raise AssertionError("should not GET when data URI")

    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **kw: _FakeClient())

    out = asyncio.run(p.cleanup_image("data:image/png;base64,AAA"))
    assert out == PNG_BYTES


def test_cleanup_image_timeout(monkeypatch):
    """Slow response (>timeout) → LLMTimeoutError."""
    import httpx
    from app.services.llm.errors import LLMTimeoutError

    p = DashScopeProvider(api_key="sk-fake", timeout=0.1)

    class _SlowClient:
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *a, **kw):
            await asyncio.sleep(1)
            raise AssertionError("should have timed out")

    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **kw: _SlowClient())

    with pytest.raises(LLMTimeoutError):
        asyncio.run(p.cleanup_image("data:image/png;base64,AAA"))