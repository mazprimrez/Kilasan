package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryTag
import com.onedayonesecond.data.Mood
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A stretch of days, both ends included. */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "$end is before $start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val length: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    fun dates(): List<LocalDate> = (0 until length).map { start.plusDays(it.toLong()) }
}

/** Which days a Patterns screen covers, and which days it is compared with. */
object PatternSpans {

    /** The days of [period] up to [today]; null for a period that has not begun. */
    fun visible(period: RecapPeriod, today: LocalDate): DateSpan? {
        if (period.start.isAfter(today)) return null
        return DateSpan(period.start, minOf(period.end, today))
    }

    /**
     * The same span of the period before: a whole period for a whole one, and for one
     * still going the same days of the one before ("1–16 Oct" for "1–16 Nov"), so a
     * partial period is never compared with a whole one. A day that the period before
     * does not have (31 Oct → September) is cut to its last day.
     */
    fun previous(period: RecapPeriod, visible: DateSpan): DateSpan {
        val before = period.previous()
        if (visible.end == period.end) return DateSpan(before.start, before.end)
        val end = when (period) {
            is RecapPeriod.Week -> before.start.plusDays(ChronoUnit.DAYS.between(period.start, visible.end))
            is RecapPeriod.Month -> {
                val month = (before as RecapPeriod.Month).month
                month.atDay(minOf(visible.end.dayOfMonth, month.lengthOfMonth()))
            }
            // minusYears turns 29 February into the 28th.
            is RecapPeriod.Year -> visible.end.minusYears(1)
        }
        return DateSpan(before.start, end)
    }
}

/** An average of mood scores (1 rough to 5 great) over [days] days. */
data class MoodAverage(val average: Double, val days: Int) {
    /** The mood the average rounds to, halves rounding up (3.5 is good). */
    val mood: Mood get() = SinceLast.moodOf(average)

    companion object {
        fun of(moods: Collection<Mood>): MoodAverage? =
            if (moods.isEmpty()) null else MoodAverage(moods.map { it.score }.average(), moods.size)
    }
}

/**
 * A topic, habit or person and the days it came up on in the span. [previousDays] is
 * the same count over the same span of the period before.
 */
data class TagCount(
    val name: String,
    val days: List<LocalDate>,
    val previousDays: Int,
) {
    val count: Int get() = days.size

    /** Days it came up on per weekday, Monday first. */
    val byWeekday: Map<DayOfWeek, Int>
        get() = DayOfWeek.entries.associateWith { weekday -> days.count { it.dayOfWeek == weekday } }

    /** Days it came up on per month, January first (12 numbers). */
    val byMonth: List<Int>
        get() = (1..12).map { month -> days.count { it.monthValue == month } }
}

/** A habit with the average mood on the days it was done and on the other days with a mood. */
data class HabitCount(val tag: TagCount, val onHabitDays: MoodAverage?, val otherDays: MoodAverage?)

enum class Versus { ABOVE, BELOW, SAME }

/** The average mood on the days a topic came up, against the usual mood of the span. */
data class TopicMood(val name: String, val days: Int, val mood: MoodAverage, val versusUsual: Versus)

/** The mood of one weekday or one month. [average] is null when it rests on fewer than 2 days ("Too few"). */
data class MoodBucket(val key: Int, val days: Int, val average: MoodAverage?)

