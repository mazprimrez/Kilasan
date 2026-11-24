package com.onedayonesecond.ui.patterns

import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.journal.PatternSpans
import com.onedayonesecond.journal.Patterns
import com.onedayonesecond.journal.PatternsCalculator
import com.onedayonesecond.journal.RecapPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * A period's Patterns, worked out again whenever its entries, moods, places or tags
 * change, or the people switch in Settings is flipped. Null for a period not begun.
 * Shared by the Patterns screen and the Patterns chapter of a letter, so they agree.
 */
@UnstableApi
fun AppContainer.observePatterns(period: RecapPeriod, today: LocalDate): Flow<Patterns?> {
    val span = PatternSpans.visible(period, today) ?: return flowOf(null)
    val from = PatternSpans.previous(period, span).start
    val to = span.end
    val calculator = PatternsCalculator()
    return combine(
        journal.observeEntriesBetween(from, to),
        journal.observeMetaBetween(from, to),
        journal.observeTagsBetween(from, to),
        journalPrefs.showPeopleInPatterns,
    ) { entries, metas, tags, showPeople ->
        calculator.calculate(
            period,
            today,
            PatternsCalculator.Input(
                entries = entries,
                moods = metas.mapNotNull { m -> m.mood?.let { m.date to it } }.toMap(),
                places = metas.mapNotNull { m -> m.placeName?.let { m.date to it } }.toMap(),
                tags = tags,
            ),
            includePeople = showPeople,
        )
    }.flowOn(Dispatchers.Default)
}
