import json

import pytest

from app import recap

from conftest import sample

# What a well-behaved model writes for tests/samples/week.json (9 to 15 November 2026;
# entries on the 9th, 11th, 13th and 14th).
WEEK_ANSWER = sample("week_answer")
WEEK_RESULT = {"schemaVersion": 2, **WEEK_ANSWER}


def post(client, owner, body):
    return client.post("/v1/recap", json=body, headers=owner)


# The request and the prompt


@pytest.mark.parametrize("period", ["week", "month", "year"])
def test_the_entries_reach_the_model_as_they_were_sent(client, fake, owner, period):
    request = sample(period)
    first = request["entries"][0]
    fake.answer = {**WEEK_ANSWER, "quote": {"text": first["text"], "date": first["date"]}}

    response = post(client, owner, request)

    assert response.status_code == 200
    assert json.loads(fake.calls[0].parts[0].text) == request
    # Indonesian and English are sent as written, not as escape codes.
    assert first["text"] in fake.calls[0].parts[0].text


def test_the_prompt_comes_from_its_own_file(client, fake, owner):
    fake.answer = WEEK_ANSWER

    post(client, owner, sample("week"))

    assert recap.PROMPT_PATH.name == "recap.md"
    assert fake.calls[0].config.system_instruction == recap.PROMPT_PATH.read_text(encoding="utf-8")


def test_a_recap_comes_back_in_the_shape_the_app_reads(client, fake, owner):
    fake.answer = WEEK_ANSWER

    response = post(client, owner, sample("week"))

    assert response.status_code == 200
    assert response.json() == WEEK_RESULT
    assert len(fake.calls) == 1


def test_the_recap_also_answers_without_the_v1_prefix(client, fake, owner):
    fake.answer = WEEK_ANSWER

    response = client.post("/recap", json=sample("week"), headers=owner)

    assert response.status_code == 200


# The quote


def test_an_exact_quote_is_kept(client, fake, owner):
    fake.answer = WEEK_ANSWER

    body = post(client, owner, sample("week")).json()

    assert body["quote"] == {"text": "It was the first quiet morning in weeks.", "date": "2026-11-14"}


def test_a_quote_that_differs_only_in_whitespace_is_kept_without_a_retry(client, fake, owner):
    fake.answer = {**WEEK_ANSWER, "quote": {"text": "  “It was the first   quiet\nmorning in weeks.”", "date": "2026-11-14"}}

    body = post(client, owner, sample("week")).json()

    assert body["quote"] == {"text": "It was the first quiet morning in weeks.", "date": "2026-11-14"}
    assert len(fake.calls) == 1


@pytest.mark.parametrize(
    "quote",
    [
        {"text": "It was the first calm morning in weeks.", "date": "2026-11-14"},  # a word changed
        {"text": "it was the first quiet morning in weeks.", "date": "2026-11-14"},  # case changed
        {"text": "Itu pagi tenang pertama dalam beberapa minggu.", "date": "2026-11-14"},  # translated
        {"text": "It was the first quiet morning in weeks.", "date": "2026-11-13"},  # the wrong day
        {"text": "", "date": ""},  # missing
    ],
)
def test_a_quote_that_is_not_word_for_word_is_asked_for_again_with_the_reason(client, fake, owner, quote):
    fake.answers = [{**WEEK_ANSWER, "quote": quote}, WEEK_ANSWER]

    body = post(client, owner, sample("week")).json()

    assert len(fake.calls) == 2
    assert "word for word" in fake.calls[1].parts[-1].text
    assert body["quote"] == WEEK_ANSWER["quote"]


def test_a_quote_still_wrong_after_the_retry_is_picked_from_the_entries(client, fake, owner):
    wrong = {**WEEK_ANSWER, "quote": {"text": "Something nobody wrote.", "date": "2026-11-14"}}
    fake.answers = [wrong, wrong]

    body = post(client, owner, sample("week")).json()

    assert len(fake.calls) == 2
    entries = {(e["date"], e["text"]) for e in sample("week")["entries"]}
    assert body["quote"]["text"]
    assert any(date == body["quote"]["date"] and body["quote"]["text"] in text for date, text in entries)


def test_the_picked_quote_is_a_whole_sentence():
    texts = {"2026-11-09": ["Pendek.", "Kalimat pertama yang cukup panjang. Kalimat kedua juga ada di sini."]}

    assert recap.fallback_quote(texts) == ("Kalimat pertama yang cukup panjang.", "2026-11-09")


# Dates


