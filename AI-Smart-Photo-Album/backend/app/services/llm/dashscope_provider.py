import asyncio
import json
import os
from .errors import LLMTimeoutError, LLMAuthError, LLMParseError
from .provider import LLMProvider

try:
    import dashscope
    from dashscope import (
        AioMultiModalConversation, AioGeneration, TextEmbedding,
    )
    _HAS_DASHSCOPE = True
except ImportError:
    _HAS_DASHSCOPE = False

OCR_PROMPT = (
    "你是 OCR 助手。请识别图片中所有文字（含手写内容），"
    "对模糊字或污渍尽量合理还原；输出 JSON："
    '{"ocr_text": "...", "key_points": ["...", "..."], "confidence": 0.9}'
)

class DashScopeProvider(LLMProvider):
    name = "dashscope"

    def __init__(self, api_key: str | None = None,
                 ocr_model: str = "qwen-vl-max",
                 llm_model: str = "qwen-plus",
                 embed_model: str = "text-embedding-v3",
                 timeout: int = 30):
        if not _HAS_DASHSCOPE:
            raise LLMAuthError("dashscope SDK 未安装")
        self.api_key = api_key or os.environ.get("DASHSCOPE_API_KEY", "")
        if not self.api_key:
            raise LLMAuthError("DASHSCOPE_API_KEY 未设置")
        dashscope.api_key = self.api_key
        self.ocr_model = ocr_model
        self.llm_model = llm_model
        self.embed_model = embed_model
        self.timeout = timeout

    async def _call_with_timeout(self, coro):
        try:
            return await asyncio.wait_for(coro, timeout=self.timeout)
        except asyncio.TimeoutError:
            raise LLMTimeoutError(f"LLM 调用超时 ({self.timeout}s)")

    async def analyze_image(self, image_url: str, prompt: str) -> dict:
        messages = [{
            "role": "user",
            "content": [
                {"image": image_url},
                {"text": prompt or OCR_PROMPT},
            ],
        }]
        resp = await self._call_with_timeout(
            AioMultiModalConversation.acall(model=self.ocr_model, messages=messages)
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"OCR 调用失败: {resp.code} {resp.message}")
        text = resp.output.choices[0].message.content[0]["text"]
        try:
            data = json.loads(text)
            return {
                "ocr_text": data.get("ocr_text", ""),
                "key_points": data.get("key_points", []),
                "confidence": float(data.get("confidence", 0.9)),
            }
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            raise LLMParseError(f"OCR JSON 解析失败: {e}; raw={text[:200]}")

    async def summarize(self, text: str, max_words: int = 120) -> str:
        prompt = f"用中文总结下面内容，不超过 {max_words} 字：\n\n{text}"
        return await self._gen(prompt)

    async def extract_key_points(self, text: str, max_points: int = 5) -> list[str]:
        prompt = (
            f"从下面内容提炼最多 {max_points} 条关键知识点，"
            "每条一行，不要编号：\n\n" + text
        )
        out = await self._gen(prompt)
        return [l.strip("-• ").strip() for l in out.splitlines() if l.strip()][:max_points]

    async def generate_questions(
        self, text: str, count: int = 5, types: list[str] | None = None,
    ) -> list[dict]:
        types = types or ["choice", "fill"]
        prompt = (
            f"基于下面内容生成 {count} 道练习题（类型：{','.join(types)}），"
            "严格输出 JSON 数组，每个元素："
            '{"question_type":"choice|fill|short_answer","stem":"...","options":[...],'
            '"answer":"...","explanation":"...","difficulty":"easy|medium|hard"}\n\n'
            + text
        )
        out = await self._gen(prompt)
        try:
            return json.loads(out)
        except json.JSONDecodeError:
            start, end = out.find("["), out.rfind("]") + 1
            if start >= 0 and end > start:
                return json.loads(out[start:end])
            raise LLMParseError(f"generate_questions JSON 失败: {out[:200]}")

    async def polish(self, text: str, action: str) -> str:
        action_zh = {"polish": "润色", "expand": "扩写", "shorten": "精简"}.get(action, action)
        return await self._gen(f"请对下面文本进行{action_zh}，保持原意：\n\n{text}")

    async def translate(self, text: str, target_lang: str) -> str:
        lang = "英文" if target_lang == "en" else "中文"
        return await self._gen(f"请将下面内容翻译为{lang}：\n\n{text}")

    async def embed(self, text: str) -> list[float]:
        def _sync_embed_call():
            return TextEmbedding.call(model=self.embed_model, input=text)
        resp = await self._call_with_timeout(
            asyncio.to_thread(_sync_embed_call)
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"embed 失败: {resp.code} {resp.message}")
        return list(resp.output["embeddings"][0]["embedding"])

    async def _gen(self, prompt: str) -> str:
        resp = await self._call_with_timeout(
            AioGeneration.acall(model=self.llm_model, prompt=prompt, result_format="message")
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"Generation 失败: {resp.code} {resp.message}")
        return resp.output.choices[0].message.content.strip()