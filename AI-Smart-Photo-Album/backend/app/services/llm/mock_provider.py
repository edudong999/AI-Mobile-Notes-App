import hashlib
import re
from .provider import LLMProvider

EMBED_DIM = 768

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

    async def polish(self, text: str, action: str) -> str:
        return f"[{action}]\n{text}"

    async def translate(self, text: str, target_lang: str) -> str:
        label = "English" if target_lang == "en" else "中文"
        return f"{text}\n\n[Translated to {label} (mock)]"

    async def embed(self, text: str) -> list[float]:
        h = hashlib.sha256(text.encode("utf-8")).digest()
        seed = int.from_bytes(h[:8], "big")
        vec = [((seed >> (i % 64)) & 0xFF) / 255.0 for i in range(EMBED_DIM)]
        norm = sum(x * x for x in vec) ** 0.5 or 1.0
        return [x / norm for x in vec]