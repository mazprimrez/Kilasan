import json

import pytest

from app import tag

ENTRIES = [
    {"id": 11, "date": "2026-11-16", "text": "Abis gym pagi, terus meeting sama Raka soal launch."},
    {"id": 12, "date": "2026-11-17", "text": "Makan malam sama Ma. Mau ke gym besok."},
    {"id": 13, "date": "2026-11-18", "text": "Quiet day. Read on the train."},
]
VOCABULARY = {"topics": ["Work", "Family"], "habits": ["Gym"], "people": ["Mama", "Raka"]}
REQUEST = {"entries": ENTRIES, "vocabulary": VOCABULARY}

ANSWER = {
    "tags": [
        {"entryId": 11, "topics": ["Work"], "habits": ["Gym"], "people": ["Raka"]},
        {"entryId": 12, "topics": ["Family", "Food"], "habits": [], "people": ["Mama"]},
        {"entryId": 13, "topics": ["Reading"], "habits": ["Reading"], "people": []},
    ]
}


def post(client, owner, body):
    return client.post("/v1/tag", json=body, headers=owner)


def test_tags_come_back_one_item_per_entry_in_the_order_sent(client, fake, owner):
    fake.answer = {"tags": list(reversed(ANSWER["tags"]))}

    response = post(client, owner, REQUEST)

    assert response.status_code == 200
    assert response.json() == ANSWER
    assert len(fake.calls) == 1


def test_the_entries_and_vocabulary_reach_the_model_and_the_prompt_comes_from_its_file(client, fake, owner):
    fake.answer = ANSWER

    post(client, owner, REQUEST)

    sent = json.loads(fake.calls[0].parts[0].text)
    assert sent["vocabulary"] == VOCABULARY
    assert [e["id"] for e in sent["entries"]] == [11, 12, 13]
    assert "Abis gym pagi" in fake.calls[0].parts[0].text
    assert fake.calls[0].config.system_instruction == tag.PROMPT_PATH.read_text(encoding="utf-8")
    assert fake.calls[0].config.response_schema == tag.SCHEMA


def test_the_route_also_answers_without_the_v1_prefix(client, fake, owner):
    fake.answer = ANSWER

    assert client.post("/tag", json=REQUEST, headers=owner).status_code == 200


def test_a_stranger_cannot_tag(client, fake):
    response = client.post("/v1/tag", json=REQUEST, headers={"Authorization": "Bearer stranger-token"})

    assert response.status_code == 403
    assert fake.calls == []


# Name consistency against the vocabulary


def test_names_are_spelled_as_the_vocabulary_spells_them(client, fake, owner):
    fake.answer = {
        "tags": [
            {"entryId": 11, "topics": ["work", " WORK "], "habits": ["gym."], "people": ["raka"]},
            {"entryId": 12, "topics": ["family"], "habits": [], "people": ["mama"]},
        ]
    }

    body = post(client, owner, REQUEST).json()

    assert body["tags"][0] == {"entryId": 11, "topics": ["Work"], "habits": ["Gym"], "people": ["Raka"]}
    assert body["tags"][1]["people"] == ["Mama"]
    assert body["tags"][1]["topics"] == ["Family"]


def test_a_new_name_is_kept_tidied(client, fake, owner):
    fake.answer = {"tags": [{"entryId": 13, "topics": ["  train   rides "], "habits": [], "people": []}]}

    body = post(client, owner, REQUEST).json()

    assert body["tags"] == [{"entryId": 13, "topics": ["Train rides"], "habits": [], "people": []}]


def test_a_person_mapped_to_the_vocabulary_is_kept_but_an_invented_one_is_dropped(client, fake, owner):
    # Entry 12 says "Ma"; mapping it to the known "Mama" is the model's job and is kept.
    # "Budi" is in neither the entry nor the vocabulary.
    fake.answer = {"tags": [{"entryId": 12, "topics": [], "habits": [], "people": ["Mama", "Budi"]}]}

    body = post(client, owner, REQUEST).json()

    assert body["tags"][0]["people"] == ["Mama"]


