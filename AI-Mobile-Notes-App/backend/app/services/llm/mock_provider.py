import hashlib
import re
from .provider import LLMProvider

EMBED_DIM = 768

# 1×1 transparent PNG used by mock cleanup_image — keeps tests hermetic
# without pulling in Pillow just for this fixture.
_MOCK_PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
    "890000000a49444154789c6300010000000500010d0a2db40000000049454e44"
    "ae426082"
)

class MockProvider(LLMProvider):
    name = "mock"

    async def analyze_image(self, image_url: str, prompt: str) -> dict:
        return {
            "ocr_text": "（mock）这是 OCR 识别出的测试文本。\n第二段：演示多行输出。\n第三段：结束。",
            "confidence": 0.9,
            "key_points": ["测试文本", "多行输出", "结束"],
        }

    async def summarize(self, text: str, max_words: int = 120) -> str:
        snippet = text[:80].replace("\n", " ")
        return f"{snippet}（mock摘要，请配置 DASHSCOPE_API_KEY 启用真实 AI）"

    async def extract_key_points(self, text: str, max_points: int = 5) -> list[str]:
        lines = [l.strip() for l in text.splitlines() if l.strip()]
        return lines[:max_points] or ["（无内容）"]

    async def generate_questions(
        self, text: str, count: int = 5, types: list[str] | None = None,
    ) -> list[dict]:
        types = types or ["choice"]
        lines = [l.strip() for l in text.splitlines() if l.strip()][: max(count, 1)]
        out = []
        for i in range(count):
            t = types[i % len(types)]
            stem = f"关于\"{lines[min(i, len(lines)-1)][:30]}\"的正确说法是？"
            options = (lines + ["干扰项 A", "干扰项 B"])[:4]
            out.append({
                "question_type": t,
                "stem": stem,
                "options": options,
                "answer": options[0],
                "explanation": f"依据原文「{lines[0][:50] if lines else ''}」。",
                "difficulty": "easy",
            })
        return out

    async def generate_questions_from_images(
        self,
        images: list[str],
        count: int = 5,
        types: list[str] | None = None,
        fallback_text: str = "",
    ) -> list[dict]:
        types = types or ["choice"]
        if not images:
            return await self.generate_questions(fallback_text, count, types)
        out = []
        for i in range(count):
            t = types[i % len(types)]
            stem = f"[mock] 基于第 {i + 1} 张图片的内容，下列说法正确的是？"
            options = ["（mock）依据图片", "（mock）无关干扰", "（mock）部分正确", "（mock）反向干扰"][:4]
            out.append({
                "question_type": t,
                "stem": stem,
                "options": options if t == "choice" else None,
                "answer": options[0] if t == "choice" else "（mock）图片观察",
                "explanation": (
                    "（mock）真实场景请配置 DASHSCOPE_API_KEY；本结果由 mock "
                    f"provider 根据图片数量 {len(images)} 占位生成。"
                ),
                "difficulty": "easy",
            })
        return out

    async def polish(self, text: str, action: str) -> str:
        return f"[{action}]\n{text}"

    async def translate(self, text: str, target_lang: str) -> str:
        label = "English" if target_lang == "en" else "中文"
        return f"{text}\n\n[Translated to {label} (mock)]"

    async def generate_mindmap(
        self, title: str, content: str, max_depth: int = 3,
    ) -> dict:
        return {
            "label": title or "未命名笔记",
            "children": [
                {"label": "（mock）核心概念", "children": [
                    {"label": "定义"},
                    {"label": "性质"},
                ]},
                {"label": "（mock）应用场景", "children": [
                    {"label": "例题 1"},
                    {"label": "例题 2"},
                ]},
                {"label": "（mock）易错点"},
            ],
        }

    async def embed(self, text: str) -> list[float]:
        h = hashlib.sha256(text.encode("utf-8")).digest()
        seed = int.from_bytes(h[:8], "big")
        vec = [((seed >> (i % 64)) & 0xFF) / 255.0 for i in range(EMBED_DIM)]
        norm = sum(x * x for x in vec) ** 0.5 or 1.0
        return [x / norm for x in vec]

    async def cleanup_image(self, image_url: str) -> bytes:
        """Mock cleanup: return a 1x1 PNG. Image URL is ignored."""
        return _MOCK_PNG