package com.onedayonesecond.ui.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.journal.Patterns
import com.onedayonesecond.journal.PatternsLabels
import com.onedayonesecond.journal.PatternsWords
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.recaps.PeriodTabs
import com.onedayonesecond.ui.recaps.frameKind
import com.onedayonesecond.ui.recaps.frameState
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.ContactFrame
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.TintCard
import com.onedayonesecond.ui.theme.TonalButton
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Patterns: what a week, month or year was about, when, with whom, and how those days
 * felt. Every number is counted on the phone from the entries and their tags; the
 * quote and the summary are the period's letter's own, reused as they are.
 */
@UnstableApi
@Composable
fun PatternsScreen(
    onBack: () -> Unit,
    onOpenPeriod: (RecapPeriod) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPixels: (Int) -> Unit,
    viewModel: PatternsViewModel = viewModel(factory = PatternsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period = state.period
    val today = state.today

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back", title = "Patterns")
        PeriodTabs(period, today, onOpenPeriod)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 48.dp),
        ) {
            Navigator(period, state.patterns, today, onOpenPeriod)
            val patterns = state.patterns
            when {
                state.loading -> Unit
                patterns == null -> BodyText("This ${period.noun} hasn't begun yet.", Modifier.padding(top = 16.dp))
                !patterns.hasEnough -> Empty(state, patterns, onOpenPeriod, onOpenDay)
                else -> Full(state, patterns, Links(onOpenPeriod, onOpenDay, onOpenSettings, onOpenPixels))
            }
        }
    }
}

/** The period with arrows to the one before and the one after (once it has begun). */
@Composable
private fun Navigator(period: RecapPeriod, patterns: Patterns?, today: LocalDate, onOpen: (RecapPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconAction(LineIcons.ChevronLeft, "Previous ${period.noun}", { onOpen(period.previous()) }, Modifier.offset(x = (-12).dp))
        Text(
            patterns?.let { PatternsLabels.range(period, it.span) } ?: RecapLabels.long(period),
            style = AppType.TitleSmall,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        val ahead = period.next().start.isAfter(today)
        IconAction(
            LineIcons.ChevronRight,
            if (ahead) "Next ${period.noun}, not begun yet" else "Next ${period.noun}",
            { onOpen(period.next()) },
            Modifier.offset(x = 12.dp),
            enabled = !ahead,
        )
    }
}

/** Where the screen's links go. */
private class Links(
    val period: (RecapPeriod) -> Unit,
    val day: (LocalDate) -> Unit,
    val settings: () -> Unit,
    val pixels: (Int) -> Unit,
)

/** Until something comes up on 2 or more days: the week's small frames, why, and a way back. */
@UnstableApi
@Composable
private fun Empty(state: PatternsUiState, p: Patterns, onOpenPeriod: (RecapPeriod) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L..6L).forEach { offset ->
            val date = state.stripWeek.start.plusDays(offset)
            val clip = state.clips[date]
            val isFuture = date.isAfter(state.today)
            ContactFrame(
                kind = frameKind(clip, isFuture),
                label = RecapLabels.fullDate(date),
                stateLabel = frameState(clip, state.moods[date], isFuture),
                today = date == state.today,
                frameHeight = 38.dp,
                small = true,
                onClick = { onOpenDay(date) },
                modifier = Modifier.width(28.dp),
            ) {
                ClipStill(clip, Modifier.fillMaxSize())
            }
        }
    }
    Text(
        "Patterns show up after a few days",
        style = AppType.Headline,
        color = Palette.Ink,
        modifier = Modifier.padding(top = 24.dp).semantics { heading() },
    )
    BodyText(PatternsWords.empty(p), Modifier.padding(top = 8.dp), color = Palette.Muted)
    TaggingNote(state)
    val previous = state.period.previous()
    TonalButton(
        "See ${PatternsLabels.possessive(previous, state.today)} patterns",
        { onOpenPeriod(previous) },
        Modifier.padding(top = 24.dp),
    )
}

/** Says when older entries are still being tagged, so a count that grows later is not a surprise. */
@Composable
private fun TaggingNote(state: PatternsUiState) {
    val progress = state.tagging ?: return
    if (progress.done) return
    SmallText(
        "Still tagging your entries: ${progress.tagged} of ${progress.total} done. Counts may grow.",
        Modifier.padding(top = 10.dp),
    )
}

