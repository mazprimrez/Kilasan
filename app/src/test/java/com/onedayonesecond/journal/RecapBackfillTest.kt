package com.onedayonesecond.journal

import com.onedayonesecond.data.Recap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Which past recaps are offered, and in what order they are written. */
class RecapBackfillTest {

    // Monday 23 November 2026, mid-morning.
    private val now = LocalDateTime.of(2026, 11, 23, 10, 0)

    private fun days(vararg dates: String) = dates.map(LocalDate::parse)

    /** Three days with entries in each of the weeks starting on these Mondays. */
    private fun weeksWithThreeDays(vararg mondays: String) =
        mondays.flatMap { monday -> (0L..2L).map { LocalDate.parse(monday).plusDays(it) } }

    @Test
    fun `a week needs three days with entries`() {
        val plan = RecapBackfill.plan(
            days("2026-11-02", "2026-11-03") + days("2026-11-09", "2026-11-10", "2026-11-11"),
            recaps = emptyMap(),
            now = now,
        )
        assertEquals(listOf(RecapPeriod.Week(LocalDate.parse("2026-11-09"))), plan.weeks)
    }

    @Test
    fun `a week whose letter is not due yet is not offered`() {
        // The week of 23 November ends on Sunday the 29th.
        val plan = RecapBackfill.plan(days("2026-11-23", "2026-11-24", "2026-11-25"), emptyMap(), now)
        assertTrue(plan.weeks.isEmpty())
        // Last week's letter came on Sunday night, so it is.
        val lastWeek = RecapBackfill.plan(days("2026-11-16", "2026-11-17", "2026-11-18"), emptyMap(), now)
        assertEquals(listOf(RecapPeriod.Week(LocalDate.parse("2026-11-16"))), lastWeek.weeks)
    }

    @Test
    fun `a period that already has a recap is not offered as missing`() {
        val dates = weeksWithThreeDays("2026-10-05", "2026-10-12")
        val plan = RecapBackfill.plan(dates, mapOf("week-2026-10-05" to Recap.LAYOUT_V2), now)
        assertEquals(listOf(RecapPeriod.Week(LocalDate.parse("2026-10-12"))), plan.weeks)
        assertEquals(listOf(RecapPeriod.Month(java.time.YearMonth.of(2026, 10))), plan.months)
    }

    @Test
    fun `months use the same threshold, counted over the month`() {
        // September: two days. October: three, in different weeks (no week is enough on its own).
        val plan = RecapBackfill.plan(
            days("2026-09-01", "2026-09-20", "2026-10-01", "2026-10-15", "2026-10-29"),
            emptyMap(),
            now,
        )
        assertEquals(listOf(RecapPeriod.Month(java.time.YearMonth.of(2026, 10))), plan.months)
        assertTrue(plan.weeks.isEmpty())
    }

    @Test
    fun `a finished year is offered, and this year only as so far and only when asked`() {
        val dates = days("2025-03-01", "2025-06-01", "2025-09-01", "2026-01-05", "2026-04-05", "2026-08-05")
        val plan = RecapBackfill.plan(dates, emptyMap(), now)

        assertEquals(listOf(RecapPeriod.Year(2025)), plan.years)
        assertEquals(RecapPeriod.Year(2026), plan.thisYear)
        assertTrue(RecapPeriod.Year(2026) !in RecapBackfill.queue(plan, includeOldLayout = false, includeThisYear = false))
        assertEquals(RecapPeriod.Year(2026), RecapBackfill.queue(plan, false, includeThisYear = true).last())
    }

    @Test
    fun `this year is not offered without enough entries`() {
        assertNull(RecapBackfill.plan(days("2026-01-05", "2026-04-05"), emptyMap(), now).thisYear)
    }

    @Test
    fun `recaps in the old layout are listed, and only written when chosen`() {
        val dates = weeksWithThreeDays("2026-10-05")
        val plan = RecapBackfill.plan(
            dates,
            mapOf("week-2026-10-05" to Recap.LAYOUT_V1, "month-2026-10" to Recap.LAYOUT_V2),
            now,
        )
        assertEquals(listOf(RecapPeriod.Week(LocalDate.parse("2026-10-05"))), plan.oldLayout)
        assertTrue(RecapBackfill.queue(plan, includeOldLayout = false, includeThisYear = false).isEmpty())
        assertEquals(plan.oldLayout, RecapBackfill.queue(plan, includeOldLayout = true, includeThisYear = false))
    }

    @Test
    fun `everything is written oldest first, a week before the month it closes and the month before the year`() {
        val periods = listOf(
            RecapPeriod.Year(2025),
            RecapPeriod.Month(java.time.YearMonth.of(2025, 12)),
            RecapPeriod.Week(LocalDate.parse("2025-12-29")), // ends 4 January 2026
            RecapPeriod.Week(LocalDate.parse("2025-12-22")), // ends 28 December
            RecapPeriod.Month(java.time.YearMonth.of(2025, 11)),
            RecapPeriod.Week(LocalDate.parse("2025-11-24")), // ends 30 November, with the month
        )
        assertEquals(
            listOf(
                RecapPeriod.Week(LocalDate.parse("2025-11-24")),
                RecapPeriod.Month(java.time.YearMonth.of(2025, 11)),
                RecapPeriod.Week(LocalDate.parse("2025-12-22")),
                RecapPeriod.Month(java.time.YearMonth.of(2025, 12)),
                RecapPeriod.Year(2025),
                RecapPeriod.Week(LocalDate.parse("2025-12-29")),
            ),
            RecapBackfill.ordered(periods),
        )
    }

    @Test
    fun `the queue has no doubles`() {
        val dates = weeksWithThreeDays("2026-01-05", "2026-02-02", "2026-03-02")
        val plan = RecapBackfill.plan(dates, mapOf("year-2026" to Recap.LAYOUT_V1), now)
        val queue = RecapBackfill.queue(plan, includeOldLayout = true, includeThisYear = true)
        assertEquals(queue.map { it.key }.distinct(), queue.map { it.key })
        assertTrue(queue.zipWithNext().all { (a, b) -> !a.end.isAfter(b.end) })
    }

    @Test
    fun `the counts read as a sentence`() {
        val plan = RecapBackfill.plan(weeksWithThreeDays("2026-09-07", "2026-09-14", "2026-10-05"), emptyMap(), now)
        assertEquals("3 weeks and 2 months", RecapBackfill.describe(plan))
    }

    @Test
    fun `with no entries there is nothing to do`() {
        assertTrue(RecapBackfill.plan(emptyList(), emptyMap(), now).isEmpty)
    }
}