def test_days_outside_the_period_or_without_entries_are_dropped(client, fake, owner):
    fake.answer = {
        **WEEK_ANSWER,
        "howYouFelt": {"text": "Steady.", "days": ["2026-11-09", "2026-11-10", "2026-12-01"]},
        "threads": [
            # The 10th has no entries and the 16th is the next week: only the 9th is kept.
            {"title": "Kerjaan", "line": "Deadline.", "days": ["2026-11-09", "2026-11-10", "2026-11-16"]},
            # Nothing real is left of this one, so it goes.
            {"title": "Invented", "line": "Never happened.", "days": ["2026-11-10", "not a date"]},
        ],
        "dayByDay": [{"date": "2026-11-09", "line": "Meeting."}, {"date": "2026-11-12", "line": "Nothing written."}],
        "wins": [{"text": "Real.", "date": "2026-11-14"}, {"text": "Next week.", "date": "2026-11-16"}],
        "stillOpen": [{"text": "Before the week.", "date": "2026-11-08"}],
    }

    body = post(client, owner, sample("week")).json()

    assert body["howYouFelt"]["days"] == ["2026-11-09"]
    assert body["threads"] == [{"title": "Kerjaan", "line": "Deadline.", "days": ["2026-11-09"]}]
    assert body["dayByDay"] == [{"date": "2026-11-09", "line": "Meeting."}]
    assert body["wins"] == [{"text": "Real.", "date": "2026-11-14"}]
    assert body["stillOpen"] == []


def test_lists_are_cut_to_what_the_page_has_room_for(client, fake, owner):
    thread = {"title": "T", "line": "L", "days": ["2026-11-09"]}
    win = {"text": "W", "date": "2026-11-09"}
    fake.answer = {**WEEK_ANSWER, "threads": [thread] * 6, "wins": [win] * 5, "stillOpen": [win] * 7}

    body = post(client, owner, sample("week")).json()

    assert len(body["threads"]) == recap.MAX_THREADS
    assert len(body["wins"]) == recap.MAX_WINS
    assert len(body["stillOpen"]) == recap.MAX_OPEN


def test_people_and_topics_only_come_with_a_month_or_a_year(client, fake, owner):
    named = {**WEEK_ANSWER, "topPeople": [{"name": "Ibu", "count": 2}], "topTopics": [{"name": "Kerjaan", "count": 3}]}
    fake.answer = named
    assert post(client, owner, sample("week")).json()["topPeople"] == []

    month = sample("month")
    first = month["entries"][0]
    fake.answer = {**named, "quote": {"text": first["text"], "date": first["date"]}}
    body = post(client, owner, month).json()
    assert body["topPeople"] == [{"name": "Ibu", "count": 2}]
    assert body["topTopics"] == [{"name": "Kerjaan", "count": 3}]


def test_a_year_has_no_day_by_day(client, fake, owner):
    year = sample("year")
    first = year["entries"][0]
    fake.answer = {
        **WEEK_ANSWER,
        "quote": {"text": first["text"], "date": first["date"]},
        "dayByDay": [{"date": first["date"], "line": "Something."}],
    }

    assert post(client, owner, year).json()["dayByDay"] == []


# The shape


def test_an_answer_that_does_not_parse_is_asked_for_again_once(client, fake, owner):
    broken = {key: value for key, value in WEEK_ANSWER.items() if key != "question"}
    fake.answers = [broken, WEEK_ANSWER]

    response = post(client, owner, sample("week"))

    assert response.status_code == 200
    assert len(fake.calls) == 2
    assert "question" in fake.calls[1].parts[-1].text
    assert response.json() == WEEK_RESULT


@pytest.mark.parametrize("missing", ["title", "note", "question"])
def test_an_answer_still_broken_after_the_retry_is_an_error_the_app_retries(client, fake, owner, missing):
    fake.answer = {**WEEK_ANSWER, missing: "  "}

    response = post(client, owner, sample("week"))

    assert response.status_code == 502
    assert response.json()["error"]
    assert len(fake.calls) == 2


def test_a_schema_retry_and_a_quote_retry_each_happen_once_at_most(client, fake, owner):
    broken = {key: value for key, value in WEEK_ANSWER.items() if key != "note"}
    wrong_quote = {**WEEK_ANSWER, "quote": {"text": "Not in the entries at all.", "date": "2026-11-14"}}
    fake.answers = [broken, wrong_quote, wrong_quote]

    response = post(client, owner, sample("week"))

    assert response.status_code == 200
    assert len(fake.calls) == 3


@pytest.mark.parametrize(
    "body",
    [
        b"not json",
        b"[]",
        json.dumps({**sample("week"), "period": "decade"}).encode(),
        json.dumps({**sample("week"), "entries": []}).encode(),
        json.dumps({**sample("week"), "entries": "all of them"}).encode(),
        json.dumps({**sample("week"), "start": "yesterday"}).encode(),
        json.dumps({**sample("week"), "start": "2026-11-15", "end": "2026-11-09"}).encode(),
    ],
)
def test_a_request_that_is_not_a_period_with_entries_is_refused(client, fake, owner, body):
    response = client.post("/v1/recap", content=body, headers={**owner, "Content-Type": "application/json"})

    assert response.status_code == 400
    assert fake.calls == []
