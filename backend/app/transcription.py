"""Voice note to text.

`transcribe` is the only thing the rest of the service knows about transcription. To use
another provider, rewrite the body of that one function; nothing else has to change.
"""

from . import model

INSTRUCTION = """\
You transcribe voice notes for a personal journal.

- The speaker mostly speaks Indonesian mixed with English. Write every word in the \
language it was spoken in, switching wherever the speaker switches. Never translate, \
in either direction. Other languages may appear; write them as spoken too.
- Verbatim: keep the words exactly as said, including informal words, slang, fillers \
and repetitions. Do not correct grammar, tidy, shorten, summarise or add anything.
- Add punctuation and sentence breaks so it reads naturally.
- No timestamps, no speaker labels, and no notes such as [music] or [laughs].
- If there is no speech, return empty text.
"""

SCHEMA = {
    "type": "OBJECT",
    "properties": {"text": {"type": "STRING"}},
    "required": ["text"],
}

# The app records AAC in an MP4 container and calls it audio/mp4. Gemini lists the same
# thing as audio/m4a.
_M4A_NAMES = {"audio/mp4", "audio/x-m4a", "audio/mp4a-latm"}


async def transcribe(audio: bytes, mime_type: str) -> str:
    """What was said in [audio], word for word. Empty when nothing was said."""
    if mime_type in _M4A_NAMES:
        mime_type = "audio/m4a"
    answer = await model.ask_json(
        feature="transcribe",
        instruction=INSTRUCTION,
        parts=[model.file_part(audio, mime_type), model.text_part("Transcribe this voice note.")],
        schema=SCHEMA,
    )
    return str(answer.get("text") or "").strip()
