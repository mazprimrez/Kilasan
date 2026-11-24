"""The journal's AI backend: five endpoints in front of Gemini on Vertex AI.

Run with `uvicorn app.main:create_app --factory`. The contract with the app is in
docs/JOURNAL_BACKEND.md.
"""

import json
import logging
from typing import Any

from fastapi import APIRouter, Depends, FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from . import book_recap, extraction, model, recap, settings as settings_module, tag, transcription
from .auth import TokenVerifier, google_verifier, require_owner
from .errors import ApiError
from .settings import Settings

# A five-minute voice note is about 2.4 MB and a photo is at most 2048 px on its long
# side; these leave room and still keep a request well inside Gemini's 20 MB.
MAX_MEDIA_BYTES = 10 * 1024 * 1024
MAX_RECAP_BYTES = 2 * 1024 * 1024

IMAGE_TYPES = ("image/jpeg", "image/png")


def create_app(
    settings: Settings | None = None,
    *,
    model_client: Any = None,
    verify_token: TokenVerifier | None = None,
) -> FastAPI:
    """Tests pass their own settings, model client and token check."""
    logging.basicConfig(level=logging.INFO)
    settings = settings or settings_module.load()
    model.configure(settings, model_client)
    owner_only = Depends(require_owner(settings, verify_token or google_verifier(settings)))

    app = FastAPI(title="Journal backend", docs_url=None, redoc_url=None, openapi_url=None)
    api = APIRouter(dependencies=[owner_only])

    @api.post("/transcribe")
    async def transcribe(request: Request) -> dict:
        mime_type = _content_type(request)
        if not mime_type.startswith("audio/"):
            raise ApiError(415, "Send the voice note as audio, for example audio/mp4.")
        audio = await _read_body(request, MAX_MEDIA_BYTES)
        return {"text": await transcription.transcribe(audio, mime_type)}

    @api.post("/extract-text")
    async def extract_text(request: Request) -> dict:
        mime_type = _content_type(request)
        if mime_type not in IMAGE_TYPES:
            raise ApiError(415, "Send the page as image/jpeg or image/png.")
        image = await _read_body(request, MAX_MEDIA_BYTES)
        return {"text": await extraction.extract_text(image, mime_type)}

    @api.post("/recap")
    async def write_recap(request: Request) -> dict:
        body = await _read_body(request, MAX_RECAP_BYTES)
        try:
            parsed = json.loads(body)
        except ValueError:
            raise ApiError(400, "The recap request must be JSON.")
        return await recap.write_recap(recap.check_request(parsed))

    @api.post("/tag")
    async def tag_entries(request: Request) -> dict:
        body = await _read_body(request, MAX_RECAP_BYTES)
        try:
            parsed = json.loads(body)
        except ValueError:
            raise ApiError(400, "The tag request must be JSON.")
        return await tag.tag_entries(tag.check_request(parsed))

    @api.post("/book-recap")
    async def write_book_recap(request: Request) -> dict:
        body = await _read_body(request, MAX_RECAP_BYTES)
        try:
            parsed = json.loads(body)
        except ValueError:
            raise ApiError(400, "The book summary request must be JSON.")
        return await book_recap.write_book_recap(book_recap.check_request(parsed))

    # The app calls /v1/...; the same routes also answer without the prefix.
    app.include_router(api, prefix="/v1")
    app.include_router(api)

    @app.get("/health")
    async def health() -> dict:
        return {"ok": True, "model": settings.model}

    @app.exception_handler(ApiError)
    async def api_error(_: Request, error: ApiError) -> JSONResponse:
        return JSONResponse({"error": error.message}, status_code=error.status)

    @app.exception_handler(StarletteHTTPException)
    async def http_error(_: Request, error: StarletteHTTPException) -> JSONResponse:
        return JSONResponse({"error": str(error.detail)}, status_code=error.status_code)

    return app


def _content_type(request: Request) -> str:
    return request.headers.get("content-type", "").split(";")[0].strip().lower()


async def _read_body(request: Request, limit: int) -> bytes:
    """The whole body, refused with 413 as soon as it is known to be over [limit]."""
    too_large = ApiError(413, f"That is too large. The limit is {limit // (1024 * 1024)} MB.")
    declared = request.headers.get("content-length", "")
    if declared.isdigit() and int(declared) > limit:
        raise too_large
    chunks: list[bytes] = []
    size = 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise too_large
        chunks.append(chunk)
    if size == 0:
        raise ApiError(400, "The request was empty.")
    return b"".join(chunks)
