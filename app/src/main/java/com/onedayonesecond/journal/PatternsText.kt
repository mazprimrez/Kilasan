package com.onedayonesecond.journal

import com.onedayonesecond.data.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * How Patterns writes its dates. One place, so the navigator, the counted line and the
 * comparisons always agree: "9–15 November 2026", "1–16 November 2026",
 * "January–16 November 2026".
 */
object PatternsLabels {

    private val locale: Locale get() = Locale.getDefault()

    private fun month(date: LocalDate, style: TextStyle = TextStyle.FULL) = date.month.getDisplayName(style, locale)

    /** The period navigator: "16–22 November 2026", "26 October–1 November 2026", "January–24 November 2026". */
    fun range(period: RecapPeriod, span: DateSpan): String = when (period) {
        is RecapPeriod.Year ->
            if (span.end == period.end) {
                "${month(span.start)}–${month(span.end)} ${span.end.year}"
            } else {
                "${month(span.start)}–${span.end.dayOfMonth} ${month(span.end)} ${span.end.year}"
            }
        else -> {
            val (start, end) = span.start to span.end
            when {
                start.year != end.year -> "${day(start)} ${start.year}–${day(end)} ${end.year}"
                start.month != end.month -> "${day(start)}–${day(end)} ${end.year}"
                else -> "${start.dayOfMonth}–${day(end)} ${end.year}"
            }
        }
    }

    /** The start of the counted line: "From 16 to 22 November", "From 1 January to 24 November". */
    fun from(span: DateSpan): String {
        val (start, end) = span.start to span.end
        return when {
            start == end -> "On ${day(start)}"
            start.month == end.month && start.year == end.year -> "From ${start.dayOfMonth} to ${day(end)}"
            start.year == end.year -> "From ${day(start)} to ${day(end)}"
            else -> "From ${day(start)} ${start.year} to ${day(end)} ${end.year}"
        }
    }

    /** What a comparison is against, short: "9–15 Nov", "1–24 Oct", "Jan–24 Nov 2025". */
    fun short(period: RecapPeriod, span: DateSpan): String {
        val (start, end) = span.start to span.end
        fun d(date: LocalDate) = "${date.dayOfMonth} ${month(date, TextStyle.SHORT)}"
        return when {
            period is RecapPeriod.Year && span.length >= 365 -> start.year.toString()
            period is RecapPeriod.Year -> "${month(start, TextStyle.SHORT)}–${d(end)} ${end.year}"
            start == end -> d(start)
            start.month == end.month -> "${start.dayOfMonth}–${d(end)}"
            else -> "${d(start)}–${d(end)}"
        }
    }

    /** "You, Saturday 14 November". */
    fun quoteSource(date: LocalDate): String =
        "You, ${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${day(date)}"

    /** "The week in short", "November so far, in short", "The year so far, in short", "2025, in short". */
    fun inShort(period: RecapPeriod, span: DateSpan): String {
        val whole = span.end == period.end
        return when (period) {
            is RecapPeriod.Week -> "The week in short"
            is RecapPeriod.Month -> if (whole) "${month(period.start)}, in short" else "${month(period.start)} so far, in short"
            is RecapPeriod.Year -> if (whole) "${period.year}, in short" else "The year so far, in short"
        }
    }

    /** What a footer link or the empty state's button calls a period: "November’s", "last week’s", "2025’s". */
    fun possessive(period: RecapPeriod, today: LocalDate): String = when (period) {
        is RecapPeriod.Week -> if (RecapPeriod.weekOf(today) == period.next()) "last week’s" else "${range(period, DateSpan(period.start, period.end))}’s"
        is RecapPeriod.Month -> "${month(period.start)}’s"
        is RecapPeriod.Year -> "${period.year}’s"
    }

    fun weekday(day: DayOfWeek, style: TextStyle = TextStyle.FULL): String = day.getDisplayName(style, locale)

    fun monthName(month: Int, style: TextStyle = TextStyle.FULL): String = Month.of(month).getDisplayName(style, locale)

    /** "14 November". */
    fun day(date: LocalDate): String = "${date.dayOfMonth} ${month(date)}"
}

/**
 * Every sentence and heading on a Patterns screen, chosen from templates by the numbers.
 * The model writes none of them. A heading states the finding; it is only as strong as
 * the numbers: moods are always averages, and nothing claims that one thing caused
 * another. Wording follows the Patterns boards of the Kilasan design canvas.
 */
