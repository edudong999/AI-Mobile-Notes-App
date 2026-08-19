import asyncio
import json
import os
import base64
import httpx
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

DASHSCOPE_IMAGE_EDIT_URL = (
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/"
    "image2image/image-edit"
)

OCR_PROMPT = (
    "你是图片识别助手。请仔细观察图片：\n"
    "1) 若图中含文字（含手写/印刷/屏幕），完整识别并放入 ocr_text；\n"
    "2) 始终用 1-2 句话概括图片内容/场景作为 description；\n"
    "3) 提炼 2-4 个关键要点作为 key_points；\n"
    "严格输出 JSON（不要 markdown 代码块），格式：\n"
    '{"ocr_text":"...", "description":"...", "key_points":["..."], "confidence":0.9}'
)

MINDMAP_PROMPT = (
    "你是思维导图整理助手。请阅读下面的笔记，构造一个层级思维导图：\n"
    "- 根节点 label 用笔记标题；\n"
    "- 第二层 3-6 个主要分支（章节或主题）；\n"
    "- 每个分支下 1-4 个子节点（具体知识点或要点）；\n"
    "- 总深度不超过 {max_depth} 层。\n"
    "严格输出 JSON（不要 markdown 代码块），结构：\n"
    '{{"label":"根节点","children":[{{"label":"分支","children":[{{"label":"要点"}}]}}]}}\n\n'
    "笔记标题：{title}\n\n笔记内容：\n{content}"
)


