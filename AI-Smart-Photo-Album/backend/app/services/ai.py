"""AI 服务封装：analyze_photo 与 extract_query_tags。

mock 分支（AI_SERVICE=mock 或 DASHSCOPE_API_KEY 为空）：
  不调用任何远程 API，从候选 scene/emotion/tag 列表里按 photo_id 稳定地
  挑选一个分类，便于本地开发 / CI / 演示，不需要 DashScope key。

real 分支（AI_SERVICE=real 且 DASHSCOPE_API_KEY 非空）：
  懒加载 model.inference.PhotoClassifier。
"""
from __future__ import annotations

import asyncio
import hashlib
import logging
import os
import random
import sys
from dataclasses import dataclass
from pathlib import Path

log = logging.getLogger(__name__)


def _load_backend_env():
    """把 backend/.env 显式注入到 os.environ。

    ClassifierConfig 用 os.getenv 读，pydantic-settings 只把它读进 Settings 对象
    不会自动 export 到 os.environ，所以这里手动 dotenv load 一次。
    """
    from dotenv import load_dotenv
    env_path = Path(__file__).resolve().parents[2] / ".env"  # backend/.env
    if env_path.exists():
        load_dotenv(env_path, override=False)


def _service_mode() -> str:
    """读取 AI_SERVICE，支持大小写与空白。"""
    return (os.getenv("AI_SERVICE", "mock") or "mock").strip().lower()


def _api_key_present() -> bool:
    """DASHSCOPE_API_KEY 或 ALIYUN_API_KEY 是否非空。"""
    _load_backend_env()
    key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("ALIYUN_API_KEY") or ""
    return bool(key.strip())


def _use_mock() -> bool:
    """mock = 显式 mock 或 缺 key 时降级。"""
    return _service_mode() != "real"


@dataclass
class AIAnalysisResult:
    """单张照片的分类结果。"""

    description: str
    scene_category_name: str
    scene_confidence: float
    emotion_category_name: str
    emotion_confidence: float
    tag_category_names: list[tuple[str, float]]


# 候选标签列表（与 model.inference.config.DEFAULT_* / seed 一致）
_MOCK_SCENES = [
    "🏖️ 海滩", "🏙️ 城市", "🏠 室内", "⛰️ 山景", "🌲 森林",
    "🌾 草原", "🏜️ 沙漠", "❄️ 雪景", "🏞️ 湖泊", "🌊 河流",
    "🏡 乡村", "🛣️ 街景", "🌃 夜景", "🏞️ 公园", "🌷 花园",
    "🏯 古镇", "🏝️ 海岛", "⚓ 码头", "🎓 校园", "🍽️ 餐厅",
]
_MOCK_EMOTIONS = [
    "😄 快乐", "😌 平静", "😢 忧伤", "🤩 兴奋", "🥰 温馨",
    "😔 孤独", "💕 浪漫", "🥹 怀旧", "💚 治愈", "🌿 清新",
    "🥲 感动", "😲 惊喜", "🕊️ 宁静", "🌧️ 忧郁", "😎 放松",
    "⚡ 活力", "☕ 惬意", "🌅 期待", "🤔 沉思", "😊 愉悦",
]
_MOCK_TAGS = [
    "👤 人物", "🏞️ 风景", "🐾 动物", "🍜 美食", "🏛️ 建筑",
    "🌿 植物", "🌸 花卉", "🐱 宠物", "👶 孩童", "👴 老人",
    "💑 情侣", "👫 朋友", "👨‍👩‍👧 家庭", "🤳 自拍", "📸 合影",
    "✈️ 旅行", "🎉 节日", "⚽ 运动", "🎨 艺术", "📷 街拍",
]
_MOCK_DESCRIPTIONS = [
    "画面构图清晰，光影层次分明，色彩饱和度自然。",
    "主体突出，背景虚化得当，整体氛围感强。",
    "细节丰富，质感表现细腻，适合作为纪念性照片。",
    "色调统一，节奏平稳，给人一种静谧的观感。",
    "瞬间捕捉到位，情绪表达自然真实。",
    "景深控制合理，前景与背景呼应良好。",
]