object PatternsWords {

    const val NOT_PROOF = "Showing up on the same day isn't proof that one caused the other."

    fun footer(period: RecapPeriod): String = when (period) {
        is RecapPeriod.Week -> "A week is short: patterns here are single days, not habits. The month view shows what repeats."
        is RecapPeriod.Month -> "Patterns need at least 2 days to show. Counts update when you edit an entry."
        is RecapPeriod.Year -> "Patterns are counts from your own entries, not conclusions about you."
    }

    fun mood(mood: Mood): String = mood.label.lowercase()

    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    private fun entries(n: Int): String = if (n == 1) "1 entry" else "$n entries"

    /** "Work", "Work and Family", "Work, Family and Gym". */
    fun names(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** A name inside a sentence: "reading", but "Tech Cantik" and "GBK" keep their capitals. */
    fun inline(name: String): String {
        val words = name.split(" ")
        val plain = words.all { w -> w.drop(1).all { !it.isUpperCase() } } && words.drop(1).all { w -> w.firstOrNull()?.isLowerCase() != false }
        return if (plain) name.replaceFirstChar { it.lowercase() } else name
    }

    private fun weekday(date: LocalDate) = PatternsLabels.weekday(date.dayOfWeek)

    /** The 1–2 sentences that open the screen. */
    fun summary(p: Patterns): String {
        val top = PatternsCalculator.tiedTop(p.topics) { it.count }
        val first = when {
            top.size == 1 && p.daysWithEntries > 0 ->
                "${top[0].name} came up most, on ${top[0].count} of your ${days(p.daysWithEntries)} with entries."
            top.size > 1 -> "${names(top.take(3).map { it.name })} came up most, on ${days(top[0].count)} each."
            p.daysWithEntries > 0 -> "You wrote on ${days(p.daysWithEntries)}."
            else -> "Nothing written yet."
        }
        val second = p.usualMood?.let { " Your average mood was ${mood(it.mood)}." }.orEmpty()
        return first + second
    }

    /** "From 16 to 22 November: 6 days with entries, 14 entries. Counted on your phone." */
    fun counted(p: Patterns): String =
        "${PatternsLabels.from(p.span)}: ${days(p.daysWithEntries)} with entries, ${entries(p.entryCount)}. Counted on your phone."

    /** Under "Patterns show up after a few days". */
    fun empty(p: Patterns): String {
        val stillGoing = p.span.end != p.period.end
        val count = if (p.daysWithEntries == 0) {
            "Nothing written ${if (stillGoing) "yet " else ""}this ${p.period.noun}."
        } else if (stillGoing) {
            "You have ${days(p.daysWithEntries)} with entries so far this ${p.period.noun}."
        } else {
            "This ${p.period.noun} has ${days(p.daysWithEntries)} with entries."
        }
        return "$count Keep adding seconds and entries; topics, habits and people appear once something comes up on 2 or more days."
    }

    /**
     * A section: its heading (the finding), its label (a Rose name, then " · " and a
     * Muted line saying what the chart counts), the sentence after the chart, and a
     * smaller footnote under it.
     */
    data class Section(val heading: String, val label: String, val sentence: String? = null, val footnote: String? = null)

    private fun than(p: Patterns) = PatternsLabels.short(p.period, p.previousSpan)

    /** "Tuesday to Thursday", "Monday and Wednesday"; null when there are too many to name. */
    private fun dayRun(days: List<LocalDate>): String? {
        val sorted = days.sorted()
        if (sorted.isEmpty()) return null
        val consecutive = sorted.zipWithNext().all { (a, b) -> b == a.plusDays(1) }
        return when {
            sorted.size == 1 -> weekday(sorted[0])
            consecutive -> "${weekday(sorted.first())} to ${weekday(sorted.last())}"
            sorted.size <= 3 -> names(sorted.map(::weekday))
            else -> null
        }
    }

    fun glance(p: Patterns): Section {
        // The busiest topic and habit whose days can be named as a run.
        val topic = p.topics.firstOrNull { dayRun(it.days) != null }
        val habit = p.habits.map { it.tag }.firstOrNull { dayRun(it.days) != null }
        val parts = listOfNotNull(topic, habit).distinctBy { it.name }.mapNotNull { tag ->
            dayRun(tag.days)?.let { run -> tag to run }
        }
        val heading = when {
            parts.isEmpty() -> {
                val top = (p.topics + p.habits.map { it.tag } + p.people).maxByOrNull { it.count }
                top?.let { "${it.name} came up on ${it.count} of ${days(p.span.length)}" } ?: "Nothing came up twice yet"
            }
            else -> parts.mapIndexed { i, (tag, run) -> "${if (i == 0) tag.name else inline(tag.name)} $run" }.joinToString(", ")
        }
        return Section(heading, "Your week · a dot means it came up that day. Tap a day to open it.")
    }

    /** The week's mood: where it was lowest and highest. Null with fewer than 2 days with a mood. */
    fun weekMood(p: Patterns): Section? {
        if (p.moods.size < PatternsCalculator.MIN_DAYS) return null
        val low = p.moods.entries.sortedWith(compareBy({ it.value.score }, { it.key })).first()
        val high = p.moods.entries.sortedWith(compareBy({ -it.value.score }, { it.key })).first()
        val heading: String
        val label: String
        if (low.value == high.value) {
            heading = "Your mood stayed ${mood(low.value)} all week"
            label = "Mood · ${mood(low.value)} on every day with a mood."
        } else if (low.key.isBefore(high.key)) {
            heading = "Your mood climbed from ${weekday(low.key)} to ${weekday(high.key)}"
            label = "Mood · from ${mood(low.value)} on ${weekday(low.key)} to ${mood(high.value)} on ${weekday(high.key)}."
        } else {
            heading = "Your mood dipped from ${weekday(high.key)} to ${weekday(low.key)}"
            label = "Mood · from ${mood(high.value)} on ${weekday(high.key)} to ${mood(low.value)} on ${weekday(low.key)}."
        }
        val habit = p.habits.firstOrNull { it.onHabitDays != null && it.otherDays != null }
        val sentence = habit?.let {
            "Your average mood on ${inline(it.tag.name)} days was ${mood(it.onHabitDays!!.mood)}, " +
                "and ${mood(it.otherDays!!.mood)} on the other days."
        }
        return Section(heading, label, sentence, habit?.let { NOT_PROOF })
    }

    fun topics(p: Patterns): Section {
        val top = PatternsCalculator.tiedTop(p.topics) { it.count }
        val heading = when (top.size) {
            0 -> "No topic came up twice yet"
            1 -> "${top[0].name} came up most, on ${days(top[0].count)}"
            else -> "${names(top.take(3).map { it.name })} came up most, on ${days(top[0].count)} each"
        }
        val first = p.topics.firstOrNull()
        val sentence = first?.let { compare(it.name, it.count, it.previousDays, than(p)) }
        return Section(heading, "Topics · days each topic came up. Tap one to see its weekdays.", sentence)
    }

    /** Under a topic's bar: "3 more than 1–16 Oct", "1 fewer than 1–16 Oct", "Same as 1–16 Oct", "Not in 1–16 Oct". */
    fun versusBefore(now: Int, before: Int, than: String): String = when {
        before == 0 -> "Not in $than"
        now > before -> "${now - before} more than $than"
        now < before -> "${before - now} fewer than $than"
        else -> "Same as $than"
    }

    /** "That's 3 more days than 1–24 Oct.", "It didn't come up in 1–24 Oct." */
    fun compare(name: String, now: Int, before: Int, than: String): String = when {
        before == 0 -> "$name didn't come up in $than."
        now > before -> "${now - before} more ${if (now - before == 1) "day" else "days"} for $name than $than (${days(before)})."
        now < before -> "${before - now} fewer ${if (before - now == 1) "day" else "days"} for $name than $than (${days(before)})."
        else -> "$name came up on as many days as $than."
    }

    /** Under a topic's weekday columns: "Most often on Monday and Thursday. It never came up on a weekend." */
    fun weekdays(byWeekday: Map<DayOfWeek, Int>): String? {
        val top = byWeekday.values.maxOrNull() ?: 0
        if (top == 0) return null
        val most = DayOfWeek.entries.filter { byWeekday[it] == top }
        val weekend = (byWeekday[DayOfWeek.SATURDAY] ?: 0) + (byWeekday[DayOfWeek.SUNDAY] ?: 0)
        val weekdays = byWeekday.filterKeys { it.value <= 5 }.values.sum()
        val first = if (most.size >= 5) "About as often on any day." else "Most often on ${names(most.map { PatternsLabels.weekday(it) })}."
        val second = when {
            weekend == 0 -> " It never came up on a weekend."
            weekdays == 0 -> " Only at weekends."
            else -> ""
        }
        return first + second
    }

    fun topicsByMonth(p: Patterns, decemberAhead: Boolean): Section {
        val top = PatternsCalculator.tiedTop(p.topics) { it.count }
        val lastMonth = p.span.end.monthValue
        val everyMonth = top.firstOrNull()?.takeIf { t -> (1..lastMonth).all { t.byMonth[it - 1] > 0 } && lastMonth > 1 }
        val heading = when {
            everyMonth != null -> "${everyMonth.name} was there every month"
            top.isEmpty() -> "No topic came up twice yet"
            top.size == 1 -> "${top[0].name} came up most, on ${days(top[0].count)}"
            else -> "${names(top.take(3).map { it.name })} came up most, on ${days(top[0].count)} each"
        }
        val sofar = if (p.span.end != p.period.end) " ${PatternsLabels.monthName(lastMonth)} is so far;" else ""
        val sentence = "Numbers are days that month. Darker means more days." +
            if (decemberAhead) "$sofar December hasn't happened yet." else ""
        return Section(heading, "Topics · days each topic came up, month by month.", footnote = sentence)
    }

    fun moodAndTopics(p: Patterns): Section {
        val usual = p.usualMood
        val above = p.moodAndTopics.firstOrNull { it.versusUsual == Versus.ABOVE }
        val below = p.moodAndTopics.lastOrNull { it.versusUsual == Versus.BELOW }
        val heading = when {
            above != null && below != null -> "${above.name} days felt lightest, ${inline(below.name)} days heaviest"
            above != null -> "${above.name} days felt lightest"
            below != null -> "${below.name} days felt heaviest"
            else -> "Your mood was about the same whatever came up"
        }
        val footnote = "Filled dots are above your usual mood across ${days(usual?.days ?: 0)}, hollow dots below. " +
            "Topics showing up on a day with a mood is not proof one caused the other."
        val sentence = usual?.let { u ->
            listOfNotNull(
                above?.let { "${it.name} days averaged ${mood(it.mood.mood)}" },
                below?.let { "${inline(it.name).replaceFirstChar { c -> if (above == null) c.uppercase() else c.toString() }} days ${mood(it.mood.mood)}" },
            ).takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "$it; your usual is ${mood(u.mood)}." }
        }
        return Section(heading, "Mood and topics · your average mood on the days each topic came up.", sentence, footnote)
    }