enum class DayPart(val label: String, val hours: String) {
    MORNING("Morning", "05–11"),       // 05:00–10:59
    AFTERNOON("Afternoon", "11–17"),   // 11:00–16:59
    EVENING("Evening", "17–22"),       // 17:00–21:59
    NIGHT("Late night", "22–05"),      // 22:00–04:59
    ;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..10 -> MORNING
            in 11..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** Entries per part of the day over [months] (1 = January). */
data class DayParts(val months: IntRange, val counts: Map<DayPart, Int>)

data class PlaceMood(val name: String, val days: Int, val mood: MoodAverage?)

data class WordCount(val word: String, val count: Int, val days: Int)

/** Everything a Patterns screen shows, counted on the phone. */
data class Patterns(
    val period: RecapPeriod,
    val span: DateSpan,
    val previousSpan: DateSpan,
    val daysWithEntries: Int,
    val entryCount: Int,
    /** The days of the span with at least one entry. */
    val writtenDays: Set<LocalDate>,
    val previousDaysWithEntries: Int,
    val previousEntryCount: Int,
    /** The mood of each day of the span that has one. */
    val moods: Map<LocalDate, Mood>,
    /** The average mood of the span: what "usual" means on this screen. */
    val usualMood: MoodAverage?,
    val previousUsualMood: MoodAverage?,
    val topics: List<TagCount>,
    val habits: List<HabitCount>,
    /** Empty when Settings says not to show people. */
    val people: List<TagCount>,
    val moodAndTopics: List<TopicMood>,
    val moodByWeekday: List<MoodBucket>,
    val moodByMonth: List<MoodBucket>,
    val dayParts: DayParts,
    /** A year only: January–June and July onwards, when the span reaches July. */
    val dayPartHalves: List<DayParts>,
    val places: List<PlaceMood>,
    val words: List<WordCount>,
    val sinceLast: List<SinceLast.Line>,
) {
    /** Patterns are shown once something came up on at least [PatternsCalculator.MIN_DAYS] days. */
    val hasEnough: Boolean get() = topics.isNotEmpty() || habits.isNotEmpty() || people.isNotEmpty()
}

/**
 * Works out a Patterns screen from a period's entries, moods, places and tags. Pure
 * Kotlin with no Android types, so every rule is unit-tested on a plain JVM:
 *
 * - every count covers exactly [Patterns.span];
 * - comparisons use the same span of the period before ([PatternSpans.previous]);
 * - moods are averages of 1–5 scores, rounded half up;
 * - nothing resting on fewer than [MIN_DAYS] days is shown;
 * - a tag counts only while it describes its entry's words as they are now.
 *
 * @param zone the device's time zone, for the time of day an entry was made.
 */
class PatternsCalculator(private val zone: ZoneId = ZoneId.systemDefault()) {

    /** What the screen is worked out from. It may hold more days than the period; they are filtered here. */
    data class Input(
        val entries: List<Entry>,
        val moods: Map<LocalDate, Mood>,
        val places: Map<LocalDate, String>,
        val tags: List<EntryTag>,
    )

    fun calculate(period: RecapPeriod, today: LocalDate, input: Input, includePeople: Boolean = true): Patterns? {
        val span = PatternSpans.visible(period, today) ?: return null
        val previous = PatternSpans.previous(period, span)

        val entries = input.entries.filter { it.date in span }
        val previousEntries = input.entries.filter { it.date in previous }
        val moods = input.moods.filterKeys { it in span }
        val previousMoods = input.moods.filterKeys { it in previous }
        val usual = MoodAverage.of(moods.values)

        val validTags = validTags(input)
        fun counts(kind: String): List<TagCount> = countTags(kind, validTags, span, previous)

        val topics = counts(EntryTag.KIND_TOPIC)
        val habits = counts(EntryTag.KIND_HABIT)
        val people = if (includePeople) counts(EntryTag.KIND_PERSON) else emptyList()

        return Patterns(
            period = period,
            span = span,
            previousSpan = previous,
            daysWithEntries = entries.map { it.date }.distinct().size,
            entryCount = entries.size,
            writtenDays = entries.map { it.date }.toSet(),
            previousDaysWithEntries = previousEntries.map { it.date }.distinct().size,
            previousEntryCount = previousEntries.size,
            moods = moods,
            usualMood = usual,
            previousUsualMood = MoodAverage.of(previousMoods.values),
            topics = topics.take(MAX_TOPICS),
            habits = habits.take(MAX_HABITS).map { habit ->
                val on = habit.days.toSet()
                HabitCount(
                    tag = habit,
                    onHabitDays = MoodAverage.of(moods.filterKeys { it in on }.values)?.takeIf { it.days >= MIN_DAYS },
                    otherDays = MoodAverage.of(moods.filterKeys { it !in on }.values)?.takeIf { it.days >= MIN_DAYS },
                )
            },
            people = people.take(MAX_PEOPLE),
            moodAndTopics = moodAndTopics(topics, moods, usual),
            moodByWeekday = DayOfWeek.entries.map { weekday ->
                bucket(weekday.value, moods.filterKeys { it.dayOfWeek == weekday }.values)
            },
            moodByMonth = if (period is RecapPeriod.Year) {
                (1..span.end.monthValue).map { month -> bucket(month, moods.filterKeys { it.monthValue == month }.values) }
            } else {
                emptyList()
            },
            dayParts = dayParts(entries, span.start.monthValue..span.end.monthValue),
            dayPartHalves = if (period is RecapPeriod.Year) halves(entries, span) else emptyList(),
            places = places(input.places.filterKeys { it in span }, moods),
            words = words(entries, if (includePeople) emptySet() else namesOf(validTags, EntryTag.KIND_PERSON)),
            sinceLast = listOfNotNull(
                SinceLast.change("Days with entries", previousEntries.map { it.date }.distinct().size, entries.map { it.date }.distinct().size),
                SinceLast.change("Entries", previousEntries.size, entries.size),
                SinceLast.moodLine(MoodAverage.of(previousMoods.values)?.average, usual?.average),
            ),
        )
    }

