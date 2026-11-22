"""The one call to Gemini on Vertex AI. Every endpoint's model work goes through ask_json.

Vertex AI is reached with the service account the service runs as (Application Default
Credentials). There is no API key.

Each call writes one usage line (see _write_usage), which the model-usage dashboard in
monitoring/ counts.
"""

import json
import logging
import time
from typing import Any

from google import genai
from google.genai import errors as genai_errors
from google.genai import types

from .errors import ApiError
from .settings import Settings

log = logging.getLogger(__name__)

# The app waits 120 seconds for an answer; give up a little before it does.
TIMEOUT_MS = 110_000

_client: Any = None
_model: str = ""


def configure(settings: Settings, client: Any = None) -> None:
    """Called once at startup. Tests pass their own client."""
    global _client, _model
    _model = settings.model
    _client = client or genai.Client(
        vertexai=True,
        project=settings.project,
        location=settings.location,
        http_options=types.HttpOptions(timeout=TIMEOUT_MS),
    )


def text_part(text: str) -> types.Part:
    return types.Part(text=text)


def file_part(data: bytes, mime_type: str) -> types.Part:
    return types.Part.from_bytes(data=data, mime_type=mime_type)


async def ask_json(*, feature: str, instruction: str, parts: list[types.Part], schema: dict) -> dict:
    """Sends one request and returns the model's answer, which is held to [schema].

    [feature] names the endpoint asking, for the usage line; it never reaches the model.

    No sampling settings are sent: current Gemini models ignore them and later ones
    reject them, and leaving them out keeps a model upgrade to changing GEMINI_MODEL.
    """
    started = time.monotonic()
    response = None
    status = 200
    try:
        response = await _generate(instruction, parts, schema)
        return _answer_from(response)
    except ApiError as e:
        status = e.status
        raise
    finally:
        _write_usage(feature, response, status, time.monotonic() - started)


async def _generate(instruction: str, parts: list[types.Part], schema: dict) -> Any:
    try:
        return await _client.aio.models.generate_content(
            model=_model,
            contents=[types.Content(role="user", parts=parts)],
            config=types.GenerateContentConfig(
                system_instruction=instruction,
                response_mime_type="application/json",
                response_schema=schema,
            ),
        )
    except genai_errors.APIError as e:
        raise _from_api_error(e) from e
    except Exception as e:
        log.error("Could not reach the model (%s): %s", _model, type(e).__name__)
        raise ApiError(503, "The model could not be reached. Try again later.") from e


def _answer_from(response: Any) -> dict:
    if not response.candidates:
        log.warning("The model returned no answer: %s", getattr(response.prompt_feedback, "block_reason", None))
        raise ApiError(422, "The model declined to read this.")
    reason = response.candidates[0].finish_reason
    if reason == types.FinishReason.MAX_TOKENS:
        raise ApiError(502, "The model's answer was cut short. Try again later.")
    if reason not in (None, types.FinishReason.STOP):
        log.warning("The model stopped early: %s", reason)
        raise ApiError(422, "The model declined to read this.")
    try:
        answer = json.loads(response.text or "")
    except json.JSONDecodeError:
        answer = None
    if not isinstance(answer, dict):
        raise ApiError(502, "The model sent an answer that could not be read. Try again later.")
    return answer


def _write_usage(feature: str, response: Any, status: int, seconds: float) -> None:
    """One JSON line on stdout, which Cloud Run stores as a structured log entry.

    Only counts and names: never the request, the answer or who asked. The log-based
    metrics in monitoring/ read these field names, so rename them there too.
    """
    usage = getattr(response, "usage_metadata", None)
    entry = {
        "severity": "INFO" if status == 200 else "WARNING",
        "message": f"model usage: {feature} {status}",
        "event": "model_usage",
        "feature": feature,
        "model": _model,
        "status": status,
        "latency_ms": round(seconds * 1000),
        "input_tokens": getattr(usage, "prompt_token_count", None) or 0,
        "output_tokens": getattr(usage, "candidates_token_count", None) or 0,
        "thinking_tokens": getattr(usage, "thoughts_token_count", None) or 0,
    }
    print(json.dumps(entry), flush=True)


def _from_api_error(e: genai_errors.APIError) -> ApiError:
    # Google's message names the model and the problem, never what the user sent.
    log.error("The model (%s) answered %s: %s", _model, e.code, getattr(e, "message", ""))
    if e.code == 429:
        return ApiError(429, "The model is busy. Try again later.")
    if e.code == 400:
        return ApiError(422, "The model could not read this.")
    if e.code in (401, 403, 404):
        # The service account or the model name is wrong. That is fixed on the server,
        # so answer with a status the app retries by itself once it is.
        return ApiError(500, "The model is not set up correctly on the backend.")
    return ApiError(503, "The model is not available right now. Try again later.")