    fun habits(p: Patterns): Section {
        val habit = p.habits.firstOrNull()
        val year = p.period is RecapPeriod.Year
        val label = when {
            year && habit != null -> "Habits · ${inline(habit.tag.name)} days per month."
            else -> "Habits · found in your entries when you mention them."
        }
        if (habit == null) return Section("No habit came up twice yet", label)
        val name = habit.tag.name
        val on = habit.onHabitDays
        val other = habit.otherDays
        val heading = when {
            on == null || other == null -> "$name on ${days(habit.tag.count)}"
            on.mood.score > other.mood.score -> "$name days felt better"
            on.mood.score < other.mood.score -> "$name days felt harder"
            else -> "$name days felt about the same as other days"
        }
        val sentence = if (year) {
            "${days(habit.tag.count).replace("day", "${inline(name)} day")} so far."
        } else {
            weekdays(habit.tag.byWeekday)
        }
        val footnote = if (on != null && other != null) "A pattern in your entries, not proof that one caused the other." else null
        return Section(heading, label, sentence, footnote)
    }

    fun people(p: Patterns): Section {
        val top = PatternsCalculator.tiedTop(p.people) { it.count }
        val heading = when (top.size) {
            0 -> "No one came up twice yet"
            1 -> "${top[0].name} came up most, on ${days(top[0].count)}"
            else -> "${names(top.take(3).map { it.name })} came up most, on ${days(top[0].count)} each"
        }
        val label = if (p.period is RecapPeriod.Year) {
            "People · days you mentioned each person, month by month. Only you see this."
        } else {
            "People · names from your entries. Only you see this."
        }
        val first = p.people.firstOrNull()
        val sentence = first?.let { person -> weekdays(person.byWeekday)?.let { "${person.name}: ${it.replaceFirstChar { c -> c.lowercase() }}" } }
        return Section(heading, label, sentence)
    }