def _strip_code_fence(text: str) -> str:
    """去掉 ```json ... ``` 这种 markdown 包裹。DashScope 经常这样返回。"""
    t = text.strip()
    if t.startswith("```"):
        # 去掉首行 ```json / ``` 和末行 ```
        first_nl = t.find("\n")
        if first_nl >= 0:
            t = t[first_nl + 1 :]
        if t.endswith("```"):
            t = t[: -3]
        t = t.strip()
    return t

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
            AioMultiModalConversation.call(model=self.ocr_model, messages=messages)
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"OCR 调用失败: {resp.code} {resp.message}")
        text = resp.output.choices[0].message.content[0]["text"]
        cleaned = _strip_code_fence(text)
        try:
            data = json.loads(cleaned)
            return {
                "ocr_text": data.get("ocr_text", ""),
                "description": data.get("description", ""),
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

    async def generate_questions_from_images(
        self,
        images: list[str],
        count: int = 5,
        types: list[str] | None = None,
        fallback_text: str = "",
    ) -> list[dict]:
        """基于图片内容生成练习题（多模态）。

        使用 qwen-vl-max 观察图片中的题目/知识点/场景，再让模型据此出题。
        题目类型可指定（choice/fill/short_answer 等）；无图时回退到文本版生成。
        """
        if not images:
            return await self.generate_questions(fallback_text, count, types)

        types = types or ["choice", "fill"]
        prompt = (
            "你是一位出题老师。请仔细观察下面这些图片（可能是题目截图、笔记、"
            "板书、教科书内页或任何含知识的画面），先简要概括图中出现的核心知识点"
            "（不必写到 JSON 里），然后据此出 "
            f"{count} 道练习题（类型：{','.join(types)}），"
            "严格输出 JSON 数组（不要 markdown 代码块），每个元素：\n"
            '{"question_type":"choice|fill|short_answer",'
            '"stem":"题目题干",'
            '"options":["A.","B.","C.","D."],'  # choice 时填，fill/short 时为 null
            '"answer":"正确答案",'
            '"explanation":"解析",'
            '"difficulty":"easy|medium|hard"}\n'
            "注意：\n"
            "- choice 题的 options 必须是 2-4 个字符串数组；fill/short 时 options 必须为 null；\n"
            "- 题干用中文，难度按 easy/medium/hard 自评；\n"
            "- 不要捏造图中没有的数值或公式，看不清的宁可跳过。"
        )
        content: list[dict] = []
        for url in images:
            content.append({"image": url})
        content.append({"text": prompt})

        messages = [{"role": "user", "content": content}]
        resp = await self._call_with_timeout(
            AioMultiModalConversation.call(model=self.ocr_model, messages=messages)
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"题目生成失败: {resp.code} {resp.message}")
        text = resp.output.choices[0].message.content[0]["text"]
        cleaned = _strip_code_fence(text)
        try:
            data = json.loads(cleaned)
        except json.JSONDecodeError:
            start, end = cleaned.find("["), cleaned.rfind("]") + 1
            if start >= 0 and end > start:
                data = json.loads(cleaned[start:end])
            else:
                raise LLMParseError(
                    f"generate_questions_from_images JSON 失败: {cleaned[:200]}"
                )
        if not isinstance(data, list):
            raise LLMParseError(
                f"题目生成返回非数组: {type(data).__name__}"
            )
        return data

    async def polish(self, text: str, action: str) -> str:
        action_zh = {"polish": "润色", "expand": "扩写", "shorten": "精简"}.get(action, action)
        return await self._gen(f"请对下面文本进行{action_zh}，保持原意：\n\n{text}")

    async def translate(self, text: str, target_lang: str) -> str:
        lang = "英文" if target_lang == "en" else "中文"
        return await self._gen(f"请将下面内容翻译为{lang}：\n\n{text}")

    async def generate_mindmap(
        self, title: str, content: str, max_depth: int = 3,
    ) -> dict:
        """基于笔记标题与正文生成树状思维导图（嵌套 label/children）。"""
        prompt = MINDMAP_PROMPT.format(
            title=title or "未命名笔记",
            content=(content or "")[:2000],
            max_depth=max_depth,
        )
        out = await self._gen(prompt)
        cleaned = _strip_code_fence(out)
        try:
            data = json.loads(cleaned)
        except json.JSONDecodeError:
            start, end = cleaned.find("{"), cleaned.rfind("}") + 1
            if start >= 0 and end > start:
                data = json.loads(cleaned[start:end])
            else:
                raise LLMParseError(f"generate_mindmap JSON 失败: {cleaned[:200]}")
        if not isinstance(data, dict) or "label" not in data:
            raise LLMParseError(f"思维导图根节点缺失 label: {cleaned[:200]}")
        return data

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
            AioGeneration.call(model=self.llm_model, prompt=prompt, result_format="message")
        )
        if resp.status_code != 200:
            raise LLMAuthError(f"Generation 失败: {resp.code} {resp.message}")
        return resp.output.choices[0].message.content.strip()

    async def cleanup_image(self, image_url: str) -> bytes:
        """Call DashScope qwen-image-edit to remove handwriting stains and
        sharpen blurry text; return the cleaned image as raw PNG/JPEG bytes.

        `image_url` may be either an http(s) URL or a `data:image/...;base64,...`
        URI. We forward it as the `image` field. On non-200, raise LLMAuthError;
        on timeout, LLMTimeoutError.
        """
        body = {
            "model": "qwen-image-edit",
            "input": {
                "image": image_url,
                "prompt": (
                    "去除图片中的手写污渍/涂鸦/水印，"
                    "保持原有印刷文字与版面；模糊的文字请尽力锐化修正。"
                ),
            },
            "parameters": {"n": 1, "size": "1024*1024"},
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        async def _do_post() -> httpx.Response:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                return await client.post(
                    DASHSCOPE_IMAGE_EDIT_URL, json=body, headers=headers,
                )
        resp = await self._call_with_timeout(_do_post())
        if resp.status_code != 200:
            raise LLMAuthError(
                f"image-edit 失败: HTTP {resp.status_code} {resp.text[:200]}"
            )
        data = resp.json()
        try:
            url_or_b64 = data["output"]["results"][0]["url"]
        except (KeyError, IndexError, TypeError) as e:
            raise LLMParseError(f"image-edit 返回结构异常: {e}; raw={resp.text[:200]}")
        if url_or_b64.startswith("data:"):
            try:
                _, payload = url_or_b64.split(",", 1)
                return base64.b64decode(payload)
            except Exception as e:
                raise LLMParseError(f"image-edit base64 解码失败: {e}")
        # Otherwise treat as URL and fetch the bytes
        async def _do_get() -> httpx.Response:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                return await client.get(url_or_b64)
        img_resp = await self._call_with_timeout(_do_get())
        if img_resp.status_code != 200:
            raise LLMAuthError(
                f"image-edit 拉取结果失败: HTTP {img_resp.status_code}"
            )
        return img_resp.content