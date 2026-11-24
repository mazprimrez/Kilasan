package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecapLetterTest {

    private val monday = LocalDate.of(2026, 11, 16)
    private val week = RecapPeriod.Week(monday)

    @Test
    fun `a letter survives being stored and read back`() {
        val letter = RecapLetter(
            howYouFelt = "Gym days were the best.",
            howYouFeltDays = listOf(monday, monday.plusDays(1)),
            threads = listOf(RecapThread("Tech Cantik", "Prepared Wed, spoke Sat.", listOf(monday.plusDays(2), monday.plusDays(5)))),
            dayByDay = listOf(DatedLine(monday, "Gym, lalu kerja.")),
            wins = listOf(DatedNote("Spoke at Tech Cantik.", monday.plusDays(5))),
            stillOpen = listOf(DatedNote("Bug di checkout.", monday.plusDays(3))),
            note = "A note.",
            question = "Which one?",
            summary = "On Monday the gym, on Saturday the talk.",
            places = listOf(PlaceDays("Jakarta Selatan", 6)),
            stats = PeriodStats(7, 6, 2310, 3.6),
            previousStats = null,
            hadEarlier = true,
        )

        assertEquals(letter, RecapLetter.decode(letter.encode()))
    }

    @Test
    fun `a damaged letter reads as an empty one`() {
        assertEquals(RecapLetter(), RecapLetter.decode("not json"))
    }

    @Test
    fun `an answer from the backend becomes a draft, without any numbers it may have sent`() {
        val reply = JSONObject(
            """
            {"schemaVersion": 2, "title": "Minggu Tech Cantik",
             "quote": {"text": "Akhirnya ngomong di depan orang.", "date": "2026-11-21"},
             "howYouFelt": {"text": "Steady.", "days": []},
             "threads": [{"title": "Tech Cantik", "line": "Prepared, then spoke.", "days": ["2026-11-18", "2026-11-21"]}],
             "dayByDay": [{"date": "2026-11-16", "line": "Gym."}],
             "wins": [], "stillOpen": [], "note": "A note.", "question": "Q?",
             "summary": "  Gym on Monday, the talk on Saturday.  ",
             "stats": {"daysKept": 99}, "places": [{"name": "Mars", "days": 3}]}
            """,
        )

        val draft = RecapRequest.parse(reply)!!

        assertEquals("Minggu Tech Cantik", draft.title)
        assertEquals("Akhirnya ngomong di depan orang.", draft.quote)
        assertEquals(LocalDate.of(2026, 11, 21), draft.quoteDate)
        assertEquals(listOf(DatedLine(monday, "Gym.")), draft.letter.dayByDay)
        assertEquals(2, draft.letter.threads.single().days.size)
        assertEquals(0, draft.letter.stats.daysKept)
        assertTrue(draft.letter.places.isEmpty())
        assertEquals("Gym on Monday, the talk on Saturday.", draft.letter.summary)
    }

    @Test
    fun `a letter written before the summary existed reads with an empty one and ignores its people and topics`() {
        val old = """{"note": "N", "topPeople": [{"name": "Ibu", "count": 4}], "topTopics": []}"""

        val letter = RecapLetter.decode(old)

        assertEquals("", letter.summary)
        assertEquals("N", letter.note)
        assertTrue(!letter.encode().contains("topPeople"))
    }

    @Test
    fun `an answer without a title or a note is no recap`() {
        assertNull(RecapRequest.parse(JSONObject("""{"title": "T", "note": " "}""")))
        assertNull(RecapRequest.parse(JSONObject("""{"title": "", "note": "N"}""")))
    }

    @Test
    fun `the request carries every entry with its time and type, the moods and the previous threads`() {
        val entries = listOf(
            Entry(id = 2, date = monday.plusDays(1), createdAt = 1_000_000, type = EntryType.VOICE, text = " Review PR. "),
            Entry(id = 1, date = monday, createdAt = 0, type = EntryType.PHOTO, placeName = "Kemang"),
        )
        val days = listOf(
            DaySummary(monday, hasClip = true, mood = Mood.GOOD, entryCount = 1),
            DaySummary(monday.plusDays(1), mood = Mood.BAD, texts = listOf("Review PR."), entryCount = 1),
        )

        val request = RecapRequest.build(
            week,
            entries,
            days,
            previous = null,
            previousThreads = listOf(RecapThread("Kerjaan", "Deadline.", emptyList())),
            name = "Eza",
            zone = ZoneOffset.UTC,
        )

        assertEquals(2, request.getInt("schemaVersion"))
        val sent = request.getJSONArray("entries")
        assertEquals("2026-11-16", sent.getJSONObject(0).getString("date"))
        assertEquals("PHOTO", sent.getJSONObject(0).getString("type"))
        assertEquals("", sent.getJSONObject(0).getString("text"))
        assertEquals("00:16", sent.getJSONObject(1).getString("time"))
        assertEquals("Review PR.", sent.getJSONObject(1).getString("text"))
        assertEquals("GOOD", request.getJSONArray("days").getJSONObject(0).getString("mood"))
        assertTrue(request.isNull("previousStats"))
        assertEquals("Kerjaan", request.getJSONArray("previousThreads").getJSONObject(0).getString("title"))
    }

    @Test
    fun `a year has places and a week has none`() {
        val days = listOf(DaySummary(monday, place = "Kemang, Jakarta Selatan", entryCount = 1))

        assertTrue(RecapGenerator.withNumbers(RecapLetter(), week, days, null, null).places.isEmpty())
        val year = RecapGenerator.withNumbers(RecapLetter(), RecapPeriod.Year(2026), days, null, null)
        assertEquals(listOf(PlaceDays("Jakarta Selatan", 1)), year.places)
    }

    @Test
    fun `the stats are this period's own, and whether anything came before is remembered`() {
        val days = listOf(DaySummary(monday, hasClip = true, texts = listOf("satu dua tiga"), entryCount = 1))
        val got = RecapGenerator.withNumbers(RecapLetter(), week, days, PeriodStats(2, 1, 9, null), earliest = monday.minusDays(30))

        assertEquals(PeriodStats(1, 1, 3, null), got.stats)
        assertEquals(PeriodStats(2, 1, 9, null), got.previousStats)
        assertTrue(got.hadEarlier)
        assertEquals(false, RecapGenerator.withNumbers(RecapLetter(), week, days, null, earliest = monday).hadEarlier)
    }
}
