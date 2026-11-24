package com.onedayonesecond.ui.recaps

import com.onedayonesecond.ui.theme.IconAction
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.TabHeader
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.TintCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.data.Recap
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Recaps tab: one page per year, newest first, with the years as tabs across the
 * top. Each page has the year's letter, the year in pixels, this month's weeks with a
 * small strip of their frames, and a row for every earlier month that opens out into
 * its own weeks.
 */
@UnstableApi
@Composable
fun RecapsScreen(
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenPixels: (Int) -> Unit,
    onOpenPatterns: (RecapPeriod) -> Unit,
    onSettings: () -> Unit,
    viewModel: RecapsViewModel = viewModel(factory = RecapsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // Newest first, so this year is always the first page however far back the list grows.
    val years = remember(state.years) { state.years.reversed() }
    val pager = rememberPagerState { years.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TabHeader("Recaps") { IconAction(LineIcons.Settings, "Settings", onSettings) }
        YearTabs(
            years = years,
            selected = pager.currentPage,
            onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } },
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val year = years.getOrNull(page) ?: return@HorizontalPager
            YearPage(
                year = year,
                state = state,
                onOpenRecap = onOpenRecap,
                onOpenPixels = { onOpenPixels(year) },
                onOpenPatterns = {
                    // This year opens on the week under way; an earlier one on the whole year.
                    onOpenPatterns(if (year == state.today.year) RecapPeriod.weekOf(state.today) else RecapPeriod.Year(year))
                },
            )
        }
    }
}

/**
 * The years as tabs. Up to four share the width; more than that and they keep a
 * comfortable width each and the row scrolls.
 */
@Composable
private fun YearTabs(years: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val scrolls = years.size > 4
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .selectableGroup(),
        ) {
            years.forEachIndexed { index, year ->
                val on = index == selected
                Box(
                    Modifier
                        .then(if (scrolls) Modifier.width(96.dp) else Modifier.weight(1f))
                        .height(48.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(year.toString(), style = AppType.Label, color = if (on) Palette.Rose else Palette.Muted)
                    if (on) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.4f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(Palette.Rose),
                        )
                    }
                }
            }
        }
        Hairline()
    }
}

@UnstableApi
@Composable
private fun YearPage(
    year: Int,
    state: RecapsUiState,
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenPixels: () -> Unit,
    onOpenPatterns: () -> Unit,
) {
    val today = state.today
    val yearPeriod = RecapPeriod.Year(year)
    // Newest month first; this year stops at the month we are in.
    val lastMonth = if (year == today.year) today.monthValue else 12
    val months = (lastMonth downTo 1).map { YearMonth.of(year, it) }
    val locale = Locale.getDefault()
    val kept = remember(state.clips, year) { state.clips.keys.count { it.year == year } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenPadding, top = 20.dp, end = ScreenPadding, bottom = 32.dp),
    ) {
        YearLetter(
            recap = state.recaps[yearPeriod.key],
            year = year,
            kept = kept,
            isThisYear = year == today.year,
            onOpen = { onOpenRecap(yearPeriod) },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(Corners.Chip)
                .clickable(role = Role.Button, onClick = onOpenPixels)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelGlyph()
            Text("Year in pixels", style = AppType.Body, color = Palette.Ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(Corners.Chip)
                .clickable(role = Role.Button, onClick = onOpenPatterns)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PatternsGlyph()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Patterns", style = AppType.Body, color = Palette.Ink)
                Text("What you wrote about, when, and how those days felt", style = AppType.Meta, color = Palette.Muted)
            }
            Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        }

        // The newest month is laid open: its weeks are what there is to read most often.
        months.firstOrNull()?.let { month ->
            val period = RecapPeriod.Month(month)
            val recap = state.recaps[period.key]
            val name = month.month.getDisplayName(TextStyle.FULL, locale)
            HeadingText(name, Modifier.padding(top = 20.dp))
            Text(
                when {
                    recap != null -> recap.title
                    month == YearMonth.from(today) ->
                        "Monthly letter arrives on ${RecapLabels.dayAndMonth(RecapRules.arrives(period))}"
                    else -> "No monthly letter yet"
                },
                style = AppType.Meta,
                color = Palette.Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open the letter for $name") { onOpenRecap(period) }
                    .defaultMinSize(minHeight = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            WeekRows(month, state, onOpenRecap)
        }

        if (months.size > 1) {
            Spacer(Modifier.height(16.dp))
            months.drop(1).forEach { month -> MonthRow(month = month, state = state, onOpenRecap = onOpenRecap) }
        }
    }
}

/**
 * The year's recap on a tinted card: its title, how many seconds the year holds, its
 * quote, and the way in. Before the letter is written it says when it will arrive.
 */
@Composable
private fun YearLetter(recap: Recap?, year: Int, kept: Int, isThisYear: Boolean, onOpen: () -> Unit) {
    val seconds = "$kept second${if (kept == 1) "" else "s"}"
    TintCard(onClick = onOpen) {
        Text(recap?.title ?: "The year's letter", style = AppType.TitleLarge, color = Palette.Ink)
        MetaText(
            when {
                isThisYear -> "$year so far, $seconds"
                else -> "$year, $seconds"
            },
            Modifier.padding(top = 4.dp),
        )
        when {
            recap != null && recap.quote.isNotEmpty() -> Text(
                "“${recap.quote}”",
                style = AppType.Quote.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = Palette.Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            recap == null -> MetaText(
                if (isThisYear) {
                    "Arrives on ${RecapLabels.dayAndMonth(RecapRules.arrives(RecapPeriod.Year(year)))}."
                } else {
                    "Not written yet."
                },
                Modifier.padding(top = 12.dp),
            )
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Read the year", style = AppType.Label, color = Palette.Rose)
            Icon(
                LineIcons.ChevronRight,
                contentDescription = null,
                tint = Palette.Rose,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

/** Six 6 dp squares with 2 dp between: the width of [PixelGlyph]. */
private val PixelGlyphWidth = 46.dp

/** Three little bars, the tallest in Rose: Patterns, in miniature. Decorative. */
@Composable
private fun PatternsGlyph() {
    Row(
        // As wide as the pixel glyph above it, so the two rows line up.
        Modifier.width(PixelGlyphWidth).height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(0.55f to Palette.BlushDeep, 1f to Palette.Rose, 0.75f to Palette.BlushDeep).forEach { (h, c) ->
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(c))
        }
    }
}

/** A dozen little squares in the mood colours: the year in pixels, in miniature. Decorative. */
@Composable
private fun PixelGlyph() {
    val moods = listOf(
        Mood.GOOD, Mood.GREAT, Mood.NEUTRAL, Mood.GOOD, Mood.BAD, Mood.GOOD,
        Mood.NEUTRAL, Mood.GOOD, Mood.ROUGH, Mood.GREAT, Mood.GOOD, Mood.NEUTRAL,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        moods.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { mood ->
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(Palette.mood(mood)))
                }
            }
        }
    }
}

/** The weeks of [month] that have begun, oldest first, each with its strip of frames. */
@UnstableApi
@Composable
private fun WeekRows(month: YearMonth, state: RecapsUiState, onOpenRecap: (RecapPeriod) -> Unit) {
    val today = state.today
    // Only weeks that have begun: a week that has not started has nothing to open.
    val weeks = remember(month, today) { RecapPeriod.weeksOf(month).filter { !it.start.isAfter(today) } }
    weeks.forEach { week -> WeekRow(week, state, onClick = { onOpenRecap(week) }) }
}

/**
 * One week: its dates, how many of its days were kept, its seven frames in miniature,
 * and "Read", or the day its letter arrives while the week is still under way.
 */
@UnstableApi
@Composable
private fun WeekRow(week: RecapPeriod.Week, state: RecapsUiState, onClick: () -> Unit) {
    val today = state.today
    val days = (0..6).map { week.start.plusDays(it.toLong()) }
    val kept = days.count { it in state.clips }
    val underWay = !today.isAfter(week.end)
    val dates = RecapLabels.tight(week)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = if (underWay) {
                    "Weekly recap for ${RecapLabels.short(week)} arrives on Sunday, $kept of 7 days kept so far"
                } else {
                    "Weekly recap, ${RecapLabels.short(week)}, $kept of 7 days kept"
                }
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(108.dp)) {
            Text(dates, style = AppType.TitleSmall, color = Palette.Ink, maxLines = 1)
            SmallText("$kept of 7 kept")
        }
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEach { day -> MiniFrame(clip = state.clips[day], isToday = day == today, isFuture = day.isAfter(today)) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (underWay) "Sunday" else "Read",
            style = AppType.Tab,
            color = if (underWay) Palette.Muted else Palette.Rose,
        )
    }
}