def test_a_new_person_written_in_the_entry_is_kept(client, fake, owner):
    request = {"entries": [{"id": 1, "date": "2026-11-20", "text": "Ngopi sama Bu Sari."}], "vocabulary": VOCABULARY}
    fake.answer = {"tags": [{"entryId": 1, "topics": [], "habits": [], "people": ["Bu Sari"]}]}

    assert post(client, owner, request).json()["tags"][0]["people"] == ["Bu Sari"]


def test_at_most_three_topics(client, fake, owner):
    fake.answer = {"tags": [{"entryId": 11, "topics": ["Work", "Launch", "Health", "Money"], "habits": [], "people": []}]}

    assert post(client, owner, REQUEST).json()["tags"][0]["topics"] == ["Work", "Launch", "Health"]


def test_entries_that_were_not_sent_are_dropped_and_left_out_ones_are_not_invented(client, fake, owner):
    fake.answer = {
        "tags": [
            {"entryId": 99, "topics": ["Work"], "habits": [], "people": []},
            {"entryId": 11, "topics": ["Work"], "habits": [], "people": []},
            {"entryId": 11, "topics": ["Other"], "habits": [], "people": []},
        ]
    }

    body = post(client, owner, REQUEST).json()

    # 12 and 13 were left out by the model: the phone asks for them again later.
    assert body["tags"] == [{"entryId": 11, "topics": ["Work"], "habits": [], "people": []}]


# Schema validation


def test_an_answer_in_the_wrong_shape_is_asked_for_once_more(client, fake, owner):
    fake.answers = [{"tags": [{"entryId": "eleven", "topics": "Work"}]}, ANSWER]

    response = post(client, owner, REQUEST)

    assert response.status_code == 200
    assert response.json() == ANSWER
    assert len(fake.calls) == 2
    assert "did not match" in fake.calls[1].parts[-1].text


def test_two_answers_in_the_wrong_shape_are_an_error(client, fake, owner):
    fake.answers = [{"nope": 1}, {"tags": "no"}]

    response = post(client, owner, REQUEST)

    assert response.status_code == 502
    assert len(fake.calls) == 2


@pytest.mark.parametrize(
    "body, message",
    [
        ({}, "entries"),
        ({"entries": []}, "entries"),
        ({"entries": [{"id": i, "text": "x"} for i in range(21)]}, "at most 20"),
        ({"entries": [{"id": "1", "text": "x"}]}, "id"),
        ({"entries": [{"id": True, "text": "x"}]}, "id"),
        ({"entries": [{"id": 1, "text": "  "}]}, "no text"),
        ({"entries": [{"id": 1, "text": "a"}, {"id": 1, "text": "b"}]}, "twice"),
        ({"entries": [{"id": 1, "text": "a"}], "vocabulary": {"people": "Mama"}}, "vocabulary.people"),
    ],
)
def test_bad_requests_are_refused_before_the_model_is_asked(client, fake, owner, body, message):
    response = post(client, owner, body)

    assert response.status_code == 400
    assert message in response.json()["error"]
    assert fake.calls == []


def test_vocabulary_is_optional_and_tidied():
    checked = tag.check_request({"entries": [{"id": 1, "text": " a "}], "vocabulary": {"people": ["mama", "Mama", " "]}})

    assert checked["entries"] == [{"id": 1, "date": "", "text": "a"}]
    assert checked["vocabulary"] == {"topics": [], "habits": [], "people": ["Mama"]}
    assert tag.check_request({"entries": [{"id": 1, "text": "a"}]})["vocabulary"] == {"topics": [], "habits": [], "people": []}


def test_the_usage_line_names_the_tag_feature(client, fake, owner, capsys):
    fake.answer = ANSWER

    post(client, owner, REQUEST)

    lines = [json.loads(l) for l in capsys.readouterr().out.splitlines() if l.startswith("{")]
    assert any(l.get("feature") == "tag" for l in lines)