def _mock_analyze(photo_path: str, photo_id: int) -> AIAnalysisResult:
    """按 photo_path/photo_id 稳定地生成 mock 分类结果。"""
    seed_str = f"{photo_path}|{photo_id}"
    seed = int(hashlib.md5(seed_str.encode("utf-8")).hexdigest(), 16) % (2**32)
    rng = random.Random(seed)

    scene = rng.choice(_MOCK_SCENES)
    emotion = rng.choice(_MOCK_EMOTIONS)
    n_tags = rng.randint(2, 4)
    tags = rng.sample(_MOCK_TAGS, k=min(n_tags, len(_MOCK_TAGS)))
    tag_pairs = [(t, round(rng.uniform(0.55, 0.92), 3)) for t in tags]
    description = rng.choice(_MOCK_DESCRIPTIONS)

    return AIAnalysisResult(
        description=description,
        scene_category_name=scene,
        scene_confidence=round(rng.uniform(0.65, 0.95), 3),
        emotion_category_name=emotion,
        emotion_confidence=round(rng.uniform(0.55, 0.90), 3),
        tag_category_names=tag_pairs,
    )


def _mock_extract_tags(query: str) -> list[tuple[str, float]]:
    """从 query 里做关键词匹配，命中候选就给分。"""
    q = query.lower()
    out: list[tuple[str, float]] = []
    pools = [
        ("scene", _MOCK_SCENES),
        ("emotion", _MOCK_EMOTIONS),
        ("tag", _MOCK_TAGS),
    ]
    for _, items in pools:
        for item in items:
            # 取中文 / 英文部分做粗匹配
            for token in [item.split(" ", 1)[-1], item]:
                if len(token) >= 2 and token.lower() in q:
                    out.append((item, 0.9))
                    break
    # 去重保序
    seen, dedup = set(), []
    for name, score in out:
        if name in seen:
            continue
        seen.add(name)
        dedup.append((name, score))
    return dedup


# 懒加载 PhotoClassifier（缺包 / 缺 DASHSCOPE_API_KEY 时首次调用抛错）
_clf = None


def _get_classifier():
    global _clf
    if _clf is not None:
        return _clf
    if _use_mock():
        raise RuntimeError(
            "当前为 mock 模式，不应调用 _get_classifier（请检查 _use_mock 分支）"
        )
    _load_backend_env()
    # 文件布局: <repo_root>/backend/app/services/ai.py → parents[3] = repo_root (含 model/)
    _PROJECT_ROOT = Path(__file__).resolve().parents[3]
    if str(_PROJECT_ROOT) not in sys.path:
        sys.path.insert(0, str(_PROJECT_ROOT))
    from model.inference import ClassifierConfig, PhotoClassifier  # noqa: E402

    cfg = ClassifierConfig.from_env()
    if not cfg.aliyun_api_key.strip():
        raise RuntimeError("DASHSCOPE_API_KEY 未配置，AI 服务无法启动")
    _clf = PhotoClassifier(cfg)
    log.info(
        "AI 服务初始化完成: model=%s, base_url=%s",
        cfg.aliyun_model, cfg.aliyun_base_url or "<default>",
    )
    return _clf


def reset_classifier() -> None:
    """测试用: 重置单例。"""
    global _clf
    _clf = None


async def analyze_photo(photo_path: str, photo_id: int) -> AIAnalysisResult:
    """分析单张照片。失败抛异常。

    mock：本地确定性随机分类，无外部依赖。
    real：调用 model.inference.PhotoClassifier。
    """
    if _use_mock():
        # mock 模式下即使没 key 也能跑；让异步可被 cancel
        await asyncio.sleep(0)
        return _mock_analyze(photo_path, photo_id)

    clf = _get_classifier()
    result = await clf.analyze(photo_path, photo_id=photo_id)
    return AIAnalysisResult(
        description=result.description,
        scene_category_name=result.scene_category_name,
        scene_confidence=result.scene_confidence,
        emotion_category_name=result.emotion_category_name,
        emotion_confidence=result.emotion_confidence,
        tag_category_names=list(result.tag_category_names),
    )


async def extract_query_tags(query: str) -> list[tuple[str, float]]:
    """从自然语言 query 抽取相关分类。失败抛异常（搜索路由会处理）。"""
    query = (query or "").strip()
    if not query:
        return []
    if _use_mock():
        return _mock_extract_tags(query)
    clf = _get_classifier()
    return await clf.extract_query_tags(query)


__all__ = ["AIAnalysisResult", "analyze_photo", "extract_query_tags", "reset_classifier", "_use_mock"]
