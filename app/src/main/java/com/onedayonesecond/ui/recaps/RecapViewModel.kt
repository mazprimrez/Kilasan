package com.onedayonesecond.ui.recaps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.journal.Patterns
import com.onedayonesecond.journal.RecapContent
import com.onedayonesecond.journal.RecapGenerator
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapLetter
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.journal.RecapSource
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.patterns.observePatterns
import com.onedayonesecond.ui.theme.MessageAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Where a period's letter stands. */
sealed interface RecapStatus {
    data object Loading : RecapStatus

    /**
     * A letter to show. [letter] is set for one in layout 2; [content] for one in the
     * old layout, which is still shown the old way.
     */
    data class Ready(val recap: Recap, val letter: RecapLetter?, val content: RecapContent?) : RecapStatus

    /** Being written right now, and there is no earlier letter to show meanwhile. */
    data object Writing : RecapStatus

    /** Too few days were written on for a letter. */
    data class NotEnough(val daysWritten: Int) : RecapStatus

    /** The period is not over yet; the letter comes on [arrives]. */
    data class NotDue(val arrives: LocalDate, val daysWritten: Int) : RecapStatus

    /** Due, enough to write from, and still not there. [failure] says why, if it was tried. */
    data class Missing(val failure: RecapFailure?) : RecapStatus

    /** This build has no AI service to write letters with. */
    data object NotSetUp : RecapStatus
}

data class RecapUiState(
    val period: RecapPeriod,
    val status: RecapStatus = RecapStatus.Loading,
    /** The period's seconds by day, for the tiles and for "Play this week". */
    val clips: Map<LocalDate, ClipEntity> = emptyMap(),
    val moods: Map<LocalDate, Mood> = emptyMap(),
    val name: String = "",
    val today: LocalDate = LocalDate.now(),
    /** A new version of the letter on screen is being written. The old one stays until it succeeds. */
    val rewriting: Boolean = false,
    /** Why the last "Write it again" did not work, in words for the page. The old letter was kept. */
    val rewriteFailure: String? = null,
    /** How the period changed since the letter on screen was written, or null if it has not. */
    val staleness: RecapSource.Staleness? = null,
    /** Whether "Write this recap again" can be offered at all. */
    val canRewrite: Boolean = false,
    /** The period's Patterns, counted now, for the chapter at the end of the letter. */
    val patterns: Patterns? = null,
)

@UnstableApi
class RecapViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // A key that cannot be read falls back to this week rather than crashing the page.
    val period: RecapPeriod = RecapPeriod.parse(savedStateHandle.get<String>(Routes.ARG_KEY).orEmpty())
        ?: RecapPeriod.weekOf(LocalDate.now())

    private data class Days(
        val clips: Map<LocalDate, ClipEntity>,
        val moods: Map<LocalDate, Mood>,
        val daysWritten: Int,
        val sourceHash: String,
        val entryCount: Int,
    )

    private val days = combine(
        container.repository.observeBetween(period.start, period.end),
        container.journal.observeMetaBetween(period.start, period.end),
        container.journal.observeEntriesBetween(period.start, period.end),
    ) { clips, metas, entries ->
        Days(
            clips = clips.filter { it.file.exists() }.associateBy { it.date },
            moods = metas.mapNotNull { meta -> meta.mood?.let { meta.date to it } }.toMap(),
            daysWritten = entries.map { it.date }.distinct().size,
            sourceHash = RecapSource.hash(entries, metas.associate { it.date to it.mood }),
            entryCount = entries.size,
        )
    }

    private val writingState = combine(
        container.recapGenerator.writing,
        container.recapGenerator.rewriteFailures,
        container.journalPrefs.recapFailures,
    ) { writing, rewriteFailures, failures -> Triple(writing, rewriteFailures[period.key], failures[period.key]) }

    val state: StateFlow<RecapUiState> = combine(
        days,
        container.journal.observeRecap(period.key),
        writingState,
        container.journalPrefs.name,
        container.observePatterns(period, LocalDate.now()),
    ) { days, recap, (writing, rewriteFailure, failure), name, patterns ->
        val inProgress = period.key in writing
        val status = when {
            recap != null -> RecapStatus.Ready(
                recap,
                letter = if (recap.schemaVersion >= Recap.LAYOUT_V2) RecapLetter.decode(recap.json) else null,
                content = if (recap.schemaVersion < Recap.LAYOUT_V2) RecapContent.decode(recap.json) else null,
            )
            inProgress -> RecapStatus.Writing
            // Said first, even for a period still under way: it is the one thing the
            // user can do something about.
            !RecapRules.hasEnough(days.daysWritten) -> RecapStatus.NotEnough(days.daysWritten)
            !RecapRules.isDue(period, LocalDateTime.now()) ->
                RecapStatus.NotDue(RecapRules.arrives(period), days.daysWritten)
            !container.recapGenerator.isConfigured -> RecapStatus.NotSetUp
            else -> RecapStatus.Missing(failure)
        }
        val staleness = recap?.takeIf { it.schemaVersion >= Recap.LAYOUT_V2 && it.sourceHash.isNotEmpty() }
            ?.takeIf { it.sourceHash != days.sourceHash }
            ?.let { RecapSource.Staleness(added = (days.entryCount - it.entryCount).coerceAtLeast(0)) }
        RecapUiState(
            period = period,
            status = status,
            clips = days.clips,
            moods = days.moods,
            name = name,
            rewriting = recap != null && inProgress,
            rewriteFailure = if (recap != null && !inProgress) rewriteFailure?.let(::reason) else null,
            staleness = staleness,
            canRewrite = recap != null && container.recapGenerator.isConfigured,
            patterns = patterns,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState(period))

    /** "Write the letter now". Runs on the app's scope, so leaving the page does not stop it. */
    fun writeNow() {
        container.appScope.launch { container.recapGenerator.generate(period) }
    }

    /**
     * "Write it again": a new letter from the entries as they are now. The old one stays
     * on screen until the new one is saved; then a message offers to undo, which puts
     * the old one back. Runs on the app's scope, so leaving the page does not stop it.
     */
    fun rewrite() {
        if (period.key in container.recapGenerator.writing.value) return
        container.appScope.launch {
            val outcome = container.recapGenerator.generate(period)
            if (outcome == RecapGenerator.Outcome.Written) {
                container.messages.show(
                    "New letter for ${RecapLabels.long(period)}.",
                    MessageAction("Undo", run = { container.journal.undoRecap(period.key) }),
                )
            }
        }
    }

    fun dismissRewriteFailure() = container.recapGenerator.dismissRewriteFailure(period.key)

    private fun reason(outcome: RecapGenerator.Outcome): String = when (outcome) {
        RecapGenerator.Outcome.Offline ->
            "Your phone is offline, so a new version couldn't be written. The letter below hasn't changed."
        RecapGenerator.Outcome.NotSetUp ->
            "The AI service isn't set up in this build of the app. The letter below hasn't changed."
        is RecapGenerator.Outcome.NotEnough -> {
            val days = "${outcome.daysWithEntries} day${if (outcome.daysWithEntries == 1) " has" else "s have"}"
            "Only $days entries now, and a letter needs ${RecapRules.MIN_DAYS_WITH_ENTRIES} or more. " +
                "The letter below hasn't changed."
        }
        is RecapGenerator.Outcome.Failed ->
            "The server had a problem writing a new version. The letter below hasn't changed."
        RecapGenerator.Outcome.Written -> ""
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                RecapViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
