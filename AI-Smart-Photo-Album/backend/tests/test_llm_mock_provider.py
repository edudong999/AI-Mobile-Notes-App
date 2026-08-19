import asyncio, pytest
from app.services.llm.mock_provider import MockProvider

@pytest.fixture
def p():
    return MockProvider()

def test_name(p):
    assert p.name == "mock"

def test_analyze_image(p):
    r = asyncio.run(p.analyze_image("/x.png", "extract text"))
    assert "ocr_text" in r
    assert isinstance(r["confidence"], float)

def test_summarize(p):
    s = asyncio.run(p.summarize("这是第一段。\n这是第二段。"))
    assert "mock摘要" in s

def test_extract_key_points(p):
    pts = asyncio.run(p.extract_key_points("甲乙丙\n丁戊己\n庚辛", max_points=3))
    assert len(pts) == 3
    assert all(isinstance(x, str) for x in pts)

def test_generate_questions(p):
    qs = asyncio.run(p.generate_questions("line1\nline2\nline3\nline4\nline5", count=3, types=["choice"]))
    assert len(qs) == 3
    assert qs[0]["question_type"] == "choice"
    assert "options" in qs[0]

def test_generate_questions_from_images(p):
    qs = asyncio.run(p.generate_questions_from_images(
        ["data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB"],
        count=3, types=["choice"],
    ))
    assert len(qs) == 3
    assert all("stem" in q and "answer" in q for q in qs)
    assert all("图片" in q["stem"] or "mock" in q["explanation"] for q in qs)

def test_generate_questions_from_images_empty_falls_back(p):
    qs = asyncio.run(p.generate_questions_from_images(
        [], count=2, types=["choice"], fallback_text="alpha\nbeta",
    ))
    assert len(qs) == 2
    assert all("options" in q for q in qs)

def test_polish(p):
    r = asyncio.run(p.polish("hello", "polish"))
    assert r.startswith("[polish]")

def test_translate(p):
    r = asyncio.run(p.translate("你好", "en"))
    assert "[Translated to English" in r

def test_embed_deterministic_per_text(p):
    v1 = asyncio.run(p.embed("note-1"))
    v2 = asyncio.run(p.embed("note-1"))
    assert v1 == v2
    assert len(v1) == 768

def test_cleanup_image_returns_png(p):
    """Mock provider returns a tiny PNG regardless of input."""
    png = asyncio.run(p.cleanup_image("data:image/png;base64,AAA"))
    assert png[:8] == b"\x89PNG\r\n\x1a\n"  # PNG magic bytes
    assert len(png) > 0