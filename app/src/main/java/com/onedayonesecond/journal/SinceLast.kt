package com.onedayonesecond.journal

import com.onedayonesecond.data.Mood
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The "Since last week" chapter, worked out on the phone from numbers the app counted.
 * The model never sees or writes these sentences, so they cannot say "went up from 7 to 7".
 */
object SinceLast {

    /** One line of the chapter. [direction] is null for a line that compares nothing. */
    data class Line(val direction: Direction?, val text: String, val mood: Mood? = null)

    sealed interface Chapter {
        /**
         * Nothing to compare with: the period before had no days kept. [heading] is
         * "Your first week in Kilasan." for a real first week, and says there was nothing
         * to compare with after a gap. [totals] are this period's own numbers.
         */
        data class Alone(val heading: String, val totals: List<Line>) : Chapter

        data class Compared(val lines: List<Line>) : Chapter
    }

    /**
     * @param noun "week", "month" or "year".
     * @param hadEarlier whether anything was kept or written before this period at all.
     */
    fun of(
        noun: String,
        current: PeriodStats,
        previous: PeriodStats?,
        hadEarlier: Boolean,
        locale: Locale = Locale.getDefault(),
    ): Chapter {
        if (previous == null || previous.daysKept == 0) {
            val heading = if (hadEarlier) "Nothing was kept last $noun to compare with." else "Your first $noun in Kilasan."
            return Chapter.Alone(heading, totals(current, locale))
        }
        val lines = listOf(
            change("Days kept", previous.daysKept, current.daysKept, locale),
            change("Days written", previous.daysWritten, current.daysWritten, locale),
            change("Words", previous.words, current.words, locale),
        ) + listOfNotNull(moodLine(previous.averageMood, current.averageMood))
        return Chapter.Compared(lines)
    }

    /** "Days kept went up from 3 to 7.", "… went down from 7 to 5.", "… stayed at 7." */
    fun change(label: String, from: Int, to: Int, locale: Locale = Locale.getDefault()): Line {
        val number = NumberFormat.getIntegerInstance(locale)
        return when {
            to > from -> Line(Direction.UP, "$label went up from ${number.format(from)} to ${number.format(to)}.")
            to < from -> Line(Direction.DOWN, "$label went down from ${number.format(from)} to ${number.format(to)}.")
            else -> Line(Direction.SAME, "$label stayed at ${number.format(to)}.")
        }
    }

    /**
     * Mood is compared by the word its average rounds to, not by its decimals: 3.6 and
     * 3.9 are both "good", and saying it went up between them would be noise. It is
     * always called an average: an average of good does not mean most days were good.
     */
    fun moodLine(from: Double?, to: Double?): Line? {
        val now = to?.let(::moodOf) ?: return null
        val before = from?.let(::moodOf) ?: return Line(null, "Your average mood was ${moodWord(now)}.", now)
        return when {
            now.score > before.score -> Line(Direction.UP, "Your average mood was ${moodWord(now)}, up from ${moodWord(before)}.", now)
            now.score < before.score -> Line(Direction.DOWN, "Your average mood was ${moodWord(now)}, down from ${moodWord(before)}.", now)
            else -> Line(Direction.SAME, "Your average mood stayed ${moodWord(now)}.", now)
        }
    }

    /** The mood an average rounds to, halves rounding up: 3.5 is good. */
    fun moodOf(average: Double): Mood {
        val score = average.roundToInt().coerceIn(Mood.ROUGH.score, Mood.GREAT.score)
        return Mood.entries.first { it.score == score }
    }

    /** "good": the word for the mood an average rounds to. Sentences around it say "average". */
    fun moodWord(mood: Mood): String = mood.label.lowercase()

    private fun totals(stats: PeriodStats, locale: Locale): List<Line> {
        val number = NumberFormat.getIntegerInstance(locale)
        fun count(n: Int, one: String, many: String) = "${number.format(n)} ${if (n == 1) one else many}"
        return listOfNotNull(
            Line(null, count(stats.daysKept, "day kept", "days kept")),
            Line(null, count(stats.daysWritten, "day written", "days written")),
            Line(null, count(stats.words, "word", "words")),
            stats.averageMood?.let(::moodOf)?.let { Line(null, "Your average mood was ${moodWord(it)}", it) },
        )
    }
}
