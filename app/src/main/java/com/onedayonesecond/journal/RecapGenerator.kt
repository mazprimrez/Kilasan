package com.onedayonesecond.journal

import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.AiSignInNeededException
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.data.RecapFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.LocalDate

/** Gathers a period's days, has the recap written, checks it and stores it. */
class RecapGenerator(
    private val clips: ClipRepository,
    private val journal: JournalRepository,
    private val ai: JournalAi,
    private val prefs: JournalPrefs,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface Outcome {
        data object Written : Outcome

        /** Fewer than [RecapRules.MIN_DAYS_WITH_ENTRIES] days were written on. */
        data class NotEnough(val daysWithEntries: Int) : Outcome
        data object Offline : Outcome
        data object NotSetUp : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val _writing = MutableStateFlow<Set<String>>(emptySet())

    /** Keys of the recaps being written right now, for the "still writing" state. */
    val writing: StateFlow<Set<String>> = _writing.asStateFlow()

    private val _rewriteFailures = MutableStateFlow<Map<String, Outcome>>(emptyMap())

    /**
     * Recaps that already existed, were asked to be written again, and could not be.
     * The old recap stays; the page says why and offers to try again. Cleared when the
     * next attempt starts. Kept for as long as the app runs.
     */
    val rewriteFailures: StateFlow<Map<String, Outcome>> = _rewriteFailures.asStateFlow()

    val isConfigured: Boolean get() = ai.isConfigured

    /** Every day from [start] to [end] that has a clip, a mood, a place or an entry. */
    suspend fun summaries(start: LocalDate, end: LocalDate): List<DaySummary> {
        val clipsByDay = clips.clipsBetween(start, end).associateBy { it.date }
        val metaByDay = journal.metaBetween(start, end).associateBy { it.date }
        val entriesByDay = journal.entriesBetween(start, end).groupBy { it.date }
        return (clipsByDay.keys + metaByDay.keys + entriesByDay.keys).sorted().map { date ->
            val entries = entriesByDay[date].orEmpty()
            DaySummary(
                date = date,
                hasClip = date in clipsByDay,
                caption = clipsByDay[date]?.caption.orEmpty(),
                mood = metaByDay[date]?.mood,
                place = metaByDay[date]?.placeName,
                texts = entries.mapNotNull { it.text?.trim()?.ifEmpty { null } },
                entryCount = entries.size,
            )
        }
    }

    /**
     * Writes [period]'s recap, in place of the one already there if there is one (which
     * is kept for Undo). Safe to call again: a second call while one is running for the
     * same period returns straight away. A recap that already exists is replaced only
     * when it is written successfully; until then, and if it fails, the old one stays.
     */
    suspend fun generate(period: RecapPeriod): Outcome {
        val key = period.key
        var claimed = false
        _writing.update { if (key in it) it else (it + key).also { claimed = true } }
        if (!claimed) return Outcome.Written
        _rewriteFailures.update { it - key }

        return try {
            val existed = journal.recap(key) != null
            val outcome = write(period)
            if (existed) {
                if (outcome != Outcome.Written) _rewriteFailures.update { it + (key to outcome) }
            } else {
                prefs.setRecapFailure(
                    key,
                    when (outcome) {
                        Outcome.Offline -> RecapFailure(RecapFailureReason.OFFLINE, now())
                        Outcome.NotSetUp -> RecapFailure(RecapFailureReason.NOT_SET_UP, now())
                        is Outcome.Failed -> RecapFailure(RecapFailureReason.FAILED, now())
                        else -> null
                    },
                )
            }
            outcome
        } finally {
            _writing.update { it - key }
        }
    }

    /** Hides the reason a rewrite failed, once it has been read or the user moved on. */
    fun dismissRewriteFailure(key: String) {
        _rewriteFailures.update { it - key }
    }

    private suspend fun write(period: RecapPeriod): Outcome {
        val entries = journal.entriesBetween(period.start, period.end)
        val metas = journal.metaBetween(period.start, period.end)
        val days = summaries(period.start, period.end)
        val written = days.count { it.entryCount > 0 }
        if (!RecapRules.hasEnough(written)) return Outcome.NotEnough(written)
        if (!ai.isConfigured) return Outcome.NotSetUp

        val before = period.previous()
        val previousDays = summaries(before.start, before.end)
        val previousStats = previousDays.takeIf { it.isNotEmpty() }?.let(PeriodStats::of)
        val previousLetter = journal.recap(before.key)
            ?.takeIf { it.schemaVersion >= Recap.LAYOUT_V2 }
            ?.let { RecapLetter.decode(it.json) }
        val request = RecapRequest.build(
            period = period,
            entries = entries,
            days = days,
            previous = previousStats,
            previousThreads = previousLetter?.threads.orEmpty(),
            name = prefs.name.first(),
        )

        val draft = try {
            RecapRequest.parse(ai.writeRecap(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            return Outcome.NotSetUp
        } catch (e: AiSignInNeededException) {
            return Outcome.Failed(e.message ?: "Confirm your Google account first.")
        } catch (e: IOException) {
            return Outcome.Offline
        } catch (e: AiException) {
            return Outcome.Failed(e.message ?: "The letter could not be written.")
        } ?: return Outcome.Failed("The letter came back empty.")

        // The backend already holds the quote to the entries; this is the second guard.
        // If it is not in them, one is taken from them directly.
        val quote = JournalMath.verifyQuote(draft.quote, days, draft.quoteDate)
            ?: JournalMath.fallbackQuote(days)
        val letter = withNumbers(draft.letter, period, days, previousStats, earliest())
        journal.replaceRecap(
            Recap(
                key = period.key,
                title = draft.title,
                quote = quote?.first.orEmpty(),
                quoteDate = quote?.second ?: period.end,
                json = letter.encode(),
                createdAt = now(),
                schemaVersion = Recap.LAYOUT_V2,
                sourceHash = RecapSource.hash(entries, metas.associate { it.date to it.mood }),
                entryCount = entries.size,
            ),
        )
        return Outcome.Written
    }

    /** The first day anything was kept or written, to tell a first week from a gap. */
    private suspend fun earliest(): LocalDate? =
        listOfNotNull(journal.earliestEntryDate(), clips.earliestDate()).minOrNull()

    companion object {

        /**
         * Puts the phone's own numbers into a letter the model wrote: the stats of this
         * period and the one before, and days per place for a year.
         */
        fun withNumbers(
            letter: RecapLetter,
            period: RecapPeriod,
            days: List<DaySummary>,
            previousStats: PeriodStats?,
            earliest: LocalDate?,
        ): RecapLetter = letter.copy(
            places = if (period is RecapPeriod.Year) JournalMath.daysPerCity(days.map { it.place }) else emptyList(),
            stats = PeriodStats.of(days),
            previousStats = previousStats,
            hadEarlier = earliest != null && earliest.isBefore(period.start),
        )
    }
}
