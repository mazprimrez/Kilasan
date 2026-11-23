import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from google.genai import types

from app.main import create_app
from app.settings import Settings

SAMPLES = Path(__file__).parent / "samples"
OWNER = "owner@example.com"
CLIENT_ID = "test-client.apps.googleusercontent.com"

# What the fake token check knows. Anything else is not a token Google signed.
TOKENS = {
    "owner-token": {"email": OWNER, "email_verified": True},
    "owner-shouting-token": {"email": "Owner@Example.com", "email_verified": "true"},
    "stranger-token": {"email": "stranger@example.com", "email_verified": True},
    "unverified-token": {"email": OWNER, "email_verified": False},
}


def fake_verify(token: str) -> dict:
    if token not in TOKENS:
        raise ValueError("Token is not valid")
    return TOKENS[token]


class FakeModel:
    """Stands in for Vertex AI: records each call and answers with what the test set."""

    def __init__(self):
        self.calls = []
        self.answer: object = {"text": ""}
        # Answers for consecutive calls, used before [answer] while any are left.
        self.answers: list = []
        self.finish_reason = types.FinishReason.STOP
        self.error: Exception | None = None
        self.usage = SimpleNamespace(prompt_token_count=1200, candidates_token_count=80, thoughts_token_count=None)
        self.aio = SimpleNamespace(models=self)

    async def generate_content(self, *, model, contents, config):
        self.calls.append(SimpleNamespace(model=model, parts=contents[0].parts, config=config))
        if self.error:
            raise self.error
        answer = self.answers.pop(0) if self.answers else self.answer
        if answer is None:
            return SimpleNamespace(
                candidates=[], prompt_feedback=SimpleNamespace(block_reason="SAFETY"), text=None, usage_metadata=self.usage
            )
        text = answer if isinstance(answer, str) else json.dumps(answer)
        candidate = SimpleNamespace(finish_reason=self.finish_reason)
        return SimpleNamespace(candidates=[candidate], prompt_feedback=None, text=text, usage_metadata=self.usage)


def settings(model: str = "test-model") -> Settings:
    return Settings(model=model, allowed_email=OWNER, client_ids=(CLIENT_ID,), project="test-project", location="global")


@pytest.fixture
def fake() -> FakeModel:
    return FakeModel()


@pytest.fixture
def client(fake) -> TestClient:
    return TestClient(create_app(settings(), model_client=fake, verify_token=fake_verify))


@pytest.fixture
def owner() -> dict:
    return {"Authorization": "Bearer owner-token"}


def sample(name: str) -> dict:
    return json.loads((SAMPLES / f"{name}.json").read_text(encoding="utf-8"))