    fun moodByTime(p: Patterns): Section {
        val year = p.period is RecapPeriod.Year
        val buckets = if (year) p.moodByMonth else p.moodByWeekday
        val known = buckets.filter { it.average != null }
        fun name(key: Int) = if (year) PatternsLabels.monthName(key) else PatternsLabels.weekday(DayOfWeek.of(key)) + "s"
        val best = known.maxOfOrNull { it.average!!.average }
        val worst = known.minOfOrNull { it.average!!.average }
        val lightest = known.filter { it.average!!.average == best }
        val heaviest = known.filter { it.average!!.average == worst }
        val heading = when {
            known.size < 2 -> "Too few days with a mood to compare ${if (year) "months" else "weekdays"}"
            best == worst -> "Your ${if (year) "months" else "weekdays"} felt about the same on average"
            year -> "${names(lightest.map { name(it.key) })} ${if (lightest.size == 1) "was your lightest month" else "were your lightest months"}"
            else -> "${names(lightest.map { name(it.key) })} were the lightest"
        }
        val label = if (year) "Mood · average mood each month." else "Mood by weekday · average mood on each weekday and how many days it’s based on."
        val tooFew = buckets.filter { it.average == null && it.days > 0 }
        val sentence = if (known.size >= 2 && best != worst) {
            val main = if (year) {
                "${names(lightest.map { name(it.key) })} ${if (lightest.size == 1) "was" else "were"} the lightest and ${names(heaviest.map { name(it.key) })} the heaviest."
            } else {
                "${names(lightest.map { name(it.key) })} were your lightest days; ${names(heaviest.map { name(it.key) })} the heaviest."
            }
            val few = if (!year && tooFew.isNotEmpty()) {
                " ${names(tooFew.map { PatternsLabels.weekday(DayOfWeek.of(it.key)) })} ${if (tooFew.size == 1) "has" else "have"} only one day so far."
            } else {
                ""
            }
            main + few + if (year) " The number under each face is how many days it’s based on." else ""
        } else {
            null
        }
        return Section(heading, label, sentence)
    }

