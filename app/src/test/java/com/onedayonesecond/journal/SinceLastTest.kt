package com.onedayonesecond.journal

import com.onedayonesecond.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** "Since last week" is counted and worded on the phone, never by the model. */
class SinceLastTest {

    private val locale = Locale.US

    private fun lines(current: PeriodStats, previous: PeriodStats?) =
        (SinceLast.of("week", current, previous, hadEarlier = true, locale) as SinceLast.Chapter.Compared).lines

    @Test
    fun `a number that went up says from what to what`() {
        assertEquals(SinceLast.Line(Direction.UP, "Days kept went up from 3 to 7."), SinceLast.change("Days kept", 3, 7, locale))
    }

    @Test
    fun `a number that went down says from what to what`() {
        assertEquals(
            SinceLast.Line(Direction.DOWN, "Days written went down from 7 to 5."),
            SinceLast.change("Days written", 7, 5, locale),
        )
    }

    @Test
    fun `a number that did not move stays, with the same icon and never went up from 7 to 7`() {
        val line = SinceLast.change("Days kept", 7, 7, locale)
        assertEquals(SinceLast.Line(Direction.SAME, "Days kept stayed at 7."), line)
        assertTrue("up" !in line.text)
    }

    @Test
    fun `big numbers are grouped`() {
        assertEquals("Words went up from 1,204 to 2,310.", SinceLast.change("Words", 1204, 2310, locale).text)
    }

    @Test
    fun `every line compares this period with the one before`() {
        val got = lines(PeriodStats(7, 5, 2310, 3.6), PeriodStats(3, 5, 1204, 2.4))
        assertEquals(
            listOf(
                SinceLast.Line(Direction.UP, "Days kept went up from 3 to 7."),
                SinceLast.Line(Direction.SAME, "Days written stayed at 5."),
                SinceLast.Line(Direction.UP, "Words went up from 1,204 to 2,310."),
                SinceLast.Line(Direction.UP, "Your mood was mostly good, up from mostly bad.", Mood.GOOD),
            ),
            got,
        )
    }

    @Test
    fun `mood is shown as a word, and moves only when the word does`() {
        assertEquals(
            SinceLast.Line(Direction.SAME, "Your mood stayed mostly good.", Mood.GOOD),
            SinceLast.moodLine(3.6, 4.1),
        )
        assertEquals(
            SinceLast.Line(Direction.DOWN, "Your mood was mostly neutral, down from mostly great.", Mood.NEUTRAL),
            SinceLast.moodLine(4.8, 3.14),
        )
        // No mood last week: no comparison, just this week's word.
        assertEquals(SinceLast.Line(null, "Your mood was mostly good.", Mood.GOOD), SinceLast.moodLine(null, 3.5))
        // No mood this week: no line at all.
        assertNull(SinceLast.moodLine(3.0, null))
    }

    @Test
    fun `an average rounds halves up to the nearest mood`() {
        assertEquals(Mood.GOOD, SinceLast.moodOf(3.5))
        assertEquals(Mood.NEUTRAL, SinceLast.moodOf(3.14))
        assertEquals(Mood.ROUGH, SinceLast.moodOf(1.0))
        assertEquals(Mood.GREAT, SinceLast.moodOf(4.6))
    }

    @Test
    fun `the first week ever is not compared with zero`() {
        val chapter = SinceLast.of("week", PeriodStats(7, 5, 2310, 3.6), previous = null, hadEarlier = false, locale)

        chapter as SinceLast.Chapter.Alone
        assertEquals("Your first week in Kilasan.", chapter.heading)
        assertEquals(
            listOf("7 days kept", "5 days written", "2,310 words", "Your mood was mostly good"),
            chapter.totals.map { it.text },
        )
        assertTrue(chapter.totals.all { it.direction == null })
        assertEquals(Mood.GOOD, chapter.totals.last().mood)
    }

    @Test
    fun `a period before with no days kept is not compared either`() {
        val chapter = SinceLast.of("month", PeriodStats(20, 12, 5000, null), PeriodStats(0, 2, 40, 3.0), hadEarlier = true, locale)

        chapter as SinceLast.Chapter.Alone
        // There was writing before, so this is a gap, not a first month.
        assertEquals("Nothing was kept last month to compare with.", chapter.heading)
        assertEquals(listOf("20 days kept", "12 days written", "5,000 words"), chapter.totals.map { it.text })
    }

    @Test
    fun `one of something is singular`() {
        val chapter = SinceLast.of("week", PeriodStats(1, 1, 1, null), null, hadEarlier = false, locale) as SinceLast.Chapter.Alone
        assertEquals(listOf("1 day kept", "1 day written", "1 word"), chapter.totals.map { it.text })
    }
}