@UnstableApi
@Composable
private fun Full(state: PatternsUiState, p: Patterns, links: Links) {
    val period = state.period
    // 1. What the period was like, and what it is counted from.
    Text(
        PatternsWords.summary(p),
        style = AppType.TitleLarge.copy(lineHeight = SummaryLineHeight),
        color = Palette.Ink,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    MetaText(PatternsWords.counted(p), Modifier.padding(top = 12.dp))
    TaggingNote(state)

    // 2. The letter's quote and summary, as they are. Left out when there is no letter yet.
    val letter = state.letter
    if (letter != null && letter.quote.isNotBlank()) {
        TintCard(Modifier.padding(top = 24.dp)) {
            Text("“${letter.quote}”", style = AppType.Quote, color = Palette.Ink)
            LinkRow(
                PatternsLabels.quoteSource(letter.quoteDate),
                { links.day(letter.quoteDate) },
                Modifier.padding(top = 4.dp),
                style = AppType.Tab,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    if (letter != null && letter.summary.isNotBlank()) {
        PatternSection(
            PatternsWords.Section(PatternsLabels.inShort(period, p.span), "Summary · written from your own entries."),
            first = true,
        ) { BodyText(letter.summary) }
    }

    // 3. The sections for this scale.
    when (period) {
        is RecapPeriod.Week -> WeekSections(state, p, links)
        is RecapPeriod.Month -> MonthSections(state, p, links)
        is RecapPeriod.Year -> YearSections(p, links)
    }

    // 4. What the numbers are and are not.
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        SmallText(PatternsWords.footer(period))
        if (period is RecapPeriod.Week) {
            val month = RecapPeriod.monthOf(period.start)
            LinkRow("See ${PatternsLabels.possessive(month, state.today)} patterns", { links.period(month) }, Modifier.padding(top = 4.dp))
        }
    }
}

@UnstableApi
@Composable
private fun WeekSections(state: PatternsUiState, p: Patterns, links: Links) {
    val week = (0L..6L).map { state.stripWeek.start.plusDays(it) }
    PatternSection(PatternsWords.glance(p)) {
        val groups = listOf(
            "Topics" to p.topics.map { it.name to it.days.toSet() },
            "Habits" to p.habits.map { it.tag.name to it.tag.days.toSet() },
            "People" to p.people.map { it.name to it.days.toSet() },
        ).filter { it.second.isNotEmpty() }
        GlanceTable(week, groups) { date, modifier ->
            val clip = state.clips[date]
            val mood = state.moods[date]
            val isFuture = date.isAfter(state.today)
            ContactFrame(
                kind = frameKind(clip, isFuture),
                label = RecapLabels.fullDate(date),
                stateLabel = frameState(clip, mood, isFuture),
                number = "%02d".format(date.dayOfMonth),
                today = date == state.today,
                mood = mood,
                frameHeight = 52.dp,
                small = true,
                onClick = { links.day(date) },
                modifier = modifier,
            ) {
                ClipStill(clip, Modifier.fillMaxSize())
            }
        }
    }
    PatternsWords.weekMood(p)?.let { PatternSection(it) }
    WhenYouWrite(p)
    val lines = PatternsWords.sinceLastLines(p)
    if (lines.isNotEmpty()) PatternSection(PatternsWords.sinceLast(p)) { SinceLastRows(lines) }
}

@Composable
private fun MonthSections(state: PatternsUiState, p: Patterns, links: Links) {
    val month = p.period.let { it.start..it.end }.let { r -> generateSequence(r.start) { d -> d.plusDays(1).takeIf { it <= r.endInclusive } }.toList() }
    val than = PatternsLabels.short(p.period, p.previousSpan)
    if (p.topics.isNotEmpty()) {
        var open by rememberSaveable { mutableStateOf<String?>(null) }
        PatternSection(PatternsWords.topics(p).copy(sentence = null)) {
            val top = p.topics.first().count
            TopicRows(
                rows = p.topics.map { t ->
                    Bar(
                        t.name, t.count, t.count == top,
                        note = PatternsWords.versusBefore(t.count, t.previousDays, than),
                        description = "${t.name}, ${PatternsWords.days(t.count)}, ${PatternsWords.days(t.previousDays)} in $than",
                    )
                },
                previous = p.topics.map { it.previousDays },
                open = open,
                onToggle = { label -> open = if (open == label) null else label },
            ) { bar ->
                val topic = p.topics.first { it.name == bar.label }
                WeekdayBars(topic.byWeekday, topic.name, Modifier.padding(top = 4.dp))
                PatternsWords.weekdays(topic.byWeekday)?.let {
                    Text(it, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(top = 12.dp))
                }
                DayChipRow(topic.days, onOpenDay = links.day, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    MoodAndTopics(p)
    if (p.habits.isNotEmpty()) {
        val section = PatternsWords.habits(p)
        PatternSection(section.copy(sentence = null)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                p.habits.forEach { habit ->
                    StripRow(habit.tag.name, habit.tag.count) {
                        DayStrip(month, habit.tag.days.toSet(), p.writtenDays, state.today, habit.tag.name, ticks = true)
                    }
                }
            }
            section.sentence?.let { Text(it, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(top = 12.dp)) }
            HabitMoodCards(p.habits.first())
        }
    }
    if (p.people.isNotEmpty()) {
        val section = PatternsWords.people(p)
        PatternSection(section.copy(sentence = null)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                p.people.forEach { person ->
                    StripRow(person.name, person.count) {
                        DayStrip(month, person.days.toSet(), p.writtenDays, state.today, person.name, ticks = true)
                    }
                }
            }
            section.sentence?.let { Text(it, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(top = 12.dp)) }
            LinkRow("Turn off in Settings", links.settings, Modifier.padding(top = 4.dp), chevron = false)
        }
    }
    PatternSection(PatternsWords.moodByTime(p)) {
        MoodTiles(p.moodByWeekday, { PatternsLabels.weekday(DayOfWeek.of(it), TextStyle.SHORT) }, perRow = 7, showWord = true)
    }
    WhenYouWrite(p)
    PlacesAndWords(p)
}

@Composable
private fun YearSections(p: Patterns, links: Links) {
    val lastMonth = p.span.end.monthValue
    val gridMax = (p.topics.flatMap { it.byMonth } + p.people.flatMap { it.byMonth }).maxOrNull() ?: 0
    if (p.topics.isNotEmpty()) {
        PatternSection(PatternsWords.topicsByMonth(p, decemberAhead = lastMonth < 12)) {
            MonthGrid(p.topics.map { it.name to it.byMonth }, lastMonth, gridMax)
        }
    }
    MoodAndTopics(p)
    if (p.habits.isNotEmpty()) {
        PatternSection(PatternsWords.habits(p)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                p.habits.forEach { habit ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(habit.tag.name, style = AppType.Heading, color = Palette.Ink, modifier = Modifier.weight(1f))
                            DayCount(habit.tag.count)
                        }
                        MonthBars(habit.tag.byMonth, lastMonth, habit.tag.name)
                    }
                }
            }
        }
    }
    if (p.people.isNotEmpty()) {
        PatternSection(PatternsWords.people(p).copy(sentence = null)) {
            MonthGrid(p.people.map { it.name to it.byMonth }, lastMonth, gridMax)
            LinkRow("Turn off in Settings", links.settings, Modifier.padding(top = 8.dp), chevron = false)
        }
    }
    val year = p.period as RecapPeriod.Year
    val mood = PatternsWords.moodByTime(p)
    PatternSection(mood.copy(sentence = null)) {
        MoodTiles(p.moodByMonth, { PatternsLabels.monthName(it, TextStyle.NARROW) }, perRow = 6, showWord = false, total = 12)
        mood.sentence?.let { Text(it, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(top = 12.dp)) }
        LinkRow("See every day in Year in pixels", { links.pixels(year.year) }, Modifier.padding(top = 4.dp))
    }
    WhenYouWrite(p)
    PlacesAndWords(p)
}

@Composable
private fun MoodAndTopics(p: Patterns) {
    val usual = p.usualMood ?: return
    if (p.moodAndTopics.isEmpty()) return
    PatternSection(PatternsWords.moodAndTopics(p)) {
        MoodTracks(p.moodAndTopics, usual.mood, usual.average)
    }
}

@Composable
private fun WhenYouWrite(p: Patterns) {
    PatternSection(PatternsWords.whenYouWrite(p)) {
        if (p.dayPartHalves.size == 2) DayPartPairs(p.dayPartHalves) else DayPartBars(p.dayParts)
    }
}

@Composable
private fun PlacesAndWords(p: Patterns) {
    if (p.places.isNotEmpty()) PatternSection(PatternsWords.places(p)) { PlaceRows(p.places) }
    if (p.words.isNotEmpty()) PatternSection(PatternsWords.words(p)) { WordChips(p.words) }
}
