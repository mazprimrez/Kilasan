"""A book's thoughts to a summary of the reading and ideas for a review."""

import json
import re
from pathlib import Path

from . import model
from .errors import ApiError

# The prompt is a file of its own so it can be tuned without touching code.
PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "book_recap.md"

MIN_THOUGHTS = 3
MAX_POINTS = 5

# A point is a title and one line, and the summary one short paragraph. Anything
# longer is the model starting to write the review, which is the reader's to write.
MAX_TITLE_CHARS = 120
MAX_POINT_TITLE_CHARS = 80
MAX_LINE_CHARS = 220
MAX_SUMMARY_CHARS = 900
MAX_GAP_CHARS = 220

# Words in quotation marks this long are taken to be copied out of the book.
COPIED_WORDS = 3

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "title": {"type": "STRING"},
        "quote": {"type": "STRING"},
        "quotePage": {"type": "INTEGER"},
        "summary": {"type": "STRING"},
        "points": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "title": {"type": "STRING"},
                    "line": {"type": "STRING"},
                    "pages": {"type": "ARRAY", "items": {"type": "INTEGER"}},
                    "fromNotes": {"type": "BOOLEAN"},
                },
                "required": ["title", "line", "pages", "fromNotes"],
            },
        },
        "gap": {"type": "STRING"},
    },
    "required": ["title", "quote", "quotePage", "summary", "points", "gap"],
}


def check_request(request: object) -> dict:
    """Raises a 400 unless [request] is a book with enough thoughts to write from."""
    if not isinstance(request, dict):
        raise ApiError(400, "The book summary request must be a JSON object.")
    book = request.get("book")
    if not isinstance(book, dict) or not _text(book.get("title")):
        raise ApiError(400, "book must have a title.")
    thoughts = request.get("thoughts")
    if not isinstance(thoughts, list) or not all(isinstance(thought, dict) for thought in thoughts):
        raise ApiError(400, "thoughts must be a list of thoughts.")
    usable = len(_usable(thoughts))
    if usable < MIN_THOUGHTS:
        raise ApiError(
            400,
            f"A summary needs at least {MIN_THOUGHTS} thoughts with words and a page. This book has {usable}.",
        )
    return request


async def write_book_recap(request: dict) -> dict:
    """The summary and review ideas for [request], in the shape the app reads."""
    answer = await model.ask_json(
        feature="book-recap",
        instruction=PROMPT_PATH.read_text(encoding="utf-8"),
        parts=[model.text_part(json.dumps(request, ensure_ascii=False))],
        schema=SCHEMA,
    )
    thoughts = _usable(request["thoughts"])
    title = _clip(_one_line(answer.get("title")), MAX_TITLE_CHARS)
    summary = _clip(_one_line(answer.get("summary")), MAX_SUMMARY_CHARS)
    points = checked_points(answer.get("points"), thoughts)
    if not title or not summary or not points:
        raise ApiError(502, "The model wrote a summary without a title, a summary or any points. Try again later.")
    quote, quote_page = verified_quote(_text(answer.get("quote")), _page(answer.get("quotePage")), thoughts)
    return {
        "title": title,
        "quote": quote,
        "quotePage": quote_page,
        "summary": summary,
        "points": points,
        "gap": _clip(_one_line(answer.get("gap")), MAX_GAP_CHARS),
    }


def verified_quote(quote: str, claimed_page: int | None, thoughts: list[dict]) -> tuple[str, int | None]:
    """The quote as the reader wrote it and the page it is really from, or ("", None).

    The model is asked for the reader's own words, but models paraphrase. A quote that
    is not in the thoughts (ignoring case, spacing and surrounding quotation marks) is
    dropped, and the app then picks a sentence from the thoughts itself. So is one that
    touches a line the reader copied out of the book: that is the author's sentence,
    and the summary never quotes the book. The page is corrected when the words are in
    another page's thought.
    """
    wanted = _squash(quote.strip().strip("\"'“”‘’"))
    if not wanted:
        return "", None
    pattern = re.compile(re.escape(wanted), re.IGNORECASE)
    ordered = sorted(thoughts, key=lambda thought: thought["page"] != claimed_page)
    for thought in ordered:
        text = _squash(thought["text"])
        copied = _copied_spans(text)
        for found in pattern.finditer(text):
            if not any(found.start() < end and start < found.end() for start, end in copied):
                # Their spelling and capitals, not the model's.
                return found.group(0), thought["page"]
    return "", None


def checked_points(value: object, thoughts: list[dict]) -> list[dict]:
    """At most five points, each a title and one line, citing only pages that have a thought.

    A point that names no such page is not drawn from the notes, whatever the model
    says, so the app shows it unticked.
    """
    noted = {thought["page"] for thought in thoughts}
    points = []
    for point in _dicts(value):
        title = _clip(_one_line(point.get("title")), MAX_POINT_TITLE_CHARS)
        if not title:
            continue
        claimed = {page for page in map(_page, _list(point.get("pages"))) if page is not None}
        pages = sorted(claimed & noted) if point.get("fromNotes") is True else []
        points.append(
            {
                "title": title,
                "line": _clip(_one_line(point.get("line")), MAX_LINE_CHARS),
                "pages": pages,
                "fromNotes": bool(pages),
            }
        )
    return points[:MAX_POINTS]


def _usable(thoughts: list) -> list[dict]:
    """The thoughts that have words and a page: what a summary can be written from."""
    usable = []
    for thought in thoughts:
        if not isinstance(thought, dict):
            continue
        page = _page(thought.get("page"))
        text = _text(thought.get("text"))
        if page is not None and text:
            usable.append({"page": page, "text": text})
    return usable


_QUOTED = re.compile(r"“[^“”]*”|\"[^\"]*\"|«[^«»]*»")


def _copied_spans(text: str) -> list[tuple[int, int]]:
    """Where [text] holds a run of words in quotation marks: a line copied from the book.

    A single word or two in quotation marks is the reader's own emphasis and is left alone.
    """
    return [
        found.span()
        for found in _QUOTED.finditer(text)
        if len(found.group(0)[1:-1].split()) >= COPIED_WORDS
    ]


def _page(value: object) -> int | None:
    """A page number from a whole number, however the JSON spelled it. Null for anything else."""
    if isinstance(value, bool):
        return None
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    if isinstance(value, str) and value.strip().isdigit():
        value = int(value.strip())
    return value if isinstance(value, int) and value >= 1 else None


def _clip(text: str, limit: int) -> str:
    """[text] cut at a word boundary to at most [limit] characters, with an ellipsis if cut."""
    if len(text) <= limit:
        return text
    return text[: limit - 1].rsplit(" ", 1)[0].rstrip(" ,;:") + "…"


def _one_line(value: object) -> str:
    return _squash(value) if isinstance(value, str) else ""


def _squash(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _list(value: object) -> list:
    return value if isinstance(value, list) else []


def _dicts(value: object) -> list[dict]:
    return [item for item in _list(value) if isinstance(item, dict)]
