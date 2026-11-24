# The journal's AI backend

Four things in the journal need an AI model: writing out voice notes, typing out
notebook photos and pen pages, writing the weekly, monthly and yearly recaps, and
writing a book's summary and review ideas from the thoughts logged while reading it.
The design names the model (Gemini Flash-Lite) and says it is reached **through a
backend**, so nothing that can reach the model ships inside the app.

The backend is in [`backend/`](../backend/README.md): a FastAPI service for Cloud Run
that calls Gemini through Vertex AI as its own service account. How to test and
deploy it is in its README. Until a backend's address is set in the app, the app says
so in plain words (see "Without a backend") and everything else works.

## The model

All four endpoints use one model, named by one setting on the backend: the
environment variable `GEMINI_MODEL`, which defaults to `gemini-3.5-flash-lite`.
Moving to a newer model is changing that value; the command is in the backend's
README.

## Pointing the app at it

In `local.properties` (not committed):

```
journal.backend.url=https://your-backend.example.com
```

It ends up in `BuildConfig.JOURNAL_BACKEND_URL`. Nothing secret is built into the app.

## Who may call it

Only one Google account, named in the backend's `ALLOWED_EMAIL`. Every request
carries `Authorization: Bearer <token>`, where the token is one of:

- a **Google access token**, which is what the app sends. The backend asks Google
  whom it belongs to and which app it was given to, and lets it through only if it
  was given to this app (`GOOGLE_CLIENT_ID`, the app's Android OAuth client), belongs
  to the allowed account with a verified address, and has not expired;
- a **Google ID token** for the same client and account, checked against Google's
  public keys.

Anyone else gets 401 (no token Google vouches for) or 403 (another account).

The app gets its token in `ai/BackendAuth.kt`, the same way it gets one for Drive
(`backup/DriveAuth.kt`), asking only for the account's email address. The first time,
Google has to ask: the app shows "Typing out and letters need to confirm your Google
account once." with an **Allow** button. After that a token comes back with no screen,
which is what lets the nightly recap job run with the app closed. If the backend
refuses a token (they last an hour), the app asks Google for a fresh one and tries
once more.

Why an access token and not an ID token: on a real phone, the calls that return an ID
token without showing a screen either were refused by Google or belong to its
deprecated sign-in library, and the nightly job cannot show a screen. The Android
OAuth client is the one made for Drive backup (docs/DRIVE_BACKUP.md); no other client
is needed.

The client is `ai/BackendJournalAi.kt`, behind the `JournalAi` interface. Swapping in
a different service means writing another implementation of that interface and
changing one line in `AppContainer`.

## The contract

All four are `POST`. A reply outside 200–299 is an error; its body may be
`{"error": "…"}`. The backend also answers each path without the `/v1` prefix.

The app treats 408, 429 and 5xx as "try again later" (the entry waits and is retried
by itself), and any other error as "failed" (the user gets a "Try again" button).
The backend chooses its statuses with that in mind:

| Status | When |
| --- | --- |
| 400 | An empty request, a recap request that is not a period with days, or a book summary request with fewer than three thoughts |
| 401, 403 | No Google token the backend accepts, or a token for another account |
| 413 | Over 10 MB of audio or image, or 2 MB of recap or book summary request |
| 415 | Not audio for `/transcribe`; not JPEG or PNG for `/extract-text` |
| 422 | The model could not read this file, or declined to |
| 429 | The model is busy |
| 500 | The model name or the service account's access is wrong. Retried by the app, so entries recover once the setting is fixed |
| 502 | The model's answer could not be used (cut short, not the agreed shape, a recap with no title or note, a book summary with no title, summary or points) |
| 503 | The model could not be reached |

### `POST /v1/transcribe`

Body: the voice note, `Content-Type: audio/mp4` (AAC, mono, 64 kbit/s, at most 5
minutes, so about 2.4 MB).

Reply: `{"text": "What was said, as plain text."}`. Empty text is a valid answer.

The transcript is verbatim. The speaker mostly mixes Indonesian and English, and the
model is told to write each word in the language it was spoken in and never to
translate. Who does the transcribing is behind one function,
`transcribe(audio, mime_type)` in `backend/app/transcription.py`; using another
provider means rewriting that function only.

### `POST /v1/extract-text`

Body: one image, `Content-Type: image/jpeg` (a photo, longest side 2048 px) or
`image/png` (a pen page).

Reply: `{"text": "The handwriting or print on the page."}`. Line breaks are kept.
Empty text means nothing was written on the page.

### `POST /v1/recap`

Recap layout 2 (`schemaVersion: 2`). Body, `application/json`:

```json
{
  "schemaVersion": 2,
  "period": "week",
  "key": "week-2026-11-16",
  "start": "2026-11-16",
  "end": "2026-11-22",
  "name": "Eza",
  "language": "en-ID",
  "entries": [
    {
      "date": "2026-11-21",
      "weekday": "Saturday",
      "time": "16:50",
      "type": "WRITTEN",
      "text": "Akhirnya ngomong di Tech Cantik! Tangan gemeteran di 2 menit pertama, terus lancar.",
      "place": "Kemang, Jakarta Selatan"
    }
  ],
  "days": [
    {"date": "2026-11-21", "weekday": "Saturday", "mood": "GREAT", "place": "Kemang, Jakarta Selatan", "caption": "Tech Cantik!"}
  ],
  "stats": {"daysKept": 7, "daysWritten": 7, "words": 412, "averageMood": 3.9},
  "previousStats": {"daysKept": 4, "daysWritten": 4, "words": 70, "averageMood": 3.5},
  "previousThreads": [{"title": "Deadline deck", "line": "Dimajuin Monday, ke-submit Wednesday."}]
}
```

- `period` is `week`, `month` or `year`; `start` and `end` are the period's first and
  last day.
- `entries` are every entry of the period, oldest first. `type` is `WRITTEN`, `VOICE`,
  `NOTEBOOK_PHOTO`, `PEN` or `PHOTO`; `text` is the written text, transcript or
  typed-out page (empty for a photo), cut to 4000 characters for a week, 1500 for a
  month and 400 for a year. `place` may be `null`.
- `days` lists only days that have something: the mood (`ROUGH` … `GREAT` or `null`),
  place and the second's caption.
- `stats` and `previousStats` are counted on the phone and only give the model
  context; the recap's numbers are the phone's (see "What the app checks").
  `previousStats` is `null` when there was nothing in the period before.
- `previousThreads` are the threads of the previous period's recap if it is in
  layout 2, so "On your mind" can say what carried over.

Reply:

```json
{
  "schemaVersion": 2,
  "title": "Minggu Tech Cantik",
  "quote": {"text": "Akhirnya ngomong di Tech Cantik!", "date": "2026-11-21"},
  "howYouFelt": {"text": "Dua hari gym (Mon, Tue) jadi mood terbaik; Wednesday, after 4 hours of sleep, the lowest.",
                 "days": ["2026-11-16", "2026-11-17", "2026-11-18"]},
  "threads": [{"title": "Tech Cantik", "line": "Prepared Wed, rehearsed Thu, spoke Sat, wrote about it Sun.",
               "days": ["2026-11-18", "2026-11-19", "2026-11-21", "2026-11-22"]}],
  "dayByDay": [{"date": "2026-11-16", "line": "Gym pagi, refactor payment module."}],
  "wins": [{"text": "Spoke at Tech Cantik.", "date": "2026-11-21"}],
  "stillOpen": [{"text": "Review PR Raka belum dikerjain.", "date": "2026-11-19"}],
  "note": "One short paragraph, without a greeting.",
  "question": "You wrote about comfort vs adrenaline. Which one were you looking for this week?",
  "summary": "The week opened with gym on Monday and a deadlift PR on Tuesday. ... You gave the talk on Saturday."
}
```

`summary` is one paragraph of what happened, in time order, naming concrete days,
people and events, with no numbers, advice or judgement (3–6 sentences for a week, up
to 8 for a month or year); Patterns shows it under "The week, in short". Which people
and topics came up is counted on the phone from `/tag` (the recap used to return
`topPeople`/`topTopics`; it no longer does, and the app ignores them in old letters).
`dayByDay` is empty for a year. Before it answers, the backend
(`backend/app/recap.py`):

- **parses** the model's answer with a Pydantic model. If it does not parse (or
  `title`, `note`, `question` or `summary` is blank) it asks once more with the errors fed back,
  then answers 502;
