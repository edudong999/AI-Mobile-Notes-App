from abc import ABC, abstractmethod

class LLMProvider(ABC):
    name: str = "abstract"

    @abstractmethod
    async def analyze_image(self, image_url: str, prompt: str) -> dict: ...

    @abstractmethod
    async def summarize(self, text: str, max_words: int = 120) -> str: ...

    @abstractmethod
    async def extract_key_points(self, text: str, max_points: int = 5) -> list[str]: ...

    @abstractmethod
    async def generate_questions(
        self, text: str, count: int = 5, types: list[str] | None = None,
    ) -> list[dict]: ...

    @abstractmethod
    async def polish(self, text: str, action: str) -> str: ...

    @abstractmethod
    async def translate(self, text: str, target_lang: str) -> str: ...

    @abstractmethod
    async def embed(self, text: str) -> list[float]: ...
