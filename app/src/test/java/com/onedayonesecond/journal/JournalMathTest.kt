package com.onedayonesecond.journal

import com.onedayonesecond.data.Mood
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalMathTest {

    private val saturday = LocalDate.of(2026, 11, 14)
    private val sunday = LocalDate.of(2026, 11, 15)

    private val days = listOf(
        DaySummary(
            saturday,
            hasClip = true,
            mood = Mood.GOOD,
            place = "Kemang, Jakarta Selatan",
            texts = listOf("Coffee before work.  It was the first quiet morning in weeks."),
            entryCount = 1,
        ),
        DaySummary(sunday, mood = Mood.BAD, place = "Kemang, Jakarta Selatan", texts = listOf("Tired today."), entryCount = 2),
        DaySummary(LocalDate.of(2026, 11, 13), hasClip = true, place = "Dago, Bandung"),
    )

    // The quote

    @Test
    fun `a quote that is in an entry is accepted, with the day it was written`() {
        val quote = JournalMath.verifyQuote("It was the first quiet morning in weeks.", days, claimedDate = sunday)
        assertEquals("It was the first quiet morning in weeks." to saturday, quote)
    }

    @Test
    fun `quotation marks, case and spacing do not stop a match, and the entry's own spelling is kept`() {
        val quote = JournalMath.verifyQuote("“coffee before work. it was the first quiet morning”", days, null)
        assertEquals("Coffee before work. It was the first quiet morning" to saturday, quote)
    }

    @Test
    fun `a quote the model made up is rejected`() {
        assertNull(JournalMath.verifyQuote("Every day is a gift.", days, saturday))
        assertNull("too short to be a quote", JournalMath.verifyQuote("Tired", days, sunday))
    }

    @Test
    fun `the fallback quote is a whole sentence from the longest entry`() {
        assertEquals("Coffee before work." to saturday, JournalMath.fallbackQuote(days))
    }

    @Test
    fun `there is no fallback quote without any writing`() {
        assertNull(JournalMath.fallbackQuote(listOf(DaySummary(saturday, hasClip = true))))
    }

    // Numbers and places

    @Test
    fun `stats count kept days, written days, words and the mean mood`() {
        val stats = PeriodStats.of(days)
        assertEquals(2, stats.daysKept)
        assertEquals(2, stats.daysWritten)
        assertEquals(13, stats.words)
        assertEquals(3.0, stats.averageMood!!, 0.0001)
    }

    @Test
    fun `no moods means no average rather than zero`() {
        assertNull(PeriodStats.of(listOf(DaySummary(saturday))).averageMood)
    }

    @Test
    fun `days are counted per city, most days first`() {
        val counted = JournalMath.daysPerCity(days.map { it.place } + listOf(null, " ", "Bandung"))
        assertEquals(listOf(PlaceDays("Bandung", 2), PlaceDays("Jakarta Selatan", 2)), counted)
        assertEquals("Jakarta Selatan", JournalMath.city("Kemang, Jakarta Selatan"))
        assertEquals("Ubud", JournalMath.city("Ubud"))
    }

    @Test
    fun `a place name is short and drops administrative prefixes`() {
        assertEquals(
            "Kemang, Jakarta Selatan",
            PlaceNames.short("Kemang", "Kecamatan Mampang Prapatan", "Kota Jakarta Selatan", "Daerah Khusus Ibukota Jakarta"),
        )
        assertEquals("Mampang Prapatan, Jakarta", PlaceNames.short(null, "Kecamatan Mampang Prapatan", null, "Jakarta"))
        assertEquals("Ubud", PlaceNames.short("Ubud", null, "Ubud", null))
        assertNull(PlaceNames.short(null, " ", null, null))
    }

    // Year in pixels

    @Test
    fun `a pixel is a mood, an empty day, a day to come, or not a day at all`() {
        val moods = mapOf(saturday to Mood.GREAT)
        val today = sunday
        assertEquals(Pixel(PixelKind.MOOD, Mood.GREAT), Pixels.at(2026, 11, 14, moods, today))
        assertEquals(PixelKind.EMPTY, Pixels.at(2026, 11, 15, moods, today).kind)
        assertEquals(PixelKind.EMPTY, Pixels.at(2026, 1, 1, moods, today).kind)
        assertEquals(PixelKind.FUTURE, Pixels.at(2026, 11, 16, moods, today).kind)
        assertEquals(PixelKind.ABSENT, Pixels.at(2026, 2, 30, moods, today).kind)
        assertEquals(PixelKind.ABSENT, Pixels.at(2026, 2, 29, moods, today).kind)
        assertEquals("2024 was a leap year", PixelKind.EMPTY, Pixels.at(2024, 2, 29, moods, today).kind)
    }

    @Test
    fun `mood counts list every mood, chosen or not`() {
        val counts = JournalMath.moodCounts(listOf(Mood.GOOD, Mood.GOOD, null, Mood.ROUGH))
        assertEquals(2, counts[Mood.GOOD])
        assertEquals(1, counts[Mood.ROUGH])
        assertEquals(0, counts[Mood.GREAT])
        assertEquals(Mood.entries.toSet(), counts.keys)
    }

    // The recap's JSON

    @Test
    fun `chapters survive being stored and read back`() {
        val content = RecapContent(
            whatHappened = listOf(DatedLine(saturday, "A quiet morning."), DatedLine(sunday, "Rest.")),
            note = "You kept going.",
            changes = listOf(Change(Direction.UP, "You wrote on 5 days, up from 3."), Change(Direction.SAME, "Mood held.")),
            tries = listOf("Write one line before bed.", "Take Wednesday's walk again."),
            places = listOf(PlaceDays("Jakarta Selatan", 212)),
        )
        assertEquals(content, RecapContent.decode(content.encode()))
    }

    @Test
    fun `unusable parts of a recap are skipped and a damaged one is empty`() {
        val json = """
            {"whatHappened":[{"date":"not a date","text":"x"},{"date":"2026-11-14","text":" ok "},{"date":"2026-11-15","text":""}],
             "note":" hello ",
             "changes":[{"direction":"sideways","text":"odd"},{"text":""}],
             "tries":["one","","two","three"],
             "places":[{"name":"","days":3},{"name":"Bandung","days":0}]}
        """.trimIndent()
        val content = RecapContent.decode(json)
        assertEquals(listOf(DatedLine(saturday, "ok")), content.whatHappened)
        assertEquals("hello", content.note)
        assertEquals(listOf(Change(Direction.SAME, "odd")), content.changes)
        assertEquals("no more than two suggestions", listOf("one", "two"), content.tries)
        assertTrue(content.places.isEmpty())
        assertEquals(RecapContent(), RecapContent.decode("not json"))
    }

    @Test
    fun `the request carries the days, the entries and both sets of numbers`() {
        val period = RecapPeriod.weekOf(saturday)
        val entries = listOf(
            com.onedayonesecond.data.Entry(id = 1, date = saturday, createdAt = 0, type = com.onedayonesecond.data.EntryType.WRITTEN, text = "Coffee."),
        )
        val request = RecapRequest.build(period, entries, days, PeriodStats(3, 1, 40, 2.5), emptyList(), name = " Eza ")

        assertEquals("week", request.getString("period"))
        assertEquals("week-2026-11-09", request.getString("key"))
        assertEquals("Eza", request.getString("name"))
        val sent = request.getJSONArray("days")
        assertEquals(3, sent.length())
        assertEquals("2026-11-13", sent.getJSONObject(0).getString("date"))
        assertEquals("Friday", sent.getJSONObject(0).getString("weekday"))
        assertTrue(sent.getJSONObject(0).isNull("mood"))
        assertEquals("GOOD", sent.getJSONObject(1).getString("mood"))
        assertEquals("Coffee.", request.getJSONArray("entries").getJSONObject(0).getString("text"))
        assertEquals(2, request.getJSONObject("stats").getInt("daysWritten"))
        assertEquals(1, request.getJSONObject("previousStats").getInt("daysWritten"))
    }

    @Test
    fun `long entries are cut before they are sent, more so for longer periods`() {
        val long = listOf(
            com.onedayonesecond.data.Entry(date = saturday, createdAt = 0, type = com.onedayonesecond.data.EntryType.WRITTEN, text = "x".repeat(10_000)),
        )
        val summary = listOf(DaySummary(saturday, texts = listOf("x".repeat(10_000)), entryCount = 1))
        fun sentLength(period: RecapPeriod) = RecapRequest.build(period, long, summary, null, emptyList(), "")
            .getJSONArray("entries").getJSONObject(0).getString("text").length
        assertEquals(4000, sentLength(RecapPeriod.weekOf(saturday)))
        assertEquals(400, sentLength(RecapPeriod.Year(2026)))
        assertTrue(RecapRequest.build(RecapPeriod.Year(2026), long, summary, null, emptyList(), "").isNull("previousStats"))
    }

    @Test
    fun `a reply without a title or a note is not a recap`() {
        assertNull(RecapRequest.parse(JSONObject("""{"note":"hello"}""")))
        assertNull(RecapRequest.parse(JSONObject("""{"title":"A week"}""")))
        val draft = RecapRequest.parse(
            JSONObject(
                """{"title":"A quiet week","quote":{"text":"Tired today.","date":"2026-11-15"},"note":"Rest counts.",
                   "places":[{"name":"Made up","days":9}]}""",
            ),
        )
        assertNotNull(draft)
        assertEquals("A quiet week", draft!!.title)
        assertEquals(sunday, draft.quoteDate)
        assertTrue("places come from the phone, never from the model", draft.letter.places.isEmpty())
    }

    // Month and year recaps

    @Test
    fun `the usual mood of each weekday is the rounded average of its days`() {
        val moods = mapOf(
            LocalDate.of(2026, 11, 2) to Mood.GREAT, // Monday
            LocalDate.of(2026, 11, 9) to Mood.GOOD, // Monday: 4.5 rounds up to great
            LocalDate.of(2026, 11, 4) to Mood.ROUGH, // Wednesday
        )
        val byWeekday = JournalMath.moodByWeekday(moods)
        assertEquals(Mood.GREAT, byWeekday[java.time.DayOfWeek.MONDAY])
        assertEquals(Mood.ROUGH, byWeekday[java.time.DayOfWeek.WEDNESDAY])
        assertNull(byWeekday[java.time.DayOfWeek.SUNDAY])
        assertEquals(7, byWeekday.size)
    }
}