- **checks the quote**: it must be an exact, continuous piece of one entry's text on
  `quote.date` (only whitespace may differ, and quotation marks around it are
  ignored). If not, it asks once more with that error fed back; if the quote is still
  wrong, it picks a sentence from the entries itself;
- **checks every date**: a day in `howYouFelt.days`, a thread's `days`, `dayByDay`,
  `wins` and `stillOpen` must be inside the period and a day with entries. Days that
  are not are removed; a thread with no day left, and a win or open item with a bad
  date, is dropped;
- cuts threads to 4, wins to 3, open items to 4.

So one recap is at most three model calls.

### `POST /v1/tag`

Gives entries the topics, habits and people that Patterns counts. Body:

```json
{
  "entries": [
    {"id": 41, "date": "2026-11-17", "text": "Gym lagi, PR deadlift 80kg! Nggak nyangka bisa."},
    {"id": 42, "date": "2026-11-17", "text": "Standup tadi Raka minta review PR checkout sebelum Jumat."}
  ],
  "vocabulary": {"topics": ["Work", "Public speaking"], "habits": ["Gym"], "people": ["Raka", "Ibu"]}
}
```

- 1–20 entries, each with a unique integer `id` and non-blank `text` (cut to 4000
  characters). `vocabulary` lists the names already in use, most used first (up to
  300 per kind), so the same thing keeps one spelling. Anything else is a 400.

