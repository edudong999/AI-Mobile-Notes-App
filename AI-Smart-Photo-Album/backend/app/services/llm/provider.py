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
    async def generate_questions_from_images(
        self,
        images: list[str],
        count: int = 5,
        types: list[str] | None = None,
        fallback_text: str = "",
    ) -> list[dict]: ...

    @abstractmethod
    async def polish(self, text: str, action: str) -> str: ...

    @abstractmethod
    async def translate(self, text: str, target_lang: str) -> str: ...

    @abstractmethod
    async def generate_mindmap(
        self, title: str, content: str, max_depth: int = 3,
    ) -> dict: ...

    @abstractmethod
    async def embed(self, text: str) -> list[float]: ...

    @abstractmethod
    async def cleanup_image(self, image_url: str) -> bytes:
        """Run an image-edit model that removes handwriting stains and
        sharpens blurry text. Returns PNG/JPEG bytes of the cleaned image.
        Used by the /api/v1/note-image-cleanup endpoint.
        """
        ...
