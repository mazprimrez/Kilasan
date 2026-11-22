"""A week's, month's or year's entries to a recap."""

import json
import re
from pathlib import Path

from . import model
from .errors import ApiError

# The prompt is a file of its own so it can be tuned without touching code.
PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "recap.md"

PERIODS = ("week", "month", "year")
DIRECTIONS = ("up", "down", "same")
MAX_TRIES = 2

_DATED_LINE = {
    "type": "OBJECT",
    "properties": {"date": {"type": "STRING"}, "text": {"type": "STRING"}},
    "required": ["date", "text"],
}

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "title": {"type": "STRING"},
        "whatHappened": {"type": "ARRAY", "items": _DATED_LINE},
        "note": {"type": "STRING"},
        "quote": {"type": "STRING"},
        "quoteDate": {"type": "STRING"},
        "changes": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "direction": {"type": "STRING", "enum": list(DIRECTIONS)},
                    "text": {"type": "STRING"},
                },
                "required": ["direction", "text"],
            },
        },
        "tries": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": ["title", "whatHappened", "note", "quote", "quoteDate", "changes", "tries"],
}


def check_request(request: object) -> dict:
    """Raises a 400 unless [request] is a period with days to write from."""
    if not isinstance(request, dict):
        raise ApiError(400, "The recap request must be a JSON object.")
    if request.get("period") not in PERIODS:
        raise ApiError(400, "period must be week, month or year.")
    days = request.get("days")
    if not isinstance(days, list) or not days or not all(isinstance(day, dict) for day in days):
        raise ApiError(400, "days must list at least one day.")
    return request


async def write_recap(request: dict) -> dict:
    """The recap for [request], in the shape the app reads."""
    answer = await model.ask_json(
        feature="recap",
        instruction=PROMPT_PATH.read_text(encoding="utf-8"),
        parts=[model.text_part(json.dumps(request, ensure_ascii=False))],
        schema=SCHEMA,
    )
    title = _text(answer.get("title"))
    note = _text(answer.get("note"))
    if not title or not note:
        raise ApiError(502, "The model wrote a recap without a title or a note. Try again later.")
    quote, quote_date = verified_quote(_text(answer.get("quote")), _text(answer.get("quoteDate")), request["days"])
    return {
        "title": title,
        "quote": quote,
        "quoteDate": quote_date,
        "whatHappened": [
            {"date": _text(line.get("date")), "text": _text(line.get("text"))}
            for line in _dicts(answer.get("whatHappened"))
            if _text(line.get("text"))
        ],
        "note": note,
        "changes": [
            {
                "direction": line.get("direction") if line.get("direction") in DIRECTIONS else "same",
                "text": _text(line.get("text")),
            }
            for line in _dicts(answer.get("changes"))
            if _text(line.get("text"))
        ],
        "tries": [t for t in map(_text, _list(answer.get("tries"))) if t][:MAX_TRIES],
    }


def verified_quote(quote: str, claimed_date: str, days: list[dict]) -> tuple[str, str]:
    """The quote and the day it is really from, or two empty strings.

    The model is asked for the user's own words, but models paraphrase. A quote that is
    not in the entries (ignoring case, spacing and surrounding quotation marks) is
    dropped; the app then picks a sentence from the entries itself. The date is
    corrected when the words are in another day's entries.
    """
    wanted = _squash(quote.strip().strip("\"'“”‘’"))
    if not wanted:
        return "", ""
    ordered = sorted(days, key=lambda day: day.get("date") != claimed_date)
    for day in ordered:
        for entry in _list(day.get("entries")):
            if isinstance(entry, str) and wanted.casefold() in _squash(entry).casefold():
                return wanted, _text(day.get("date"))
    return "", ""


def _squash(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _list(value: object) -> list:
    return value if isinstance(value, list) else []


def _dicts(value: object) -> list[dict]:
    return [item for item in _list(value) if isinstance(item, dict)]
