package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * What is sent to the backend to have a recap written (layout 2), and how its answer
 * is read. Pure JSON in and out, so both directions can be unit-tested on a plain JVM.
 * The shape is documented in docs/JOURNAL_BACKEND.md.
 */
object RecapRequest {

    const val SCHEMA_VERSION = 2

    /**
     * Entries are cut to this many characters each before they are sent. A year is a
     * lot of text; the recap needs the gist of each day, not every word.
     */
    fun maxEntryChars(period: RecapPeriod): Int = when (period) {
        is RecapPeriod.Week -> 4000
        is RecapPeriod.Month -> 1500
        is RecapPeriod.Year -> 400
    }

    /**
     * @param entries every entry of the period.
     * @param days every day of the period that has anything at all: its mood, place and caption.
     * @param previous the numbers of the period before, or null if there was nothing then.
     * @param previousThreads what ran across the period before, from its recap, so the
     * model can say what carried over.
     * @param name what the letter should call the user; blank leaves it to the model.
     */
    fun build(
        period: RecapPeriod,
        entries: List<Entry>,
        days: List<DaySummary>,
        previous: PeriodStats?,
        previousThreads: List<RecapThread>,
        name: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): JSONObject {
        val limit = maxEntryChars(period)
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("period", period.noun)
            .put("key", period.key)
            .put("start", period.start.toString())
            .put("end", period.end.toString())
            .put("name", name.trim())
            .put("language", Locale.getDefault().toLanguageTag())
            .put(
                "entries",
                JSONArray(
                    entries.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map { entry ->
                        JSONObject()
                            .put("date", entry.date.toString())
                            .put("weekday", weekday(entry.date))
                            .put("time", TIME.format(Instant.ofEpochMilli(entry.createdAt).atZone(zone)))
                            .put("type", entry.type.name)
                            .put("text", entry.text?.trim().orEmpty().take(limit))
                            .put("place", entry.placeName ?: JSONObject.NULL)
                    },
                ),
            )
            .put(
                "days",
                JSONArray(
                    days.sortedBy { it.date }.map { day ->
                        JSONObject()
                            .put("date", day.date.toString())
                            .put("weekday", weekday(day.date))
                            .put("mood", day.mood?.name ?: JSONObject.NULL)
                            .put("place", day.place ?: JSONObject.NULL)
                            .put("caption", day.caption)
                    },
                ),
            )
            .put("stats", stats(PeriodStats.of(days)))
            .put("previousStats", previous?.let(::stats) ?: JSONObject.NULL)
            .put(
                "previousThreads",
                JSONArray(previousThreads.map { JSONObject().put("title", it.title).put("line", it.line) }),
            )
    }

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun stats(stats: PeriodStats): JSONObject = JSONObject()
        .put("daysKept", stats.daysKept)
        .put("daysWritten", stats.daysWritten)
        .put("words", stats.words)
        .put("averageMood", stats.averageMood ?: JSONObject.NULL)

    /** A recap as the backend sent it, before the phone adds its own numbers. */
    data class Draft(
        val title: String,
        val quote: String,
        val quoteDate: LocalDate?,
        val letter: RecapLetter,
    )

    /**
     * Null when the answer has no title or no note: that is not a recap worth showing.
     * Numbers the backend may have sent are not trusted: only the words are taken,
     * and the people and topics as names with the model's own estimate of their count.
     */
    fun parse(reply: JSONObject): Draft? {
        val title = reply.optString("title").trim()
        val note = reply.optString("note").trim()
        if (title.isEmpty() || note.isEmpty()) return null
        val quote = reply.optJSONObject("quote")
        // The answer's dayByDay lines are {"date", "line"}; the letter keeps them as {"date", "text"}.
        val stored = JSONObject(reply.toString()).apply {
            val lines = optJSONArray("dayByDay") ?: JSONArray()
            put(
                "dayByDay",
                JSONArray((0 until lines.length()).mapNotNull { lines.optJSONObject(it) }.map { line ->
                    JSONObject().put("date", line.optString("date")).put("text", line.optString("line"))
                }),
            )
            remove("stats")
            remove("previousStats")
            remove("places")
        }
        return Draft(
            title = title,
            quote = quote?.optString("text")?.trim().orEmpty(),
            quoteDate = runCatching { LocalDate.parse(quote?.optString("date")) }.getOrNull(),
            letter = RecapLetter.decode(stored.toString()),
        )
    }
}
