"""A week's, month's or year's entries to a recap (layout version 2).

The model writes the chapters; this module holds it to them before anything reaches
the phone: the answer must parse, the quote must be the user's own words, and every
date must point at a day in the period that has entries. All numbers the recap shows
("Since last week", the Patterns chapter) are worked out on the phone, never here and
never by the model. Which people and topics came up is counted on the phone from the
tags /tag gives each entry, so the recap no longer names them.
"""

import datetime
import json
import logging
import re
from pathlib import Path

from typing import Annotated

from pydantic import BaseModel, Field, StringConstraints, ValidationError

from . import model
from .errors import ApiError

log = logging.getLogger(__name__)

# The prompt is a file of its own so it can be tuned without touching code.
PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "recap.md"

SCHEMA_VERSION = 2
PERIODS = ("week", "month", "year")

MAX_THREADS = 4
MAX_WINS = 3
MAX_OPEN = 4
MIN_QUOTE = 8
MAX_QUOTE = 160


# What the model must send back. Gemini is held to SCHEMA while it writes; the same
# shape is checked again here with Pydantic, because "held to" is not "guaranteed".


# A part the page cannot do without: spaces alone do not count as written.
NonBlank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class Quote(BaseModel):
    text: str
    date: str


class HowYouFelt(BaseModel):
    text: str
    days: list[str] = Field(default_factory=list)


class Thread(BaseModel):
    title: str
    line: str
    days: list[str]


class DayLine(BaseModel):
    date: str
    line: str


class DatedText(BaseModel):
    text: str
    date: str


class RecapAnswer(BaseModel):
    title: NonBlank
    quote: Quote
    howYouFelt: HowYouFelt
    threads: list[Thread]
    dayByDay: list[DayLine] = Field(default_factory=list)
    wins: list[DatedText] = Field(default_factory=list)
    stillOpen: list[DatedText] = Field(default_factory=list)
    note: NonBlank
    question: NonBlank
    # What happened, in time order. Patterns shows it under "The week, in short".
    summary: NonBlank


def _obj(properties: dict, required: list[str]) -> dict:
    return {"type": "OBJECT", "properties": properties, "required": required}


_STR = {"type": "STRING"}
_DAYS = {"type": "ARRAY", "items": _STR}
_DATED = _obj({"text": _STR, "date": _STR}, ["text", "date"])

SCHEMA = _obj(
    {
        "title": _STR,
        "quote": _obj({"text": _STR, "date": _STR}, ["text", "date"]),
        "howYouFelt": _obj({"text": _STR, "days": _DAYS}, ["text", "days"]),
        "threads": {"type": "ARRAY", "items": _obj({"title": _STR, "line": _STR, "days": _DAYS}, ["title", "line", "days"])},
        "dayByDay": {"type": "ARRAY", "items": _obj({"date": _STR, "line": _STR}, ["date", "line"])},
        "wins": {"type": "ARRAY", "items": _DATED},
        "stillOpen": {"type": "ARRAY", "items": _DATED},
        "note": _STR,
        "question": _STR,
        "summary": _STR,
    },
    ["title", "quote", "howYouFelt", "threads", "dayByDay", "wins", "stillOpen", "note", "question", "summary"],
)


def check_request(request: object) -> dict:
    """Raises a 400 unless [request] is a period with entries to write from."""
    if not isinstance(request, dict):
        raise ApiError(400, "The recap request must be a JSON object.")
    if request.get("period") not in PERIODS:
        raise ApiError(400, "period must be week, month or year.")
    start, end = _date(request.get("start")), _date(request.get("end"))
    if start is None or end is None or end < start:
        raise ApiError(400, "start and end must be dates, start first.")
    entries = request.get("entries")
    if not isinstance(entries, list) or not entries or not all(isinstance(e, dict) for e in entries):
        raise ApiError(400, "entries must list at least one entry.")
    return request


async def write_recap(request: dict) -> dict:
    """The recap for [request], in the shape the app reads (schemaVersion 2).

    At most three model calls: the first, one more if the answer does not parse, and
    one more if its quote is not word for word. A quote that is still wrong after
    that is replaced with a sentence picked from the entries here.
    """
    instruction = PROMPT_PATH.read_text(encoding="utf-8")
    document = json.dumps(request, ensure_ascii=False)
    texts_by_day = _texts_by_day(request)
    valid_days = _valid_days(request)

    feedback: list[str] = []
    schema_retries = quote_retries = 1
    while True:
        parts = [model.text_part(document)] + [model.text_part(f) for f in feedback]
        raw = await model.ask_json(feature="recap", instruction=instruction, parts=parts, schema=SCHEMA)
        try:
            answer = RecapAnswer.model_validate(raw)
        except ValidationError as e:
            if schema_retries == 0:
                raise ApiError(502, "The model wrote a recap that could not be read. Try again later.") from e
            schema_retries -= 1
            feedback.append(_schema_feedback(e))
            continue

        quote = verified_quote(answer.quote.text, answer.quote.date, texts_by_day)
        if quote is None and quote_retries > 0:
            quote_retries -= 1
            feedback.append(_quote_feedback(answer.quote))
            continue
        if quote is None:
            log.info("The quote was still not word for word after a retry; picking one from the entries.")
            quote = fallback_quote(texts_by_day)
        return _shape(answer, quote, request["period"], valid_days)


