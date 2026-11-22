"""A photo of a notebook page, or a page written with the pen, to text."""

from . import model

INSTRUCTION = """\
You type out pages for a personal journal. The image is a photo of a handwritten \
notebook page or a page written with a stylus.

- Type what is written, exactly as written, in the language it is written in. The \
writer mostly uses Indonesian mixed with English. Never translate.
- Do not correct spelling or grammar, and do not summarise or describe the page.
- Keep the line breaks and paragraph breaks of the page.
- Leave out anything you cannot read rather than guessing at it.
- If nothing is written on the page, return empty text.
"""

SCHEMA = {
    "type": "OBJECT",
    "properties": {"text": {"type": "STRING"}},
    "required": ["text"],
}


async def extract_text(image: bytes, mime_type: str) -> str:
    """The handwriting or print on [image]. Empty when the page is blank."""
    answer = await model.ask_json(
        feature="extract-text",
        instruction=INSTRUCTION,
        parts=[model.file_part(image, mime_type), model.text_part("Type out this page.")],
        schema=SCHEMA,
    )
    return str(answer.get("text") or "").strip()