Reply:

```json
{"tags": [
  {"entryId": 41, "topics": ["Fitness"], "habits": ["Gym"], "people": []},
  {"entryId": 42, "topics": ["Work"], "habits": [], "people": ["Raka"]}
]}
```

Before it answers, the backend (`backend/app/tag.py`, prompt
[`backend/prompts/tag.md`](../backend/prompts/tag.md)):

- parses the answer with Pydantic; one retry with the errors fed back, then 502;
- keeps only ids it was sent (the first answer per id), in the order sent; an entry
  the model left out is simply missing, and the app asks again later;
- tidies names (spaces squashed, punctuation trimmed at the ends, first letter
  capital, at most 40 characters), spells them as the vocabulary does when they match
  ignoring case, spaces, `-` and `_`, removes repeats, and caps topics at 3, habits
  at 3, people at 8;
- keeps a person only if the name appears in the entry as a whole word or is in the
  vocabulary (so the model cannot invent one).

Topics are short English nouns; habits only things actually done (not plans); people
are names as written, not groups or public figures. An empty list is a fine answer:
the app stores an entry tagged with nothing as tagged, so it is not sent again. One
call per request; the usage log line has `feature: "tag"`.

### The recap prompt

The prompt is a file of its own, [`backend/prompts/recap.md`](../backend/prompts/recap.md),
so it can be tuned without touching code. Its rules: show what reading day by day
does not (patterns, threads, things left open), be specific over generic, invent
nothing, no diagnosis, no to-dos, and write in plain English even though the entries
mix Indonesian and English (the quote stays word for word, untranslated). "How you felt" only states a pattern that holds on two or more days,
or says the period was steady. The note must mention at least two specific things
from the period and may name a pattern the writer might not want to see; lines that
would fit anyone's week ("it's okay to be tired") are ruled out. The question is open and
built from something they wrote. The quote is never translated.