/** A day at its smallest: the still, a dashed outline for a missed day, a tint for a day to come. */
@UnstableApi
@Composable
private fun MiniFrame(clip: ClipEntity?, isToday: Boolean, isFuture: Boolean) {
    val size = Modifier.size(width = 19.5.dp, height = 26.dp)
    when {
        clip != null -> ClipStill(clip, size, shape = Corners.MiniFrame)
        isToday -> Box(size.border(1.5.dp, Palette.Rose, Corners.MiniFrame))
        isFuture -> Box(size.clip(Corners.MiniFrame).background(Palette.BlushTint))
        else -> Box(
            size.drawBehind {
                val inset = 0.5.dp.toPx()
                drawRoundRect(
                    Palette.FrameLine,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            },
        )
    }
}

/**
 * An earlier month: its name and the title of its letter, and how many weeks it has.
 * Tapping it opens it out into a way to its letter and a row for each week.
 */
@UnstableApi
@Composable
private fun MonthRow(month: YearMonth, state: RecapsUiState, onOpenRecap: (RecapPeriod) -> Unit) {
    val today = state.today
    val period = RecapPeriod.Month(month)
    val recap = state.recaps[period.key]
    val weeks = remember(month, today) { RecapPeriod.weeksOf(month).filter { !it.start.isAfter(today) } }
    var expanded by rememberSaveable(month.toString()) { mutableStateOf(false) }
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    Hairline()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Hide weekly recaps for $name" else "Show weekly recaps for $name",
            ) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Weeks shown" else "Weeks hidden" }
            .defaultMinSize(minHeight = 72.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = AppType.Heading, color = Palette.Ink)
            MetaText(recap?.title ?: "No letter yet", maxLines = 1)
        }
        Text(
            "${weeks.size} week${if (weeks.size == 1) "" else "s"}",
            style = AppType.Tab,
            color = Palette.Muted,
            modifier = Modifier.padding(start = 12.dp),
        )
        Icon(
            if (expanded) LineIcons.ChevronDown else LineIcons.ChevronRight,
            contentDescription = null,
            tint = Palette.Muted,
            modifier = Modifier.padding(start = 12.dp).size(20.dp),
        )
    }
    if (expanded) {
        TextAction(
            "Read the letter for $name",
            { onOpenRecap(period) },
            Modifier.offset(x = (-12).dp),
            icon = LineIcons.Recaps,
        )
        WeekRows(month, state, onOpenRecap)
        Spacer(Modifier.height(12.dp))
    }
}
