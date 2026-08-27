class LLMError(Exception):
    """所有 LLM 相关错误基类"""

class LLMTimeoutError(LLMError):
    """单次 LLM 调用超过 30s"""

class LLMParseError(LLMError):
    """LLM 返回的 JSON / 结构无法解析"""

class LLMAuthError(LLMError):
    """API key 无效 / quota 用尽"""