    fun whenYouWrite(p: Patterns): Section {
        val year = p.dayPartHalves.size == 2
        val counts = p.dayParts.counts
        val total = counts.values.sum()
        val top = counts.values.maxOrNull() ?: 0
        val tied = DayPart.entries.filter { counts[it] == top }
        val week = p.period is RecapPeriod.Week
        fun topOf(parts: DayParts): DayPart? = parts.counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        val heading = when {
            total == 0 -> "No entries yet"
            year && topOf(p.dayPartHalves[0]) != null && topOf(p.dayPartHalves[0]) != topOf(p.dayPartHalves[1]) ->
                "You moved from ${topOf(p.dayPartHalves[0])!!.label.lowercase()} to ${topOf(p.dayPartHalves[1])!!.label.lowercase()}"
            week -> {
                // The two busiest parts when they hold most entries, as the week's board says it.
                val two = DayPart.entries.sortedByDescending { counts[it] ?: 0 }.take(2)
                val both = two.sumOf { counts[it] ?: 0 }
                if (tied.size == 1 && top * 2 > total) "You wrote ${partPhrase(tied[0])}"
                else if (both * 2 > total) "You wrote ${names(two.sortedBy { it.ordinal }.map(::partPhrase))}"
                else "You wrote at all times of day"
            }
            tied.size == 1 -> "You write ${partPhrase(tied[0])}"
            else -> "You write as much ${names(tied.map(::partPhrase))}"
        }
        val after17 = (counts[DayPart.EVENING] ?: 0) + (counts[DayPart.NIGHT] ?: 0)
        val label = when {
            year -> "When · entries by time of day, first half vs second half."
            week -> "When · $after17 of ${entries(total)} came after 17:00."
            else -> "When · ${entries(total)} by time of day."
        }
        val sentence = when {
            total == 0 || week -> null
            year -> null
            tied.size == 1 -> "More of your entries came ${partPhrase(tied[0])} than at any other time."
            else -> null
        }
        return Section(heading, label, sentence)
    }

