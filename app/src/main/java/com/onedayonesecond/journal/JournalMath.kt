package com.onedayonesecond.journal

import com.onedayonesecond.data.Mood
import java.time.DayOfWeek
import java.time.LocalDate

/** One day as the recaps and pictures see it. */
data class DaySummary(
    val date: LocalDate,
    val hasClip: Boolean = false,
    val caption: String = "",
    val mood: Mood? = null,
    val place: String? = null,
    /** The words of the day's entries, oldest first. Empty text is left out. */
    val texts: List<String> = emptyList(),
    /** How many entries of any kind the day has, including photos with no words. */
    val entryCount: Int = 0,
)

/** The numbers a recap compares with the period before. */
data class PeriodStats(
    val daysKept: Int,
    val daysWritten: Int,
    val words: Int,
    /** Mean of [Mood.score] over the days that have a mood, or null if none do. */
    val averageMood: Double?,
) {
    companion object {
        fun of(days: Collection<DaySummary>): PeriodStats {
            val moods = days.mapNotNull { it.mood?.score }
            return PeriodStats(
                daysKept = days.count { it.hasClip },
                daysWritten = days.count { it.entryCount > 0 },
                words = days.sumOf { day -> day.texts.sumOf(::countWords) },
                averageMood = if (moods.isEmpty()) null else moods.average(),
            )
        }

        fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}

/** Pure helpers shared by the recap screens and the year-in-pixels picture. */
object JournalMath {

    /**
     * The city part of a place such as "Kemang, Jakarta Selatan": what follows the last
     * comma. Days are counted per city, not per neighbourhood.
     */
    fun city(place: String): String = place.substringAfterLast(',').trim().ifEmpty { place.trim() }

    /** Days spent in each city, most days first, ties in alphabetical order. */
    fun daysPerCity(places: Collection<String?>): List<PlaceDays> =
        places.filterNotNull().filter { it.isNotBlank() }
            .groupingBy(::city).eachCount()
            .map { PlaceDays(it.key, it.value) }
            .sortedWith(compareByDescending<PlaceDays> { it.days }.thenBy { it.name })

    /** How many days have each mood, for the legend. Moods nobody chose count as zero. */
    fun moodCounts(moods: Collection<Mood?>): Map<Mood, Int> =
        Mood.entries.associateWith { mood -> moods.count { it == mood } }

    /**
     * Makes sure a recap's quote really is the user's own words. The model is asked
     * for a sentence copied from an entry, but it may tidy or invent one, and a letter
     * that puts words in the writer's mouth is worse than one with a plainer quote.
     *
     * @return the quote and the day it was written, or null if no entry contains it.
     */
    fun verifyQuote(quote: String, days: Collection<DaySummary>, claimedDate: LocalDate?): Pair<String, LocalDate>? {
        val wanted = squash(quote.trim().trim('"', '“', '”', '\''))
        if (wanted.length < MIN_QUOTE_LENGTH) return null
        val ordered = days.sortedBy { if (it.date == claimedDate) 0 else 1 }
        for (day in ordered) {
            for (text in day.texts) {
                val squashed = squash(text)
                val at = squashed.indexOf(wanted, ignoreCase = true)
                // Returned as the entry spells it, not as the model did.
                if (at >= 0) return squashed.substring(at, at + wanted.length) to day.date
            }
        }
        return null
    }

    /**
     * A quote to fall back on when the model's is not really in the entries: the first
     * sentence of reasonable length from the longest entry.
     */
    fun fallbackQuote(days: Collection<DaySummary>): Pair<String, LocalDate>? {
        val candidates = days.flatMap { day -> day.texts.map { squash(it) to day.date } }
            .sortedByDescending { it.first.length }
        for ((text, date) in candidates) {
            val sentence = text.split(Regex("(?<=[.!?…])\\s+"))
                .map { it.trim() }
                .firstOrNull { it.length in MIN_QUOTE_LENGTH..MAX_QUOTE_LENGTH }
            if (sentence != null) return sentence to date
        }
        val (text, date) = candidates.firstOrNull { it.first.length >= MIN_QUOTE_LENGTH } ?: return null
        return text.take(MAX_QUOTE_LENGTH).trimEnd() to date
    }

    /**
     * On how many days [name] appears in what was written, as a whole word and ignoring
     * case ("Ibu" counts in "sama ibu", not in "ibukota"). Counted on the phone from the
     * full entries, so a person's count is a fact and not the model's estimate.
     */
    fun daysMentioning(name: String, days: Collection<DaySummary>): Int {
        val word = name.trim()
        if (word.isEmpty()) return 0
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])",
            setOf(RegexOption.IGNORE_CASE),
        )
        return days.count { day -> day.texts.any { pattern.containsMatchIn(it) } }
    }

    /**
     * The usual mood of each weekday over a month or a year: the average of that
     * weekday's moods, rounded the way "Since last …" rounds. Null for a weekday without
     * any mood.
     */
    fun moodByWeekday(moods: Map<LocalDate, Mood>): Map<DayOfWeek, Mood?> =
        DayOfWeek.entries.associateWith { weekday ->
            val scores = moods.filterKeys { it.dayOfWeek == weekday }.values.map { it.score }
            if (scores.isEmpty()) null else SinceLast.moodOf(scores.average())
        }

    private fun squash(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private const val MIN_QUOTE_LENGTH = 8
    private const val MAX_QUOTE_LENGTH = 160
}

