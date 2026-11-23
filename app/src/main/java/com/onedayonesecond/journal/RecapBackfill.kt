package com.onedayonesecond.journal

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Which past recaps "Write recaps for past weeks and months" would write, and in what
 * order. Pure, so the rules can be unit-tested on a plain JVM.
 *
 * Every period uses the same threshold as the nightly letters: [RecapRules.hasEnough]
 * days with entries. Only periods whose letter is already due are offered, so this
 * never writes a letter early, with one exception the user asks for by name: the
 * current year so far.
 */
object RecapBackfill {

    data class Plan(
        /** Due weeks with enough entries and no recap, oldest first. */
        val weeks: List<RecapPeriod.Week>,
        val months: List<RecapPeriod.Month>,
        /** Finished years with enough entries and no recap. */
        val years: List<RecapPeriod.Year>,
        /** Recaps written in layout 1, offered to be written again. Off unless chosen. */
        val oldLayout: List<RecapPeriod>,
        /** The current year so far, when it has enough entries. Only written when chosen. */
        val thisYear: RecapPeriod.Year?,
    ) {
        val missingCount: Int get() = weeks.size + months.size + years.size
        val isEmpty: Boolean get() = missingCount == 0 && oldLayout.isEmpty() && thisYear == null
    }

    /**
     * @param entryDates every day that has at least one entry.
     * @param recaps the layout of every recap already written, by key.
     */
    fun plan(entryDates: Collection<LocalDate>, recaps: Map<String, Int>, now: LocalDateTime): Plan {
        val dates = entryDates.toSortedSet()
        if (dates.isEmpty()) return Plan(emptyList(), emptyList(), emptyList(), emptyList(), null)
        val first = dates.first()
        val today = now.toLocalDate()

        fun enough(period: RecapPeriod) =
            RecapRules.hasEnough(dates.subSet(period.start, period.end.plusDays(1)).size)

        fun wanted(period: RecapPeriod) =
            period.key !in recaps && RecapRules.isDue(period, now) && enough(period)

        val weeks = generateSequence(RecapPeriod.weekOf(first)) { it.next() }
            .takeWhile { !it.start.isAfter(today) }
            .filter(::wanted).toList()
        val months = generateSequence(RecapPeriod.monthOf(first)) { it.next() }
            .takeWhile { !it.start.isAfter(today) }
            .filter(::wanted).toList()
        val years = (first.year..today.year).map { RecapPeriod.Year(it) }.filter(::wanted)
        val oldLayout = recaps.filterValues { it < com.onedayonesecond.data.Recap.LAYOUT_V2 }.keys
            .mapNotNull(RecapPeriod::parse)
            .let(::ordered)
        val current = RecapPeriod.Year(today.year)
        val thisYear = current.takeIf { !RecapRules.isDue(it, now) && enough(it) }
        return Plan(weeks, months, years, oldLayout, thisYear)
    }

    /** What to write, oldest first, without doubles. */
    fun queue(plan: Plan, includeOldLayout: Boolean, includeThisYear: Boolean): List<RecapPeriod> = ordered(
        buildList {
            addAll(plan.weeks)
            addAll(plan.months)
            addAll(plan.years)
            if (includeOldLayout) addAll(plan.oldLayout)
            if (includeThisYear) plan.thisYear?.let(::add)
        }.distinctBy { it.key },
    )

    /**
     * Oldest first, by the day a period ends; a week before the month it closes, a
     * month before the year. Each recap is then written after the one before it, so
     * it can pick up that recap's threads ("still going from last week").
     */
    fun ordered(periods: Collection<RecapPeriod>): List<RecapPeriod> =
        periods.sortedWith(compareBy<RecapPeriod>({ it.end }, { rank(it) }, { it.start }))

    private fun rank(period: RecapPeriod) = when (period) {
        is RecapPeriod.Week -> 0
        is RecapPeriod.Month -> 1
        is RecapPeriod.Year -> 2
    }

    /** "12 weeks, 3 months and 1 year". */
    fun describe(plan: Plan): String {
        fun part(n: Int, noun: String) = if (n == 0) null else "$n $noun${if (n == 1) "" else "s"}"
        val parts = listOfNotNull(part(plan.weeks.size, "week"), part(plan.months.size, "month"), part(plan.years.size, "year"))
        return when (parts.size) {
            0 -> "Nothing"
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }
}