    fun partPhrase(part: DayPart): String = when (part) {
        DayPart.MORNING -> "in the morning"
        DayPart.AFTERNOON -> "in the afternoon"
        DayPart.EVENING -> "in the evening"
        DayPart.NIGHT -> "at night"
    }

    /** Topics and habits against the same days of last week: "Gym went up from 2 to 3 days." */
    fun sinceLastLines(p: Patterns): List<SinceLast.Line> {
        val tags = (p.habits.map { it.tag } + p.topics).distinctBy { it.name.lowercase() }
        fun line(t: TagCount): SinceLast.Line = when {
            t.count > t.previousDays -> SinceLast.Line(Direction.UP, "${t.name} went up from ${t.previousDays} to ${days(t.count)}.")
            t.count < t.previousDays -> SinceLast.Line(Direction.DOWN, "${t.name} went down from ${t.previousDays} to ${days(t.count)}.")
            else -> SinceLast.Line(Direction.SAME, "${t.name} stayed at ${days(t.count)}.")
        }
        val ups = tags.filter { it.count > it.previousDays }.sortedByDescending { it.count - it.previousDays }
        val same = tags.filter { it.count == it.previousDays }
        val downs = tags.filter { it.count < it.previousDays }.sortedByDescending { it.previousDays - it.count }
        return (ups + same + downs).take(4).map(::line)
    }

    fun sinceLast(p: Patterns): Section {
        val tags = (p.habits.map { it.tag } + p.topics).distinctBy { it.name.lowercase() }
        val ups = tags.filter { it.count > it.previousDays }.sortedByDescending { it.count - it.previousDays }.take(2)
        val downs = tags.filter { it.count < it.previousDays }.sortedByDescending { it.previousDays - it.count }.take(2)
        fun list(t: List<TagCount>) = names(t.mapIndexed { i, tag -> if (i == 0) tag.name else inline(tag.name) })
        val heading = when {
            ups.isNotEmpty() -> "${list(ups)} went up"
            downs.isNotEmpty() -> "${list(downs)} went down"
            else -> "About the same as last week"
        }
        val against = PatternsLabels.range(p.period, p.previousSpan).substringBeforeLast(' ')
        return Section(heading, "Since last week · compared with $against.")
    }

    fun places(p: Patterns): Section {
        val top = p.places.firstOrNull()
        val withMood = p.places.filter { it.mood != null }
        val lightest = withMood.maxByOrNull { it.mood!!.average }?.takeIf { withMood.size >= 2 }
        val sentence = lightest?.let {
            "Your ${days(it.days)} in ${it.name.substringBefore(',')} were the lightest of the ${p.period.noun}."
        }
        return Section(
            heading = top?.let { "Most days happened in ${it.name.substringBefore(',')}" } ?: "No place came up twice yet",
            label = "Places · days at each place and your average mood there.",
            sentence = sentence,
        )
    }

    fun words(p: Patterns): Section {
        val top = PatternsCalculator.tiedTop(p.words) { it.count }
        val noun = p.period.noun
        val heading = when (top.size) {
            0 -> "No word came up on 2 days yet"
            1 -> "“${top[0].word.replaceFirstChar { it.uppercase() }}” was your word of the $noun"
            else -> "${names(top.take(3).map { "“${it.word}”" })} were your words of the $noun"
        }
        return Section(heading, "Words · small words like “yang” and “the” are left out.")
    }

    /** The one sentence of the Patterns chapter at the end of a letter. */
    fun chapter(p: Patterns?): String {
        if (p == null || !p.hasEnough) return "Patterns show up after a few days."
        val top = PatternsCalculator.tiedTop(p.topics) { it.count }
        return when {
            top.size == 1 -> "${top[0].name} came up most, on ${top[0].count} of your ${days(p.daysWithEntries)} with entries."
            top.size > 1 -> "${names(top.take(3).map { it.name })} came up most, on ${days(top[0].count)} each."
            else -> summary(p)
        }
    }

    /** What a screen reader hears for a list of bars: "Work, 9 days. Family, 6 days." */
    fun bars(items: List<Pair<String, Int>>, unit: (Int) -> String = ::days): String =
        items.joinToString(" ") { (name, n) -> "$name, ${unit(n)}." }
}
