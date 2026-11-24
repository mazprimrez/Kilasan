"""Entries to tags: the topics, habits and people each entry is about.

This is the only thing the model does for Patterns. Every number on the Patterns
screen is counted on the phone from these tags; the model never counts anything.

The answer is held to what was asked before it reaches the phone: only the entries
that were sent, at most three topics each, every name spelled the way it already is
in the user's vocabulary when it is the same word, and no person who is neither in
the entry nor in the vocabulary.
"""

import json
import logging
import re
from pathlib import Path

from pydantic import BaseModel, ValidationError

from . import model
from .errors import ApiError

log = logging.getLogger(__name__)

PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "tag.md"

MAX_ENTRIES = 20
MAX_TEXT = 4000
MAX_VOCABULARY = 300
MAX_TOPICS = 3
MAX_HABITS = 3
MAX_PEOPLE = 8
MAX_TAG_LENGTH = 40
KINDS = ("topics", "habits", "people")


class EntryTags(BaseModel):
    entryId: int
    topics: list[str]
    habits: list[str]
    people: list[str]


class TagAnswer(BaseModel):
    tags: list[EntryTags]


_STRS = {"type": "ARRAY", "items": {"type": "STRING"}}
SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "tags": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {"entryId": {"type": "INTEGER"}, "topics": _STRS, "habits": _STRS, "people": _STRS},
                "required": ["entryId", "topics", "habits", "people"],
            },
        }
    },
    "required": ["tags"],
}


def check_request(request: object) -> dict:
    """Raises a 400 unless [request] is 1 to 20 entries with text, and a vocabulary if any.

    Returns the request tidied: texts cut to MAX_TEXT, vocabulary as lists of names.
    """
    if not isinstance(request, dict):
        raise ApiError(400, "The tag request must be a JSON object.")
    entries = request.get("entries")
    if not isinstance(entries, list) or not entries:
        raise ApiError(400, "entries must list at least one entry.")
    if len(entries) > MAX_ENTRIES:
        raise ApiError(400, f"Send at most {MAX_ENTRIES} entries at a time.")
    tidy_entries = []
    seen: set[int] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ApiError(400, "Each entry must be a JSON object.")
        entry_id, text = entry.get("id"), entry.get("text")
        if not isinstance(entry_id, int) or isinstance(entry_id, bool):
            raise ApiError(400, "Each entry needs a whole-number id.")
        if entry_id in seen:
            raise ApiError(400, f"Entry {entry_id} was sent twice.")
        if not isinstance(text, str) or not text.strip():
            raise ApiError(400, f"Entry {entry_id} has no text to tag.")
        seen.add(entry_id)
        tidy_entries.append(
            {"id": entry_id, "date": str(entry.get("date") or ""), "text": text.strip()[:MAX_TEXT]}
        )
    vocabulary = request.get("vocabulary") or {}
    if not isinstance(vocabulary, dict):
        raise ApiError(400, "vocabulary must be an object of topics, habits and people.")
    tidy_vocabulary = {}
    for kind in KINDS:
        names = vocabulary.get(kind) or []
        if not isinstance(names, list) or not all(isinstance(n, str) for n in names):
            raise ApiError(400, f"vocabulary.{kind} must be a list of names.")
        tidy_vocabulary[kind] = _unique_names(names)[:MAX_VOCABULARY]
    return {"entries": tidy_entries, "vocabulary": tidy_vocabulary}


async def tag_entries(request: dict) -> dict:
    """The tags for every entry in [request] the model answered for: `{"tags": [...]}`.

    One retry if the answer does not match the schema. An entry the model left out is
    left out of the answer too, so the phone asks for it again later rather than
    recording it as having no tags.
    """
    instruction = PROMPT_PATH.read_text(encoding="utf-8")
    document = json.dumps(request, ensure_ascii=False)
    feedback: list[str] = []
    retries = 1
    while True:
        parts = [model.text_part(document)] + [model.text_part(f) for f in feedback]
        raw = await model.ask_json(feature="tag", instruction=instruction, parts=parts, schema=SCHEMA)
        try:
            answer = TagAnswer.model_validate(raw)
        except ValidationError as e:
            if retries == 0:
                raise ApiError(502, "The model sent tags that could not be read. Try again later.") from e
            retries -= 1
            problems = "; ".join(f"{'.'.join(map(str, x['loc']))}: {x['msg']}" for x in e.errors()[:8])
            feedback.append(
                f"Your previous answer did not match the required JSON shape ({problems}). "
                "Answer again with one item per entry id, each with topics, habits and people lists."
            )
            continue
        return {"tags": shape(answer, request)}


def shape(answer: TagAnswer, request: dict) -> list[dict]:
    """The model's tags, held to the rules. One item per entry that was sent and answered, in the order sent."""
    texts = {entry["id"]: entry["text"] for entry in request["entries"]}
    vocabulary = request["vocabulary"]
    by_id: dict[int, EntryTags] = {}
    for item in answer.tags:
        if item.entryId in texts and item.entryId not in by_id:
            by_id[item.entryId] = item
    out = []
    for entry in request["entries"]:
        item = by_id.get(entry["id"])
        if item is None:
            continue
        text = texts[entry["id"]]
        people = [
            name
            for name in _snap(item.people, vocabulary["people"])
            if _in_text(name, text) or _known(name, vocabulary["people"])
        ]
        out.append(
            {
                "entryId": entry["id"],
                "topics": _snap(item.topics, vocabulary["topics"])[:MAX_TOPICS],
                "habits": _snap(item.habits, vocabulary["habits"])[:MAX_HABITS],
                "people": people[:MAX_PEOPLE],
            }
        )
    return out


def normalize(name: str) -> str:
    """One space between words, no stray punctuation at the ends, first letter capital, at most 40 characters."""
    tidy = re.sub(r"\s+", " ", name).strip().strip(".,;:!?\"'“”‘’()[]#")
    if not tidy:
        return ""
    return (tidy[0].upper() + tidy[1:])[:MAX_TAG_LENGTH].rstrip()


def _key(name: str) -> str:
    return re.sub(r"[\s\-_]+", " ", name).strip().casefold()


def _snap(names: list[str], vocabulary: list[str]) -> list[str]:
    """Each name tidied, spelled as the vocabulary spells it when it is the same word, duplicates dropped."""
    known = {_key(v): v for v in vocabulary}
    out: list[str] = []
    seen: set[str] = set()
    for name in names:
        tidy = normalize(name)
        if not tidy:
            continue
        spelled = known.get(_key(tidy), tidy)
        if _key(spelled) not in seen:
            seen.add(_key(spelled))
            out.append(spelled)
    return out


def _known(name: str, vocabulary: list[str]) -> bool:
    return _key(name) in {_key(v) for v in vocabulary}


def _in_text(name: str, text: str) -> bool:
    """Whether [name] is in [text] as a whole word or words, ignoring case."""
    pattern = r"(?<![\w])" + re.escape(name) + r"(?![\w])"
    return re.search(pattern, text, flags=re.IGNORECASE) is not None


def _unique_names(names: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for name in names:
        tidy = normalize(name)
        if tidy and _key(tidy) not in seen:
            seen.add(_key(tidy))
            out.append(tidy)
    return out