    /** A tag with the day it belongs to. */
    private data class DayTag(val date: LocalDate, val kind: String, val value: String)

    /** Tags whose entry is still there and still has the words they were made from. */
    private fun validTags(input: Input): List<DayTag> {
        val entries = input.entries.associateBy { it.id }
        val hashes = HashMap<Long, String?>()
        return input.tags.mapNotNull { tag ->
            if (tag.kind == EntryTag.KIND_NONE || tag.value.isBlank()) return@mapNotNull null
            val entry = entries[tag.entryId] ?: return@mapNotNull null
            val hash = hashes.getOrPut(entry.id) { TagHash.of(entry.text) }
            if (hash != tag.sourceHash) null else DayTag(entry.date, tag.kind, tag.value.trim())
        }
    }

    private fun countTags(kind: String, tags: List<DayTag>, span: DateSpan, previous: DateSpan): List<TagCount> {
        val ofKind = tags.filter { it.kind == kind }
        // Names are compared ignoring case; the spelling used most is the one shown.
        val groups = ofKind.groupBy { it.value.lowercase() }
        return groups.values.map { group ->
            val name = group.groupingBy { it.value }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .first().key
            TagCount(
                name = name,
                days = group.filter { it.date in span }.map { it.date }.distinct().sorted(),
                previousDays = group.filter { it.date in previous }.map { it.date }.distinct().size,
            )
        }
            .filter { it.count >= MIN_DAYS }
            .sortedWith(byCountThenName { it.name to it.count })
    }

    private fun namesOf(tags: List<DayTag>, kind: String): Set<String> =
        tags.filter { it.kind == kind }.flatMap { tokens(it.value) }.toSet()

    private fun moodAndTopics(topics: List<TagCount>, moods: Map<LocalDate, Mood>, usual: MoodAverage?): List<TopicMood> {
        if (usual == null) return emptyList()
        return topics.mapNotNull { topic ->
            val average = MoodAverage.of(topic.days.mapNotNull { moods[it] }) ?: return@mapNotNull null
            if (average.days < MIN_DAYS) return@mapNotNull null
            TopicMood(topic.name, topic.count, average, versus(average.average, usual.average))
        }
            // Lightest to heaviest: the best average first; ties by days, then name.
            .sortedWith(
                compareByDescending<TopicMood> { it.mood.average }
                    .thenByDescending { it.days }
                    .thenBy { it.name.lowercase() },
            )
            .take(MAX_TOPICS)
    }

