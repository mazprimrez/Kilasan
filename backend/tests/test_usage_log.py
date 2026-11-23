"""Every model call writes one usage line, which the model-usage dashboard counts."""

import json
from types import SimpleNamespace

from google.genai import errors as genai_errors

from conftest import sample


def usage_lines(capsys) -> list[dict]:
    lines = [json.loads(line) for line in capsys.readouterr().out.splitlines() if line.startswith("{")]
    return [line for line in lines if line.get("event") == "model_usage"]


def test_a_call_logs_its_feature_model_and_tokens(client, fake, owner, capsys):
    fake.usage = SimpleNamespace(prompt_token_count=1500, candidates_token_count=90, thoughts_token_count=12)

    client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})

    [line] = usage_lines(capsys)
    assert line["feature"] == "transcribe"
    assert line["model"] == "test-model"
    assert line["status"] == 200
    assert line["severity"] == "INFO"
    assert (line["input_tokens"], line["output_tokens"], line["thinking_tokens"]) == (1500, 90, 12)
    assert isinstance(line["latency_ms"], int)


def test_each_endpoint_logs_its_own_feature(client, fake, owner, capsys):
    client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})
    client.post("/v1/extract-text", content=b"image", headers={**owner, "Content-Type": "image/jpeg"})
    fake.answer = sample("week_answer")
    client.post("/v1/recap", json=sample("week"), headers=owner)
    fake.answer = {"title": "A book", "summary": "A summary.", "ideas": []}
    client.post("/v1/book-recap", json=sample("book"), headers=owner)

    assert [line["feature"] for line in usage_lines(capsys)] == ["transcribe", "extract-text", "recap", "book-recap"]


def test_a_failed_call_logs_the_status_the_app_got(client, fake, owner, capsys):
    fake.error = genai_errors.APIError(429, {"error": {"code": 429, "message": "busy", "status": "X"}})

    response = client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})

    [line] = usage_lines(capsys)
    assert response.status_code == line["status"] == 429
    assert line["severity"] == "WARNING"
    assert line["input_tokens"] == line["output_tokens"] == 0


def test_a_refused_answer_still_logs_the_tokens_it_cost(client, fake, owner, capsys):
    fake.answer = None

    client.post("/v1/transcribe", content=b"audio", headers={**owner, "Content-Type": "audio/mp4"})

    [line] = usage_lines(capsys)
    assert line["status"] == 422
    assert line["input_tokens"] == 1200


def test_the_usage_line_never_holds_what_was_sent_or_answered(client, fake, owner, capsys):
    fake.answer = {"text": "a secret diary entry"}

    client.post("/v1/transcribe", content=b"private audio bytes", headers={**owner, "Content-Type": "audio/mp4"})

    out = capsys.readouterr().out
    assert "secret diary" not in out
    assert "private audio" not in out
    assert "owner@example.com" not in out
