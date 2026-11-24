package com.onedayonesecond.ui.patterns

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
import com.onedayonesecond.journal.EntryTagger
import com.onedayonesecond.journal.PatternSpans
import com.onedayonesecond.journal.Patterns
import com.onedayonesecond.journal.RecapLetter
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.ui.nav.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The quote and the summary of the period's letter, reused word for word. */
data class LetterWords(val quote: String, val quoteDate: LocalDate, val summary: String)

data class PatternsUiState(
    val period: RecapPeriod,
    val today: LocalDate,
    val loading: Boolean = true,
    val patterns: Patterns? = null,
    /** From the stored letter of the same period; null when there is none yet. Never made up here. */
    val letter: LetterWords? = null,
    /** The week shown as frames: the period itself, or the latest week of a month or year. */
    val stripWeek: RecapPeriod.Week,
    val clips: Map<LocalDate, ClipEntity> = emptyMap(),
    val moods: Map<LocalDate, Mood> = emptyMap(),
    /** Entries still waiting to be tagged, so the page can say its counts may still grow. */
    val tagging: EntryTagger.Progress? = null,
)

@UnstableApi
class PatternsViewModel(
    container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val period: RecapPeriod = RecapPeriod.parse(savedStateHandle.get<String>(Routes.ARG_KEY).orEmpty())
        ?: RecapPeriod.weekOf(today)

    private val stripWeek: RecapPeriod.Week = when (period) {
        is RecapPeriod.Week -> period
        else -> RecapPeriod.weekOf(PatternSpans.visible(period, today)?.end ?: period.end)
    }

    private val strip = combine(
        container.repository.observeBetween(stripWeek.start, stripWeek.end),
        container.journal.observeMetaBetween(stripWeek.start, stripWeek.end),
    ) { clips, metas ->
        clips.filter { it.file.exists() }.associateBy { it.date } to
            metas.mapNotNull { m -> m.mood?.let { m.date to it } }.toMap()
    }

    val state: StateFlow<PatternsUiState> = combine(
        container.observePatterns(period, today),
        container.journal.observeRecap(period.key),
        strip,
        container.entryTagger.progress(),
    ) { patterns, recap, (clips, moods), progress ->
        PatternsUiState(
            period = period,
            today = today,
            loading = false,
            patterns = patterns,
            letter = recap?.let(::wordsOf),
            stripWeek = stripWeek,
            clips = clips,
            moods = moods,
            tagging = progress.takeIf { container.entryTagger.isConfigured },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatternsUiState(period, today, stripWeek = stripWeek))

    private fun wordsOf(recap: Recap): LetterWords? {
        val summary = if (recap.schemaVersion >= Recap.LAYOUT_V2) RecapLetter.decode(recap.json).summary else ""
        if (recap.quote.isBlank() && summary.isBlank()) return null
        return LetterWords(recap.quote, recap.quoteDate, summary)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                PatternsViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