/** Turns what the phone's geocoder knows about a spot into a short name such as "Kemang, Jakarta Selatan". */
object PlaceNames {

    /**
     * @param neighbourhood the smallest named area (sub-locality), if known.
     * @param district the next one up (locality), used when there is no neighbourhood.
     * @param city the city or regency (sub-admin area), falling back to [region].
     */
    fun short(neighbourhood: String?, district: String?, city: String?, region: String?): String? {
        val small = tidy(neighbourhood) ?: tidy(district)
        val large = tidy(city) ?: tidy(district)?.takeIf { it != small } ?: tidy(region)
        return listOfNotNull(small, large?.takeIf { !it.equals(small, ignoreCase = true) })
            .joinToString(", ")
            .ifEmpty { null }
    }

    /** Geocoders in Indonesia prefix administrative levels ("Kota Jakarta Selatan"); people do not. */
    private fun tidy(name: String?): String? =
        name?.trim()
            ?.replace(Regex("^(Kota|Kabupaten|Kecamatan|Kelurahan|Daerah Khusus Ibukota)\\s+", RegexOption.IGNORE_CASE), "")
            ?.ifEmpty { null }
}

/** One cell of the year-in-pixels grid. */
enum class PixelKind {
    /** A day with a mood: filled with the mood's colour. */
    MOOD,

    /** A day that has happened but has no mood: an empty outline. */
    EMPTY,

    /** A day still to come: drawn faintly. */
    FUTURE,

    /** Not a day at all, such as 30 February: nothing is drawn. */
    ABSENT,
}

data class Pixel(val kind: PixelKind, val mood: Mood? = null)

/**
 * The year-in-pixels grid: one column per month, one row per day of the month. Shared
 * by the screen and the picture that is saved to the gallery, so they cannot disagree.
 */
object Pixels {
    const val MONTHS = 12
    const val DAYS = 31

    /** @param month 1 to 12. @param day 1 to 31. */
    fun at(year: Int, month: Int, day: Int, moods: Map<LocalDate, Mood>, today: LocalDate): Pixel {
        if (day > java.time.YearMonth.of(year, month).lengthOfMonth()) return Pixel(PixelKind.ABSENT)
        val date = LocalDate.of(year, month, day)
        val mood = moods[date]
        return when {
            mood != null -> Pixel(PixelKind.MOOD, mood)
            date.isAfter(today) -> Pixel(PixelKind.FUTURE)
            else -> Pixel(PixelKind.EMPTY)
        }
    }
}