    private fun bucket(key: Int, moods: Collection<Mood>): MoodBucket =
        MoodBucket(key, moods.size, MoodAverage.of(moods)?.takeIf { it.days >= MIN_DAYS })

    private fun hourOf(entry: Entry): Int = Instant.ofEpochMilli(entry.createdAt).atZone(zone).hour

    private fun dayParts(entries: List<Entry>, months: IntRange): DayParts {
        val counts = entries.groupingBy { DayPart.of(hourOf(it)) }.eachCount()
        return DayParts(months, DayPart.entries.associateWith { counts[it] ?: 0 })
    }

    private fun halves(entries: List<Entry>, span: DateSpan): List<DayParts> {
        val last = span.end.monthValue
        val first = dayParts(entries.filter { it.date.monthValue <= 6 }, 1..minOf(6, last))
        if (last < 7) return listOf(first)
        return listOf(first, dayParts(entries.filter { it.date.monthValue >= 7 }, 7..last))
    }

    private fun places(places: Map<LocalDate, String>, moods: Map<LocalDate, Mood>): List<PlaceMood> =
        places.filterValues { it.isNotBlank() }.entries
            .groupBy({ it.value.trim() }, { it.key })
            .map { (name, days) ->
                PlaceMood(name, days.size, MoodAverage.of(days.mapNotNull { moods[it] })?.takeIf { it.days >= MIN_DAYS })
            }
            .filter { it.days >= MIN_DAYS }
            .sortedWith(byCountThenName { it.name to it.days })
            .take(MAX_PLACES)

    private fun words(entries: List<Entry>, hidden: Set<String>): List<WordCount> {
        val count = HashMap<String, Int>()
        val days = HashMap<String, MutableSet<LocalDate>>()
        for (entry in entries) {
            for (word in tokens(entry.text.orEmpty())) {
                if (word.length < MIN_WORD || word in StopWords.ALL || word in hidden || word.all { it.isDigit() }) continue
                count[word] = (count[word] ?: 0) + 1
                days.getOrPut(word) { HashSet() } += entry.date
            }
        }
        return count.entries
            .filter { (days[it.key]?.size ?: 0) >= MIN_DAYS }
            .map { WordCount(it.key, it.value, days.getValue(it.key).size) }
            .sortedWith(byCountThenName { it.word to it.count })
            .take(MAX_WORDS)
    }

    companion object {
        /** Nothing that rests on fewer days than this is shown. */
        const val MIN_DAYS = 2

        const val MAX_TOPICS = 8
        const val MAX_HABITS = 5
        const val MAX_PEOPLE = 8
        const val MAX_PLACES = 6
        const val MAX_WORDS = 12
        private const val MIN_WORD = 3

        /**
         * How two averages compare. Equal when they round to the same tenth, so 3.62
         * and 3.58 are not called higher and lower.
         */
        fun versus(value: Double, usual: Double): Versus {
            val a = Math.round(value * 10)
            val b = Math.round(usual * 10)
            return when {
                a > b -> Versus.ABOVE
                a < b -> Versus.BELOW
                else -> Versus.SAME
            }
        }

        /** Most first; a tie in alphabetical order, ignoring case. */
        fun <T> byCountThenName(key: (T) -> Pair<String, Int>): Comparator<T> =
            compareByDescending<T> { key(it).second }.thenBy { key(it).first.lowercase() }

        /** The names that share the highest count: every one of them is drawn in Rose. */
        fun <T> tiedTop(items: List<T>, count: (T) -> Int): List<T> {
            val top = items.maxOfOrNull(count) ?: return emptyList()
            return items.filter { count(it) == top }
        }

        /** Lower-case words: letters (and digits) only, apostrophes and hyphens kept inside a word. */
        fun tokens(text: String): List<String> =
            Regex("[\\p{L}\\p{N}]+(?:['-][\\p{L}\\p{N}]+)*")
                .findAll(text.lowercase().replace('’', '\''))
                .map { it.value }
                .toList()
    }
}
