"""One setting, GEMINI_MODEL, decides the model for all three endpoints."""

import pytest
from fastapi.testclient import TestClient

from app import settings as settings_module
from app.main import create_app

from conftest import fake_verify, sample, settings

REQUIRED = {"ALLOWED_EMAIL": "owner@example.com", "GOOGLE_CLIENT_ID": "id.apps.googleusercontent.com"}


def test_the_model_defaults_to_flash_lite():
    assert settings_module.load(REQUIRED).model == "gemini-3.5-flash-lite"


def test_gemini_model_overrides_the_default():
    assert settings_module.load({**REQUIRED, "GEMINI_MODEL": " gemini-9-flash-lite "}).model == "gemini-9-flash-lite"


def test_every_endpoint_uses_the_configured_model(fake, owner):
    client = TestClient(create_app(settings("gemini-9-flash-lite"), model_client=fake, verify_token=fake_verify))

    client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})
    client.post("/v1/extract-text", content=b"image", headers={**owner, "Content-Type": "image/jpeg"})
    fake.answer = sample("week_answer")
    client.post("/v1/recap", json=sample("week"), headers=owner)

    assert [call.model for call in fake.calls] == ["gemini-9-flash-lite"] * 3


def test_health_reports_the_model_without_signing_in(client):
    assert client.get("/health").json() == {"ok": True, "model": "test-model"}


def test_no_sampling_settings_are_sent(client, fake, owner):
    client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})

    config = fake.calls[0].config
    assert config.temperature is None and config.top_p is None and config.top_k is None
    assert config.response_mime_type == "application/json"


@pytest.mark.parametrize("missing", ["ALLOWED_EMAIL", "GOOGLE_CLIENT_ID"])
def test_the_service_refuses_to_start_without_its_access_settings(missing):
    env = {key: value for key, value in REQUIRED.items() if key != missing}
    with pytest.raises(RuntimeError, match=missing):
        settings_module.load(env)