### `POST /v1/book-recap`

The "Summary and review ideas" page of a book. Body, `application/json`:

```json
{
  "language": "en-ID",
  "book": {
    "title": "Rumah di Ujung Musim",
    "author": "Sekar Wulandari",
    "totalPages": 379,
    "currentPage": 215,
    "status": "READING"
  },
  "thoughts": [
    {"page": 214, "date": "2026-11-14", "text": "I think this book is less about leaving than about who stays."}
  ],
  "otherBooks": [{"title": "Pulang ke Timur", "author": "Raka Mahendra"}]
}
```

- `thoughts` are the reader's own notes, lowest page first: what they wrote, or the
  transcript of a voice note. A photo of a page is never sent and never typed out, so
  the book's own text does not reach the model.
- At least **three** thoughts with words and a page. Fewer is a 400, and the model is
  not asked.
- `otherBooks` are the other books finished this year; may be empty. `author` and
  `totalPages` may be `null`. `status` is `READING` or `FINISHED`.

Reply:

```json
{
  "title": "Pembaca yang awalnya ragu lalu pelan-pelan jatuh hati",
  "quote": "I think this book is less about leaving than about who stays.",
  "quotePage": 214,
  "summary": "One short paragraph on how the reader's thoughts changed.",
  "points": [
    {"title": "Siapa yang tinggal", "line": "One line on what they noticed.", "pages": [214], "fromNotes": true},
    {"title": "Dibanding Pulang ke Timur", "line": "One line.", "pages": [], "fromNotes": false}
  ],
  "gap": "Who would you recommend it to?"
}
```

`title`, `summary` and at least one point are required; without them the answer is a
502, which the app retries. `quote` may be empty, and then `quotePage` is `null`.

### The book summary prompt

[`backend/prompts/book_recap.md`](../backend/prompts/book_recap.md). Two rules come
before everything else in it: **never quote the book**, and **never write the review**.
The model points at what the reader noticed; the writing is theirs. It asks for a
one-line title on how they read the book, their strongest line word for word, a short
paragraph on how their thoughts changed, four or five points with the pages they come
from, and one thing the notes do not cover yet.

The backend does not leave those rules to the prompt alone:

- **The quote is the reader's.** Words that are not in the thoughts are dropped, the
  quote comes back in the reader's own spelling, and the page is corrected to the
  thought it is really from.
- **Never the book.** A thought sometimes copies out a line of the book. Three or more
  words inside quotation marks are taken to be such a line, and a quote that is one, or
  runs into one, is dropped. A single word or two in quotation marks is the reader's
  own emphasis and is left alone.
- **Never the review.** A point is a title and one line: line breaks are removed and
  the line is cut at 220 characters. The summary is cut at 900. At most five points.
- **Points say where they come from.** Pages that have no thought are removed. A point
  left with no pages, or one the model marked as its own suggestion, comes back with
  `fromNotes: false` and no pages, and the app shows it unticked.

## What the app checks for itself

- **The quote**, a second time. The app looks for the backend's quote in that
  period's entries (ignoring case, spacing and quotation marks). If it is not there,
  the app takes a sentence from the entries itself. A recap never shows words the
  user did not write. (`JournalMath.verifyQuote`.)
