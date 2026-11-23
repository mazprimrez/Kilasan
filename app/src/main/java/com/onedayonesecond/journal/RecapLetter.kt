package com.onedayonesecond.journal

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** Something that ran across days: a person, work, an event, a worry. [days] are the days it showed up. */
data class RecapThread(val title: String, val line: String, val days: List<LocalDate>)

/** A win or something left open, with the day it came from. */
data class DatedNote(val text: String, val date: LocalDate)

/**
 * A person or topic that came up, with the days it came up on and the same count for
 * the period before. [previous] is null when there is no recap of that period in this
 * layout to compare with, so no arrow is drawn; 0 means it is new.
 */
data class Mention(val name: String, val count: Int, val previous: Int? = null) {
    val direction: Direction?
        get() = previous?.let {
            when {
                count > it -> Direction.UP
                count < it -> Direction.DOWN
                else -> Direction.SAME
            }
        }
}

/**
 * The chapters of a recap in layout 2, as stored in `Recap.json`. The words come from
 * the model; every number (the stats, the mention counts, days per place) was worked
 * out on the phone when the recap was written and is kept with it, so "Since last
 * week" says what was true then.
 *
 * ```
 * {
 *   "howYouFelt": {"text": "…", "days": ["2026-11-16", "2026-11-17"]},
 *   "threads": [{"title": "Tech Cantik", "line": "…", "days": ["2026-11-18", "2026-11-21"]}],
 *   "dayByDay": [{"date": "2026-11-16", "text": "…"}],
 *   "wins": [{"text": "…", "date": "2026-11-21"}],
 *   "stillOpen": [{"text": "…", "date": "2026-11-19"}],
 *   "note": "…", "question": "…",
 *   "topPeople": [{"name": "Ibu", "count": 4, "previous": 2}], "topTopics": [],
 *   "places": [{"name": "Jakarta Selatan", "days": 212}],
 *   "stats": {"daysKept": 7, "daysWritten": 6, "words": 2310, "averageMood": 3.6},
 *   "previousStats": null, "hadEarlier": false
 * }
 * ```
 */
data class RecapLetter(
    val howYouFelt: String = "",
    val howYouFeltDays: List<LocalDate> = emptyList(),
    val threads: List<RecapThread> = emptyList(),
    val dayByDay: List<DatedLine> = emptyList(),
    val wins: List<DatedNote> = emptyList(),
    val stillOpen: List<DatedNote> = emptyList(),
    val note: String = "",
    val question: String = "",
    val topPeople: List<Mention> = emptyList(),
    val topTopics: List<Mention> = emptyList(),
    val places: List<PlaceDays> = emptyList(),
    val stats: PeriodStats = PeriodStats(0, 0, 0, null),
    /** The period before, as it was when this was written. Null if it had nothing at all. */
    val previousStats: PeriodStats? = null,
    /** Whether anything was kept or written before this period, which tells a first week from a gap. */
    val hadEarlier: Boolean = false,
) {

    fun encode(): String = JSONObject()
        .put("howYouFelt", JSONObject().put("text", howYouFelt).put("days", dates(howYouFeltDays)))
        .put(
            "threads",
            JSONArray(threads.map { JSONObject().put("title", it.title).put("line", it.line).put("days", dates(it.days)) }),
        )
        .put("dayByDay", JSONArray(dayByDay.map { JSONObject().put("date", it.date.toString()).put("text", it.text) }))
        .put("wins", notes(wins))
        .put("stillOpen", notes(stillOpen))
        .put("note", note)
        .put("question", question)
        .put("topPeople", mentions(topPeople))
        .put("topTopics", mentions(topTopics))
        .put("places", JSONArray(places.map { JSONObject().put("name", it.name).put("days", it.days) }))
        .put("stats", stats.toJson())
        .put("previousStats", previousStats?.toJson() ?: JSONObject.NULL)
        .put("hadEarlier", hadEarlier)
        .toString()

    companion object {

        /** Reads the chapters back. Anything unusable is skipped; a damaged file gives an empty letter. */
        fun decode(json: String): RecapLetter {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return RecapLetter()
            }
            val felt = root.optJSONObject("howYouFelt")
            return RecapLetter(
                howYouFelt = felt?.optString("text")?.trim().orEmpty(),
                howYouFeltDays = felt?.dates("days").orEmpty(),
                threads = root.objects("threads").mapNotNull { thread ->
                    val title = thread.optString("title").trim()
                    val line = thread.optString("line").trim()
                    val days = thread.dates("days")
                    if (title.isEmpty() || line.isEmpty()) null else RecapThread(title, line, days)
                },
                dayByDay = root.objects("dayByDay").mapNotNull { line ->
                    val date = date(line.optString("date"))
                    val text = line.optString("text").trim()
                    if (date == null || text.isEmpty()) null else DatedLine(date, text)
                },
                wins = root.notes("wins"),
                stillOpen = root.notes("stillOpen"),
                note = root.optString("note").trim(),
                question = root.optString("question").trim(),
                topPeople = root.mentions("topPeople"),
                topTopics = root.mentions("topTopics"),
                places = root.objects("places").mapNotNull { place ->
                    val name = place.optString("name").trim()
                    val days = place.optInt("days")
                    if (name.isEmpty() || days <= 0) null else PlaceDays(name, days)
                },
                stats = root.optJSONObject("stats")?.let(::stats) ?: PeriodStats(0, 0, 0, null),
                previousStats = root.optJSONObject("previousStats")?.let(::stats),
                hadEarlier = root.optBoolean("hadEarlier"),
            )
        }

        private fun date(text: String?): LocalDate? = runCatching { LocalDate.parse(text?.trim()) }.getOrNull()

        private fun dates(days: List<LocalDate>) = JSONArray(days.map { it.toString() })

        private fun notes(notes: List<DatedNote>) =
            JSONArray(notes.map { JSONObject().put("text", it.text).put("date", it.date.toString()) })

        private fun mentions(mentions: List<Mention>) = JSONArray(
            mentions.map {
                JSONObject().put("name", it.name).put("count", it.count).put("previous", it.previous ?: JSONObject.NULL)
            },
        )

        private fun PeriodStats.toJson(): JSONObject = JSONObject()
            .put("daysKept", daysKept)
            .put("daysWritten", daysWritten)
            .put("words", words)
            .put("averageMood", averageMood ?: JSONObject.NULL)

        private fun stats(json: JSONObject) = PeriodStats(
            daysKept = json.optInt("daysKept"),
            daysWritten = json.optInt("daysWritten"),
            words = json.optInt("words"),
            averageMood = if (json.isNull("averageMood")) null else json.optDouble("averageMood").takeIf { !it.isNaN() },
        )

        private fun JSONObject.objects(name: String): List<JSONObject> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }

        private fun JSONObject.dates(name: String): List<LocalDate> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { date(array.optString(it)) }.distinct().sorted()
        }

        private fun JSONObject.notes(name: String): List<DatedNote> = objects(name).mapNotNull { note ->
            val text = note.optString("text").trim()
            val date = date(note.optString("date"))
            if (text.isEmpty() || date == null) null else DatedNote(text, date)
        }

        private fun JSONObject.mentions(name: String): List<Mention> = objects(name).mapNotNull { mention ->
            val label = mention.optString("name").trim()
            if (label.isEmpty()) {
                null
            } else {
                Mention(
                    name = label,
                    count = mention.optInt("count").coerceAtLeast(0),
                    previous = if (!mention.has("previous") || mention.isNull("previous")) null else mention.optInt("previous"),
                )
            }
        }
    }
}
