package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryTag
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class PatternsCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val calculator = PatternsCalculator(zone)

    // 16–22 November 2026 is a Monday-to-Sunday week.
    private val monday = LocalDate.of(2026, 11, 16)
    private val week = RecapPeriod.Week(monday)
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val year = RecapPeriod.Year(2026)

    private var nextId = 1L

    /** A tiny diary: each entry is (date, text, topics, habits, people), written at [time]. */
    private inner class Diary {
        val entries = mutableListOf<Entry>()
        val tags = mutableListOf<EntryTag>()
        val moods = mutableMapOf<LocalDate, Mood>()
        val places = mutableMapOf<LocalDate, String>()

        fun entry(
            date: LocalDate,
            text: String = "Entry ${nextId}.",
            topics: List<String> = emptyList(),
            habits: List<String> = emptyList(),
            people: List<String> = emptyList(),
            time: LocalTime = LocalTime.of(20, 0),
        ): Entry {
            val entry = Entry(
                id = nextId++,
                date = date,
                createdAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                type = EntryType.WRITTEN,
                text = text,
                processing = ProcessingState.DONE,
            )
            entries += entry
            tags += TagSet(topics, habits, people).rows(entry.id, TagHash.of(text)!!, 0)
            return entry
        }

        fun input() = PatternsCalculator.Input(entries.toList(), moods.toMap(), places.toMap(), tags.toList())
    }

    private fun Diary.calc(period: RecapPeriod, today: LocalDate, people: Boolean = true) =
        calculator.calculate(period, today, input(), people)!!

    // Range filtering and the same span for comparisons

    @Test
    fun `a period still going covers only the days up to today`() {
        assertEquals(DateSpan(monday, monday.plusDays(2)), PatternSpans.visible(week, monday.plusDays(2)))
        assertEquals(DateSpan(november.start, LocalDate.of(2026, 11, 24)), PatternSpans.visible(november, LocalDate.of(2026, 11, 24)))
        assertEquals(DateSpan(year.start, LocalDate.of(2026, 11, 24)), PatternSpans.visible(year, LocalDate.of(2026, 11, 24)))
        assertNull(PatternSpans.visible(week.next(), monday))
    }

    @Test
    fun `a partial period is compared with the same days of the one before`() {
        val wednesday = monday.plusDays(2)
        assertEquals(
            DateSpan(monday.minusWeeks(1), wednesday.minusWeeks(1)),
            PatternSpans.previous(week, PatternSpans.visible(week, wednesday)!!),
        )
        val sixteenth = LocalDate.of(2026, 11, 16)
        assertEquals(
            DateSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16)),
            PatternSpans.previous(november, PatternSpans.visible(november, sixteenth)!!),
        )
        assertEquals(
            DateSpan(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 11, 16)),
            PatternSpans.previous(year, PatternSpans.visible(year, sixteenth)!!),
        )
    }

    @Test
    fun `a day the month before does not have is cut to its last day, and 29 February to the 28th`() {
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        val the31st = LocalDate.of(2026, 10, 31)
        // 1–31 Oct is the whole month, so it is compared with the whole of September.
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), PatternSpans.previous(october, DateSpan(october.start, the31st)))
        val march = RecapPeriod.Month(YearMonth.of(2027, 3))
        assertEquals(
            DateSpan(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)),
            PatternSpans.previous(march, DateSpan(march.start, LocalDate.of(2027, 3, 30))),
        )
        val leap = RecapPeriod.Year(2028)
        assertEquals(
            DateSpan(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 28)),
            PatternSpans.previous(leap, DateSpan(leap.start, LocalDate.of(2028, 2, 29))),
        )
    }

    @Test
    fun `a whole period is compared with the whole period before`() {
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), PatternSpans.previous(october, DateSpan(october.start, october.end)))
    }

    @Test
    fun `every count covers exactly the span, and the comparison exactly the same span before`() {
        val d = Diary()
        val today = LocalDate.of(2026, 11, 16)
        d.entry(LocalDate.of(2026, 11, 2), topics = listOf("Work"))
        d.entry(LocalDate.of(2026, 11, 16), topics = listOf("Work"))
        d.entry(LocalDate.of(2026, 11, 20), topics = listOf("Work")) // after today: never counted
        d.entry(LocalDate.of(2026, 10, 31), topics = listOf("Work")) // last month, outside both spans
        d.entry(LocalDate.of(2026, 10, 3), topics = listOf("Work"))
        d.entry(LocalDate.of(2026, 10, 10), topics = listOf("Work"))
        d.entry(LocalDate.of(2026, 10, 17), topics = listOf("Work")) // the 17th: just outside 1–16 Oct

        val p = d.calc(november, today)

        assertEquals(DateSpan(LocalDate.of(2026, 11, 1), today), p.span)
        assertEquals(2, p.daysWithEntries)
        assertEquals(2, p.entryCount)
        assertEquals(listOf(LocalDate.of(2026, 11, 2), today), p.topics.single().days)
        assertEquals(2, p.topics.single().previousDays)
        assertEquals(2, p.previousDaysWithEntries)
    }

    // Averages, rounded half up

    @Test
    fun `moods are averages of their scores, halves rounding up`() {
        assertEquals(Mood.NEUTRAL, MoodAverage.of(listOf(Mood.BAD, Mood.NEUTRAL))!!.mood) // 2.5
        assertEquals(Mood.GOOD, MoodAverage.of(listOf(Mood.NEUTRAL, Mood.GOOD))!!.mood) // 3.5
        assertEquals(Mood.NEUTRAL, MoodAverage.of(listOf(Mood.NEUTRAL, Mood.NEUTRAL, Mood.GOOD))!!.mood) // 3.33
        assertEquals(Mood.GOOD, MoodAverage.of(listOf(Mood.GOOD, Mood.GOOD, Mood.NEUTRAL, Mood.GREAT))!!.mood) // 4.0
        assertEquals(1.5, MoodAverage.of(listOf(Mood.ROUGH, Mood.BAD))!!.average, 0.0)
        assertNull(MoodAverage.of(emptyList()))
    }

    @Test
    fun `the usual mood is the average over the span's days with a mood`() {
        val d = Diary()
        d.entry(monday, topics = listOf("Work"))
        d.entry(monday.plusDays(1), topics = listOf("Work"))
        d.moods[monday] = Mood.BAD
        d.moods[monday.plusDays(1)] = Mood.NEUTRAL
        d.moods[monday.minusDays(1)] = Mood.ROUGH // the week before: not usual for this week

        val usual = d.calc(week, monday.plusDays(6)).usualMood!!

        assertEquals(2.5, usual.average, 0.0)
        assertEquals(2, usual.days)
        assertEquals(Mood.NEUTRAL, usual.mood)
    }

    // The 2-day minimum and "Too few"

    @Test
    fun `nothing that came up on only one day is shown`() {
        val d = Diary()
        d.entry(monday, topics = listOf("Work", "Travel"), habits = listOf("Gym"), people = listOf("Raka"))
        d.entry(monday.plusDays(1), topics = listOf("Work"))
        // Twice on the same day is still one day.
        d.entry(monday.plusDays(1), topics = listOf("Travel"))
        d.entry(monday.plusDays(1), topics = listOf("Travel"))

        val p = d.calc(week, monday.plusDays(6))

        assertEquals(listOf("Travel", "Work"), p.topics.map { it.name })
        assertTrue(p.habits.isEmpty())
        assertTrue(p.people.isEmpty())
        assertTrue(p.hasEnough)
    }

    @Test
    fun `with nothing on two days there is not enough to show`() {
        val d = Diary()
        d.entry(monday, topics = listOf("Work"))
        d.entry(monday.plusDays(1), topics = listOf("Family"))

        assertFalse(d.calc(week, monday.plusDays(6)).hasEnough)
    }

    @Test
    fun `a weekday with fewer than two moods says too few`() {
        val d = Diary()
        val mondays = listOf(2, 9, 16).map { LocalDate.of(2026, 11, it) }
        mondays.forEach { d.entry(it); d.moods[it] = Mood.GOOD }
        d.entry(LocalDate.of(2026, 11, 3))
        d.moods[LocalDate.of(2026, 11, 3)] = Mood.ROUGH

        val p = d.calc(november, LocalDate.of(2026, 11, 22))
        val byDay = p.moodByWeekday.associateBy { DayOfWeek.of(it.key) }

        assertEquals(Mood.GOOD, byDay.getValue(DayOfWeek.MONDAY).average!!.mood)
        assertEquals(3, byDay.getValue(DayOfWeek.MONDAY).days)
        assertNull(byDay.getValue(DayOfWeek.TUESDAY).average)
        assertEquals(1, byDay.getValue(DayOfWeek.TUESDAY).days)
        assertNull(byDay.getValue(DayOfWeek.SUNDAY).average)
        assertEquals(7, p.moodByWeekday.size)
    }

    @Test
    fun `habit moods need two days on each side`() {
        val d = Diary()
        val days = (0L..3L).map { monday.plusDays(it) }
        d.entry(days[0], habits = listOf("Gym")); d.moods[days[0]] = Mood.GREAT
        d.entry(days[1], habits = listOf("Gym")); d.moods[days[1]] = Mood.GOOD
        d.entry(days[2]); d.moods[days[2]] = Mood.BAD

        val habit = d.calc(week, monday.plusDays(6)).habits.single()

        assertEquals(Mood.GREAT, habit.onHabitDays!!.mood) // 4.5 rounds up
        assertNull(habit.otherDays) // one other day with a mood: too few

        d.moods[days[3]] = Mood.NEUTRAL
        d.entry(days[3])
        val again = d.calc(week, monday.plusDays(6)).habits.single()
        assertEquals(Mood.NEUTRAL, again.otherDays!!.mood) // 2.5 rounds up
        assertEquals(2, again.otherDays!!.days)
    }

    // Above and below usual

    @Test
    fun `a topic's mood is above, below or the same as usual, and they are sorted lightest first`() {
        val d = Diary()
        val days = (1..6).map { LocalDate.of(2026, 11, it) }
        // Work on bad days, Family on great days, Reading on a mix that averages to usual.
        d.entry(days[0], topics = listOf("Work")); d.moods[days[0]] = Mood.BAD
        d.entry(days[1], topics = listOf("Work")); d.moods[days[1]] = Mood.BAD
        d.entry(days[2], topics = listOf("Family")); d.moods[days[2]] = Mood.GREAT
        d.entry(days[3], topics = listOf("Family")); d.moods[days[3]] = Mood.GREAT
        d.entry(days[4], topics = listOf("Reading")); d.moods[days[4]] = Mood.BAD
        d.entry(days[5], topics = listOf("Reading")); d.moods[days[5]] = Mood.GREAT

        val p = d.calc(november, LocalDate.of(2026, 11, 30))

        assertEquals(3.5, p.usualMood!!.average, 0.0)
        assertEquals(listOf("Family", "Reading", "Work"), p.moodAndTopics.map { it.name })
        assertEquals(listOf(Versus.ABOVE, Versus.SAME, Versus.BELOW), p.moodAndTopics.map { it.versusUsual })
        assertEquals(Mood.GREAT, p.moodAndTopics.first().mood.mood)
    }

    @Test
    fun `averages that round to the same tenth are the same, not higher or lower`() {
        assertEquals(Versus.SAME, PatternsCalculator.versus(3.62, 3.58))
        assertEquals(Versus.ABOVE, PatternsCalculator.versus(3.7, 3.58))
        assertEquals(Versus.BELOW, PatternsCalculator.versus(3.4, 3.58))
    }

    @Test
    fun `a topic whose days have fewer than two moods is left out of mood and topics`() {
        val d = Diary()
        d.entry(monday, topics = listOf("Work")); d.moods[monday] = Mood.GOOD
        d.entry(monday.plusDays(1), topics = listOf("Work"))

        val p = d.calc(week, monday.plusDays(6))

        assertEquals(1, p.topics.size)
        assertTrue(p.moodAndTopics.isEmpty())
    }

    // Top-N ordering and ties

    @Test
    fun `most days first, ties in alphabetical order ignoring case, at most eight`() {
        val d = Diary()
        val names = listOf("work", "Family", "Health", "Money", "Faith", "Food", "Sleep", "Travel", "Books", "Art")
        val days = (1..10).map { LocalDate.of(2026, 11, it) }
        // Work on 5 days, everything else on 2.
        days.take(5).forEach { d.entry(it, topics = listOf("work")) }
        names.drop(1).forEachIndexed { i, name ->
            d.entry(days[i % 10], topics = listOf(name))
            d.entry(days[(i + 3) % 10], topics = listOf(name))
        }

        val p = d.calc(november, LocalDate.of(2026, 11, 30))

        assertEquals(PatternsCalculator.MAX_TOPICS, p.topics.size)
        assertEquals("work", p.topics[0].name)
        assertEquals(listOf("Art", "Books", "Faith", "Family", "Food", "Health", "Money"), p.topics.drop(1).map { it.name })
    }

    @Test
    fun `every name tied for the top is the top`() {
        val items = listOf("Work" to 5, "Family" to 5, "Gym" to 3)
        assertEquals(listOf("Work", "Family"), PatternsCalculator.tiedTop(items) { it.second }.map { it.first })
        assertEquals(emptyList<Pair<String, Int>>(), PatternsCalculator.tiedTop(emptyList<Pair<String, Int>>()) { it.second })
    }

    @Test
    fun `the same name in other capitals is one name, shown as it is written most`() {
        val d = Diary()
        d.entry(monday, topics = listOf("Work"))
        d.entry(monday.plusDays(1), topics = listOf("work"))
        d.entry(monday.plusDays(2), topics = listOf("Work"))

        val topic = d.calc(week, monday.plusDays(6)).topics.single()

        assertEquals("Work", topic.name)
        assertEquals(3, topic.count)
    }

    // Weekday and month bucketing, in the device's time zone

    @Test
    fun `days per weekday and per month`() {
        val d = Diary()
        listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 4)).forEach {
            d.entry(it, topics = listOf("Work"))
        }

        val topic = d.calc(year, LocalDate.of(2026, 11, 24)).topics.single()

        assertEquals(2, topic.byWeekday.getValue(DayOfWeek.MONDAY))
        assertEquals(1, topic.byWeekday.getValue(DayOfWeek.WEDNESDAY))
        assertEquals(listOf(2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0), topic.byMonth)
    }

    @Test
    fun `the time of day is read in the device's time zone`() {
        val jakarta = ZoneId.of("Asia/Jakarta") // UTC+7
        val entry = Entry(
            id = 1,
            date = monday,
            // 15:30 UTC is 22:30 in Jakarta.
            createdAt = monday.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
            type = EntryType.WRITTEN,
            text = "Late.",
        )
        val input = PatternsCalculator.Input(listOf(entry), emptyMap(), emptyMap(), emptyList())

        val utc = PatternsCalculator(ZoneOffset.UTC).calculate(week, monday.plusDays(6), input)!!
        val wib = PatternsCalculator(jakarta).calculate(week, monday.plusDays(6), input)!!

        assertEquals(1, utc.dayParts.counts[DayPart.AFTERNOON])
        assertEquals(1, wib.dayParts.counts[DayPart.NIGHT])
        assertEquals(0, wib.dayParts.counts[DayPart.AFTERNOON])
    }

    @Test
    fun `the parts of the day have fixed hours`() {
        assertEquals(DayPart.NIGHT, DayPart.of(4))
        assertEquals(DayPart.MORNING, DayPart.of(5))
        assertEquals(DayPart.MORNING, DayPart.of(10))
        assertEquals(DayPart.AFTERNOON, DayPart.of(11))
        assertEquals(DayPart.AFTERNOON, DayPart.of(16))
        assertEquals(DayPart.EVENING, DayPart.of(17))
        assertEquals(DayPart.EVENING, DayPart.of(21))
        assertEquals(DayPart.NIGHT, DayPart.of(22))
        assertEquals(DayPart.NIGHT, DayPart.of(0))
        assertEquals(listOf("05–11", "11–17", "17–22", "22–05"), DayPart.entries.map { it.hours })
        assertEquals("Late night", DayPart.NIGHT.label)
    }

    @Test
    fun `a year shows the mood of each month so far and the two halves of the day once it reaches July`() {
        val d = Diary()
        d.entry(LocalDate.of(2026, 2, 3), time = LocalTime.of(8, 0))
        d.entry(LocalDate.of(2026, 8, 3), time = LocalTime.of(8, 0))
        d.entry(LocalDate.of(2026, 8, 4), time = LocalTime.of(22, 0))

        val p = d.calc(year, LocalDate.of(2026, 11, 24))

        assertEquals((1..11).toList(), p.moodByMonth.map { it.key })
        assertEquals(listOf(1..6, 7..11), p.dayPartHalves.map { it.months })
        assertEquals(1, p.dayPartHalves[0].counts[DayPart.MORNING])
        assertEquals(1, p.dayPartHalves[1].counts[DayPart.NIGHT])

        val spring = d.calc(year, LocalDate.of(2026, 4, 1))
        assertEquals(1, spring.dayPartHalves.size)
    }

    // Tags that no longer fit, people, places and words

    @Test
    fun `tags of an entry whose words changed since do not count`() {
        val d = Diary()
        val first = d.entry(monday, text = "Gym pagi.", habits = listOf("Gym"))
        d.entry(monday.plusDays(1), text = "Gym lagi.", habits = listOf("Gym"))
        d.entries[d.entries.indexOf(first)] = first.copy(text = "Ga jadi ke gym.")

        assertTrue(d.calc(week, monday.plusDays(6)).habits.isEmpty())
    }

    @Test
    fun `with people switched off there are no people and their names leave the words too`() {
        val d = Diary()
        d.entry(monday, text = "Makan malam sama Raka di kemang.", people = listOf("Raka"))
        d.entry(monday.plusDays(1), text = "Raka telpon, ngobrol soal kemang.", people = listOf("Raka"))

        val shown = d.calc(week, monday.plusDays(6))
        val hidden = d.calc(week, monday.plusDays(6), people = false)

        assertEquals("Raka", shown.people.single().name)
        assertTrue("raka" in shown.words.map { it.word })
        assertTrue(hidden.people.isEmpty())
        assertFalse("raka" in hidden.words.map { it.word })
        assertTrue("kemang" in hidden.words.map { it.word })
    }

    @Test
    fun `stop words in both languages are left out and a word needs two days`() {
        val d = Diary()
        d.entry(monday, text = "Yang the meeting, gue udah meeting.")
        d.entry(monday.plusDays(1), text = "The meeting yang lama. Kopi.")

        val words = d.calc(week, monday.plusDays(6)).words

        assertEquals(listOf(WordCount("meeting", 3, 2)), words)
    }

    @Test
    fun `places count days per place with an average mood where two days had one`() {
        val d = Diary()
        val days = (0L..2L).map { monday.plusDays(it) }
        days.forEach { d.entry(it) }
        d.places[days[0]] = "Kemang, Jakarta Selatan"
        d.places[days[1]] = "Kemang, Jakarta Selatan "
        d.places[days[2]] = "Dago, Bandung"
        d.moods[days[0]] = Mood.GOOD
        d.moods[days[1]] = Mood.GREAT

        val places = d.calc(week, monday.plusDays(6)).places

        assertEquals(listOf("Kemang, Jakarta Selatan"), places.map { it.name })
        assertEquals(2, places.single().days)
        assertEquals(Mood.GREAT, places.single().mood!!.mood) // 4.5
    }

    @Test
    fun `since last week compares the same days of last week`() {
        val d = Diary()
        d.entry(monday)
        d.entry(monday.plusDays(1))
        d.entry(monday.minusWeeks(1))
        d.entry(monday.minusWeeks(1).plusDays(5)) // last Saturday: outside Mon–Tue of last week
        d.moods[monday] = Mood.GOOD
        d.moods[monday.minusWeeks(1)] = Mood.NEUTRAL

        val p = d.calc(week, monday.plusDays(1))

        assertEquals(
            listOf(
                "Days with entries went up from 1 to 2.",
                "Entries went up from 1 to 2.",
                "Your average mood was good, up from neutral.",
            ),
            p.sinceLast.map { it.text },
        )
    }
}