- **Every number.** "Since last week" is worked out on the phone from its own counts
  (`SinceLast`): "went up from 3 to 7", "went down from 7 to 5", "stayed at 7", and no
  comparison at all when the period before had no days kept ("Your first week in
  Kilasan." with this period's totals). Average mood is a face and a word ("mostly
  good"), never a decimal. The counts are stored with the recap when it is written.
- **People's counts** are recounted on the phone: the days whose entries contain the
  name as a whole word. A topic keeps the model's count. The arrow compares with the
  previous period's recap.
- **Places.** "Where you were" on the year recap is counted on the phone from each
  day's place. Anything the model says about places is dropped.
- **Enough to write from.** No request is sent for a period with fewer than three
  days that have an entry (the same for a week, a month and a year).

For a book's summary the phone does the same checks the backend does, and does not
take the backend's word for them (`books/BookSummaryContent.kt`, `BookSummaryTest`):

- **Only the reader's words are sent.** Thoughts with words go, lowest page first.
  A photo of a page is never sent, so the book's own text does not leave the phone.
- **The quote is the reader's, or there is none.** The quote must be found in one
  of the thoughts. It is shown as the reader spelled it, with the page it is really
  on. Three or more words inside quotation marks in a thought are taken to be a
  line copied out of the book, and a quote that touches one is not used. If the
  model's quote cannot be used, the phone takes a sentence of the reader's own; if
  there is none, the page has no quote.
- **Points name only pages that have notes.** A point left with no such page is
  shown as "A suggestion, not from your notes" and starts unticked. At most five.
- **Enough to write from.** No request is sent for a book with fewer than three
  thoughts that have words.

## When book summaries are written

Opening "Summary and review ideas" writes the summary if there is none, or if the
number of thoughts with words has changed since it was written (a thought added, or
one deleted that it may be quoting). Otherwise the kept one in `book_summaries` is
shown and nothing is sent. Ticking and unticking points is saved with it. If the
phone is offline, a job waits for a connection and writes it then.

"Start my review" does not ask the model for anything. It opens today's write screen
with the book's name and the ticked points' titles as starting lines, and what is
written is saved as an ordinary diary entry.

## When recaps are written

A WorkManager job runs each night a little after 21:00 and writes whichever of these
is due and missing (`RecapRules`):

| Recap | Due |
| --- | --- |
| Week (Monday to Sunday) | Sunday, 21:00 |
| Month | the 1st of the next month |
| Year | 31 December, 21:00 |

It also catches up the newest one of each kind after a night the phone was off, and
replaces a recap that was written before its period was due (the year so far, see
below). If the phone is offline that night the failure is recorded (the recap page
then says "Your phone was offline on Sunday night. Your entries are safe.") and a
second job waits for a connection. "Write the letter now" on a recap page does the
same by hand.

**Write this recap again.** From the ⋯ menu on a recap. The old letter stays on screen
until the new one is saved, and is kept (one version, `Recap.previousJson`) so Undo
can put it back. If it fails, the old letter stays and the page says why.

**Past weeks and months** (Settings → Recaps). Lists the due periods with enough
entries and no recap, the recaps in the old layout (opt-in), and this year so far
(opt-in), then writes them oldest first as a chain of one-period WorkManager jobs, 5
seconds apart, each waiting for a connection and retrying with backoff
(`work/RecapBackfillWorker.kt`). Progress shows in Settings and in a notification
with Stop; stopping keeps what was written.

## Without a backend

With `journal.backend.url` empty, which is the default:

- Voice notes, notebook photos and pen pages are saved and can be played and viewed.
  In place of their text the day page says typing out is not set up in this build.
  The words can be typed by hand from the entry's options.
- Recap pages show the days, moods and "Play this week", and say that letters cannot
  be written yet.
- A book's "Summary and review ideas" page says summaries are not set up in this
  build. Books, thoughts and search do not need the backend.
- No request of any kind is made.

## Privacy

Everything else in the journal stays on the phone (and in the user's own Drive, if
they back up). These four calls are the exception: voice notes, photos the user
marks as notebook pages, pen pages, the text of entries for a recap, and the thoughts
logged on a book for its summary are sent to the backend, and from there to the model (Gemini on Vertex AI, in the Google Cloud
project the backend is deployed to). The backend keeps nothing: it does not store or
log what it is sent, only that a request came and how it ended. Ordinary photos are
never sent. The "It's a notebook page" switch says so where the choice is made.
