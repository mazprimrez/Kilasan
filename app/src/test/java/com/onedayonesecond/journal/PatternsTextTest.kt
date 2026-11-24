package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

class PatternsTextTest {

    private lateinit var saved: Locale

    @Before
    fun english() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restore() = Locale.setDefault(saved)

    private val week = RecapPeriod.Week(LocalDate.of(2026, 11, 9))
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val year = RecapPeriod.Year(2026)

    @Test
    fun `the navigator uses one date format at every scale`() {
        assertEquals("9–15 November 2026", PatternsLabels.range(week, DateSpan(week.start, week.end)))
        assertEquals("1–16 November 2026", PatternsLabels.range(november, DateSpan(november.start, LocalDate.of(2026, 11, 16))))
        assertEquals("January–16 November 2026", PatternsLabels.range(year, DateSpan(year.start, LocalDate.of(2026, 11, 16))))
        assertEquals("January–December 2025", PatternsLabels.range(RecapPeriod.Year(2025), DateSpan(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))))
        val straddling = RecapPeriod.Week(LocalDate.of(2026, 10, 26))
        assertEquals("26 October–1 November 2026", PatternsLabels.range(straddling, DateSpan(straddling.start, straddling.end)))
        val newYear = RecapPeriod.Week(LocalDate.of(2025, 12, 29))
        assertEquals("29 December 2025–4 January 2026", PatternsLabels.range(newYear, DateSpan(newYear.start, newYear.end)))
    }

    @Test
    fun `comparisons name the same span of the period before, short`() {
        assertEquals("1–16 Oct", PatternsLabels.short(november, DateSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16))))
        assertEquals("2–8 Nov", PatternsLabels.short(week, DateSpan(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))))
        assertEquals("Jan–16 Nov 2025", PatternsLabels.short(year, DateSpan(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 11, 16))))
        assertEquals("2025", PatternsLabels.short(year, DateSpan(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))))
    }

    @Test
    fun `the counted line, the quote's source and the in-short title`() {
        assertEquals("From 9 to 15 November", PatternsLabels.from(DateSpan(week.start, week.end)))
        assertEquals("From 1 January to 16 November", PatternsLabels.from(DateSpan(year.start, LocalDate.of(2026, 11, 16))))
        assertEquals("You, Saturday 14 November", PatternsLabels.quoteSource(LocalDate.of(2026, 11, 14)))
        assertEquals("The week in short", PatternsLabels.inShort(week, DateSpan(week.start, week.end)))
        assertEquals("November, in short", PatternsLabels.inShort(november, DateSpan(november.start, november.end)))
        assertEquals("November so far, in short", PatternsLabels.inShort(november, DateSpan(november.start, LocalDate.of(2026, 11, 16))))
        assertEquals("The year so far, in short", PatternsLabels.inShort(year, DateSpan(year.start, LocalDate.of(2026, 11, 16))))
    }

    private fun patterns(): Patterns {
        val zone = ZoneOffset.UTC
        val entries = mutableListOf<Entry>()
        val tags = mutableListOf<com.onedayonesecond.data.EntryTag>()
        val moods = mutableMapOf<LocalDate, Mood>()
        (9..14).forEachIndexed { i, day ->
            val date = LocalDate.of(2026, 11, day)
            val text = "Day $day."
            val entry = Entry(id = i + 1L, date = date, createdAt = date.atTime(20, 0).toInstant(zone).toEpochMilli(), type = EntryType.WRITTEN, text = text)
            entries += entry
            val topics = if (day <= 13) listOf("Work") else listOf("Family")
            tags += TagSet(topics, habits = if (day % 2 == 0) listOf("Gym") else emptyList())
                .rows(entry.id, TagHash.of(text)!!, 0)
            moods[date] = if (day % 2 == 0) Mood.GREAT else Mood.NEUTRAL
        }
        return PatternsCalculator(zone).calculate(week, LocalDate.of(2026, 11, 15), PatternsCalculator.Input(entries, moods, emptyMap(), tags))!!
    }

    @Test
    fun `the summary and the counted line come from the numbers`() {
        val p = patterns()
        assertEquals("Work came up most, on 5 of your 6 days with entries. Your average mood was good.", PatternsWords.summary(p))
        assertEquals("From 9 to 15 November: 6 days with entries, 6 entries. Counted on your phone.", PatternsWords.counted(p))
    }

    @Test
    fun `headings state the finding and never say mostly`() {
        val p = patterns()
        val habits = PatternsWords.habits(p)
        assertEquals("Gym days felt better", habits.heading)
        assertEquals("Habits · found in your entries when you mention them.", habits.label)
        assertEquals("Most often on Tuesday, Thursday and Saturday.", habits.sentence)
        assertEquals("Work Monday to Friday, gym Tuesday, Thursday and Saturday", PatternsWords.glance(p).heading)
        val whenSection = PatternsWords.whenYouWrite(p)
        assertEquals("You wrote in the evening", whenSection.heading)
        assertEquals("When · 6 of 6 entries came after 17:00.", whenSection.label)
        val mood = PatternsWords.weekMood(p)!!
        assertEquals("Your mood climbed from Monday to Tuesday", mood.heading)
        assertEquals("Mood · from neutral on Monday to great on Tuesday.", mood.label)
        assertEquals("Your average mood on gym days was great, and neutral on the other days.", mood.sentence)
        assertEquals(PatternsWords.NOT_PROOF, mood.footnote)
        val since = PatternsWords.sinceLast(p)
        assertEquals("Work and gym went up", since.heading)
        assertEquals("Since last week · compared with 2–8 November.", since.label)
        val lines = PatternsWords.sinceLastLines(p)
        assertEquals("Work went up from 0 to 5 days.", lines.first().text)
        assertEquals(Direction.UP, lines.first().direction)
        val all = listOf(PatternsWords.summary(p), habits.heading, habits.sentence!!, PatternsWords.moodAndTopics(p).heading) +
            lines.map { it.text }
        all.forEach { assertFalse(it, it.contains("mostly")) }
    }

    @Test
    fun `a topic's bar says how it compares with the period before`() {
        assertEquals("3 more than 1–16 Oct", PatternsWords.versusBefore(5, 2, "1–16 Oct"))
        assertEquals("1 fewer than 1–16 Oct", PatternsWords.versusBefore(2, 3, "1–16 Oct"))
        assertEquals("Same as 1–16 Oct", PatternsWords.versusBefore(2, 2, "1–16 Oct"))
        assertEquals("Not in 1–16 Oct", PatternsWords.versusBefore(2, 0, "1–16 Oct"))
    }

    @Test
    fun `links name the period they open`() {
        val today = LocalDate.of(2026, 11, 18)
        assertEquals("last week’s", PatternsLabels.possessive(week, today))
        assertEquals("November’s", PatternsLabels.possessive(november, today))
        assertEquals("2025’s", PatternsLabels.possessive(RecapPeriod.Year(2025), today))
    }

    @Test
    fun `names keep their capitals inside a sentence only when they are names`() {
        assertEquals("gym", PatternsWords.inline("Gym"))
        assertEquals("reading", PatternsWords.inline("Reading"))
        assertEquals("GBK", PatternsWords.inline("GBK"))
        assertEquals("Tech Cantik", PatternsWords.inline("Tech Cantik"))
    }

    @Test
    fun `ties at the top are named together`() {
        assertEquals("Work and Family", PatternsWords.names(listOf("Work", "Family")))
        assertEquals("A, B and C", PatternsWords.names(listOf("A", "B", "C")))
    }

    @Test
    fun `a comparison says more, fewer, the same or new`() {
        assertEquals("3 more days for Work than 1–16 Oct (2 days).", PatternsWords.compare("Work", 5, 2, "1–16 Oct"))
        assertEquals("1 fewer day for Work than 1–16 Oct (3 days).", PatternsWords.compare("Work", 2, 3, "1–16 Oct"))
        assertEquals("Work came up on as many days as 1–16 Oct.", PatternsWords.compare("Work", 2, 2, "1–16 Oct"))
        assertEquals("Work didn't come up in 1–16 Oct.", PatternsWords.compare("Work", 2, 0, "1–16 Oct"))
    }

    @Test
    fun `each scale has its footer, and the letter's chapter waits for two days`() {
        assertEquals(
            "A week is short: patterns here are single days, not habits. The month view shows what repeats.",
            PatternsWords.footer(week),
        )
        assertEquals("Patterns need at least 2 days to show. Counts update when you edit an entry.", PatternsWords.footer(november))
        assertEquals("Patterns are counts from your own entries, not conclusions about you.", PatternsWords.footer(year))
        assertEquals("Patterns show up after a few days.", PatternsWords.chapter(null))
        assertEquals("Work came up most, on 5 of your 6 days with entries.", PatternsWords.chapter(patterns()))
    }
}