def verified_quote(quote: str, date: str, texts_by_day: dict[str, list[str]]) -> tuple[str, str] | None:
    """The quote and its day, if it is an exact part of one of that day's entries.

    Only whitespace may differ (runs of spaces and line breaks count as one space), and
    quotation marks around the whole quote are ignored. Case, spelling and punctuation
    must match: these are the user's own words, quoted back to them.
    """
    wanted = _squash(quote.strip().strip("\"'“”‘’"))
    if len(wanted) < MIN_QUOTE:
        return None
    for text in texts_by_day.get(date, []):
        if wanted in _squash(text):
            return wanted, date
    return None


def fallback_quote(texts_by_day: dict[str, list[str]]) -> tuple[str, str]:
    """A sentence taken straight from the entries: the first of fitting length in the longest entry."""
    candidates = sorted(
        ((_squash(text), date) for date, texts in texts_by_day.items() for text in texts),
        key=lambda pair: len(pair[0]),
        reverse=True,
    )
    for text, date in candidates:
        for sentence in re.split(r"(?<=[.!?…])\s+", text):
            if MIN_QUOTE <= len(sentence.strip()) <= MAX_QUOTE:
                return sentence.strip(), date
    for text, date in candidates:
        if len(text) >= MIN_QUOTE:
            return text[:MAX_QUOTE].rstrip(), date
    return "", ""


def _shape(answer: RecapAnswer, quote: tuple[str, str], period: str, valid: set[str]) -> dict:
    """The answer with every unusable date dropped, cut to the sizes the page has room for.

    A thread keeps the days that are real and is dropped when none are; a win or an
    open item with a date that is not a day with entries in the period is dropped.
    """

    def ok(day: str) -> bool:
        return day in valid

    threads = []
    for thread in answer.threads:
        days = _unique(d for d in thread.days if ok(d))
        if days and thread.title.strip() and thread.line.strip():
            threads.append({"title": thread.title.strip(), "line": thread.line.strip(), "days": days})
    day_by_day = sorted(
        ({"date": line.date, "line": line.line.strip()} for line in answer.dayByDay if ok(line.date) and line.line.strip()),
        key=lambda line: line["date"],
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "title": answer.title.strip(),
        "quote": {"text": quote[0], "date": quote[1]},
        "howYouFelt": {
            "text": answer.howYouFelt.text.strip(),
            "days": _unique(d for d in answer.howYouFelt.days if ok(d)),
        },
        "threads": threads[:MAX_THREADS],
        # A year has too many days to list one by one.
        "dayByDay": [] if period == "year" else day_by_day,
        "wins": [{"text": w.text.strip(), "date": w.date} for w in answer.wins if ok(w.date) and w.text.strip()][:MAX_WINS],
        "stillOpen": [{"text": o.text.strip(), "date": o.date} for o in answer.stillOpen if ok(o.date) and o.text.strip()][
            :MAX_OPEN
        ],
        "note": answer.note.strip(),
        "question": answer.question.strip(),
        "summary": answer.summary.strip(),
    }


def _schema_feedback(error: ValidationError) -> str:
    problems = "; ".join(f"{'.'.join(map(str, e['loc']))}: {e['msg']}" for e in error.errors()[:8])
    return (
        "Your previous answer did not match the required JSON shape "
        f"({problems}). Write the whole recap again, with every field filled in."
    )


def _quote_feedback(quote: Quote) -> str:
    return (
        f'Your previous quote, "{quote.text}" (dated {quote.date}), is not copied word for word from an entry '
        "written on that date. Write the whole recap again. The quote must be an exact, continuous piece of one "
        "entry's text from the date you give: same words, spelling, punctuation and language, nothing changed."
    )


def _texts_by_day(request: dict) -> dict[str, list[str]]:
    days: dict[str, list[str]] = {}
    for entry in request.get("entries", []):
        date, text = _text(entry.get("date")), _text(entry.get("text"))
        if date and text:
            days.setdefault(date, []).append(text)
    return days


def _valid_days(request: dict) -> set[str]:
    """Days a recap may point at: inside the period, with at least one entry."""
    start, end = _date(request.get("start")), _date(request.get("end"))
    days = set()
    for entry in request.get("entries", []):
        day = _date(entry.get("date"))
        if day is not None and start <= day <= end:
            days.add(day.isoformat())
    return days


def _date(value: object) -> datetime.date | None:
    try:
        return datetime.date.fromisoformat(value) if isinstance(value, str) else None
    except ValueError:
        return None


def _unique(days) -> list[str]:
    return sorted(set(days))


def _squash(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""
