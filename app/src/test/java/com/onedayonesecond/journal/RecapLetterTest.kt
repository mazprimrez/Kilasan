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
import java.time.YearMonth
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
            topPeople = listOf(Mention("Ibu", 3, previous = 1), Mention("Raka", 2, previous = null)),
            topTopics = listOf(Mention("Kerjaan", 5, previous = 0)),
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
             "topPeople": [], "topTopics": [],
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
    fun `people are counted on the phone and compared with the period before`() {
        val month = RecapPeriod.Month(YearMonth.of(2026, 11))
        val days = listOf(
            DaySummary(monday, texts = listOf("Makan sama Ibu."), entryCount = 1),
            DaySummary(monday.plusDays(1), texts = listOf("ibu telpon"), entryCount = 1),
            DaySummary(monday.plusDays(2), texts = listOf("Ke ibukota naik kereta."), entryCount = 1),
            DaySummary(monday.plusDays(3), texts = listOf("Kerja."), entryCount = 1),
        )
        val letter = RecapLetter(
            topPeople = listOf(Mention("Raka", 4), Mention("Ibu", 9)),
            topTopics = listOf(Mention("Kerjaan", 2)),
        )
        val before = RecapLetter(topPeople = listOf(Mention("ibu", 1)), topTopics = listOf(Mention("Kerjaan", 2)))

        val got = RecapGenerator.withNumbers(letter, month, days, previousStats = null, previousLetter = before, earliest = null)

        // "Ibu" is in two days' words (not counted inside "ibukota"); the model's 9 is replaced.
        // "Raka" is never written word for word, so the model's estimate stays.
        assertEquals(listOf(Mention("Raka", 4, previous = 0), Mention("Ibu", 2, previous = 1)), got.topPeople)
        assertEquals(listOf(Mention("Kerjaan", 2, previous = 2)), got.topTopics)
        assertEquals(Direction.SAME, got.topTopics.single().direction)
    }

    @Test
    fun `a week has no people or topics, and a year has places`() {
        val letter = RecapLetter(topPeople = listOf(Mention("Ibu", 1)))
        val days = listOf(DaySummary(monday, place = "Kemang, Jakarta Selatan", entryCount = 1))

        assertTrue(RecapGenerator.withNumbers(letter, week, days, null, null, null).topPeople.isEmpty())
        val year = RecapGenerator.withNumbers(letter, RecapPeriod.Year(2026), days, null, null, null)
        assertEquals(listOf(PlaceDays("Jakarta Selatan", 1)), year.places)
        assertNull(year.topPeople.single().previous)
    }

    @Test
    fun `the stats are this period's own, and whether anything came before is remembered`() {
        val days = listOf(DaySummary(monday, hasClip = true, texts = listOf("satu dua tiga"), entryCount = 1))
        val got = RecapGenerator.withNumbers(RecapLetter(), week, days, PeriodStats(2, 1, 9, null), null, earliest = monday.minusDays(30))

        assertEquals(PeriodStats(1, 1, 3, null), got.stats)
        assertEquals(PeriodStats(2, 1, 9, null), got.previousStats)
        assertTrue(got.hadEarlier)
        assertEquals(false, RecapGenerator.withNumbers(RecapLetter(), week, days, null, null, earliest = monday).hadEarlier)
    }
}
