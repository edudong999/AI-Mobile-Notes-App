import pytest
from app.services.llm.provider import LLMProvider
from app.services.llm.errors import LLMError, LLMTimeoutError, LLMParseError, LLMAuthError

def test_cannot_instantiate_abc():
    with pytest.raises(TypeError):
        LLMProvider()

def test_error_inheritance():
    assert issubclass(LLMTimeoutError, LLMError)
    assert issubclass(LLMParseError, LLMError)
    assert issubclass(LLMAuthError, LLMError)
    err = LLMTimeoutError("x")
    assert str(err) == "x"
