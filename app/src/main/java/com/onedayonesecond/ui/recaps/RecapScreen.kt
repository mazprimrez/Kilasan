package com.onedayonesecond.ui.recaps

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import com.onedayonesecond.ui.theme.ContactFrame
import com.onedayonesecond.ui.theme.FrameKind
import com.onedayonesecond.ui.theme.MoodFace
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.TintCard
import java.time.DayOfWeek
import java.time.YearMonth
import androidx.compose.ui.graphics.PathEffect
import com.onedayonesecond.ui.theme.StateCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.onedayonesecond.journal.RecapSource
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.BusyLine
import com.onedayonesecond.ui.theme.InlineAlert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.RecapFailure
import com.onedayonesecond.data.RecapFailureReason
import com.onedayonesecond.journal.Direction
import com.onedayonesecond.journal.PixelKind
import com.onedayonesecond.journal.Pixels
import com.onedayonesecond.journal.RecapContent
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * One layout for the week, the month and the year: the period, a title and the quote
 * in the user's own words, one picture of the days, then the chapters.
 */
@UnstableApi
@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPixels: (Int) -> Unit,
    onWrite: (LocalDate) -> Unit,
    onOpenPatterns: (RecapPeriod) -> Unit,
    viewModel: RecapViewModel = viewModel(factory = RecapViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period = state.period
    val today = state.today
    var playing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingRewrite by remember { mutableStateOf(false) }
    val status = state.status
    val clips = remember(state.clips) { state.clips.values.sortedBy { it.date } }

    Column(Modifier.fillMaxSize()) {
        BackBar(
            onBack,
            backLabel = "Back to recaps",
            title = when (period) {
                is RecapPeriod.Week -> "Weekly recap"
                is RecapPeriod.Month -> "Monthly recap"
                is RecapPeriod.Year -> "Yearly recap"
            },
        ) {
            if (state.canRewrite) {
                Box {
                    IconAction(LineIcons.More, "More options", { menuOpen = true }, enabled = !state.rewriting)
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Palette.Paper,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Write this recap again", style = AppType.Body, color = Palette.Ink) },
                            onClick = {
                                menuOpen = false
                                confirmingRewrite = true
                            },
                        )
                    }
                }
            }
        }
        PeriodTabs(period, today, onOpenRecap)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, top = 8.dp, end = ScreenPadding, bottom = 40.dp),
        ) {
            // 1. The period, with a way to step to the one before (and after, once there is one).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    LineIcons.ChevronLeft,
                    "Previous ${period.noun}",
                    { onOpenRecap(period.previous()) },
                    Modifier.offset(x = (-12).dp),
                )
                Text(
                    when {
                        period is RecapPeriod.Year && period.year == today.year ->
                            "${period.year} so far, up to ${RecapLabels.dayAndMonth(today)}"
                        else -> RecapLabels.long(period)
                    },
                    style = AppType.TitleSmall,
                    color = Palette.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconAction(
                    LineIcons.ChevronRight,
                    if (period.next().start.isAfter(today)) "Next ${period.noun}, not ready yet" else "Next ${period.noun}",
                    { onOpenRecap(period.next()) },
                    Modifier.offset(x = 12.dp),
                    enabled = !period.next().start.isAfter(today),
                )
            }

            // While a new version is written the old one stays; a slim line says so.
            if (state.rewriting) {
                Column(Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    MetaText("Writing a new version…")
                    BusyLine(Modifier.padding(top = 6.dp))
                }
            }
            state.rewriteFailure?.let { reason ->
                InlineAlert(
                    "Couldn't write a new version",
                    Modifier.padding(top = 4.dp, bottom = 8.dp),
                    body = reason,
                    icon = LineIcons.Offline,
                    actionLabel = "Try again",
                    onAction = viewModel::rewrite,
                )
            }

            // 2. Title and quote.
            if (status is RecapStatus.Ready) {
                TitleText(status.recap.title, Modifier.padding(top = 8.dp))
                // Says when the letter no longer matches the entries, or is in the old layout.
                val staleness = state.staleness
                val outOfDate = when {
                    status.letter == null -> "Written in the old layout."
                    staleness != null -> RecapSource.describe(staleness)
                    else -> null
                }
                if (outOfDate != null && !state.rewriting) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        MetaText(outOfDate, Modifier.weight(1f, fill = false))
                        if (state.canRewrite) TextAction("Write it again", { confirmingRewrite = true })
                    }
                }
                if (status.recap.quote.isNotEmpty()) {
                    TintCard(Modifier.padding(top = 22.dp)) {
                        Text("“${status.recap.quote}”", style = AppType.Quote, color = Palette.Ink)
                    }
                    Text(
                        RecapLabels.quoteSource(period, status.recap.quoteDate),
                        style = AppType.Tab,
                        color = Palette.Rose,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp),
                    )
                }
            } else {
                TitleText(
                    when (period) {
                        is RecapPeriod.Week -> if (today in period.start..period.end) "This week" else "That week"
                        is RecapPeriod.Month -> period.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        is RecapPeriod.Year -> period.year.toString()
                    },
                    Modifier.padding(top = 8.dp),
                )
            }

            // 3. One picture of the days.
            Spacer(Modifier.height(24.dp))
            when (period) {
                is RecapPeriod.Week -> WeekFrames(period, state, onOpenDay)
                is RecapPeriod.Month -> MonthFrames(period, state, onOpenDay)
                is RecapPeriod.Year -> YearPreview(period.year, state.moods, today) { onOpenPixels(period.year) }
            }
            if (period !is RecapPeriod.Year) {
                SmallText("The bar under each day is your mood. Darker is a harder day.", Modifier.padding(top = 10.dp))
            }

            // 4. Play the period's seconds back to back.
            if (clips.isNotEmpty()) {
                val what = when {
                    period is RecapPeriod.Month -> period.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    period is RecapPeriod.Year && period.year == today.year -> "${period.year} so far"
                    period is RecapPeriod.Year -> period.year.toString()
                    today in period.start..period.end -> "this week"
                    else -> "that week"
                }
                TextAction(
                    "Play $what, ${clips.size} second${if (clips.size == 1) "" else "s"}",
                    { playing = true },
                    Modifier.padding(top = 6.dp).offset(x = (-12).dp),
                    icon = LineIcons.PlayFilled,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 5. The chapters, or why there are none yet.
            when (status) {
                is RecapStatus.Ready -> when {
                    status.letter != null ->
                        LetterChapters(period, status.letter, state.name, state.moods, state.patterns, onOpenDay) {
                            onOpenPatterns(period)
                        }
                    status.content != null -> Chapters(period, status.content, state.name)
                }
                RecapStatus.Loading -> Unit
                RecapStatus.Writing -> WritingPlaceholder(period)
                is RecapStatus.NotEnough -> NotEnough(period, status.daysWritten, today) { onWrite(today) }
                is RecapStatus.NotDue -> NotDue(period, status)
                is RecapStatus.Missing -> Missing(period, status.failure, viewModel::writeNow, onBack)
                RecapStatus.NotSetUp -> StateCard("Letters can't be written yet") {
                    BodyText("The AI service isn't set up in this build of the app. Your entries are safe.")
                }
            }

            // 6.
            MetaText("A reflection from your own entries, not therapy.", Modifier.padding(top = 36.dp))
        }
    }

    if (playing) PlayDialog(clips, onDismiss = { playing = false })

    if (confirmingRewrite) {
        RewriteDialog(
            period = period,
            onConfirm = {
                confirmingRewrite = false
                viewModel.dismissRewriteFailure()
                viewModel.rewrite()
            },
            onDismiss = { confirmingRewrite = false },
        )
    }
}

/** "Write this recap again": says what will be replaced. Cancel has the focus, so nothing is replaced by accident. */
@Composable
private fun RewriteDialog(period: RecapPeriod, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    AppDialog(
        onDismiss = onDismiss,
        title = "Write this recap again?",
        buttons = {
            TextAction("Cancel", onDismiss, Modifier.focusRequester(cancelFocus), color = Palette.Ink)
            RoseButton("Write it again", onConfirm)
        },
    ) {
        BodyText(
            "This replaces the current letter for ${RecapLabels.long(period)} with a new one written from your " +
                "entries as they are now.",
        )
    }
}

// The picture

/** Week, Month, Year: the same page at three lengths. Choosing one opens the period that holds this one. */
@Composable
internal fun PeriodTabs(period: RecapPeriod, today: LocalDate, onOpen: (RecapPeriod) -> Unit) {
    val tabs = listOf(
        Triple("Week", period is RecapPeriod.Week) { onOpen(period.asWeek(today)) },
        Triple("Month", period is RecapPeriod.Month) { onOpen(period.asMonth(today)) },
        Triple("Year", period is RecapPeriod.Year) { onOpen(period.asYear()) },
    )
    Column {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            tabs.forEach { (label, on, open) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { if (!on) open() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = AppType.Label, color = if (on) Palette.Ink else Palette.Muted)
                    if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Palette.Rose))
                }
            }
        }
        Hairline()
    }
}

/** The week this period is, or the latest week of it that has begun. */
internal fun RecapPeriod.asWeek(today: LocalDate): RecapPeriod.Week = when (this) {
    is RecapPeriod.Week -> this
    is RecapPeriod.Month -> RecapPeriod.weeksOf(month).let { weeks ->
        weeks.lastOrNull { !it.start.isAfter(today) } ?: weeks.first()
    }
    is RecapPeriod.Year ->
        if (year == today.year) RecapPeriod.weekOf(today) else RecapPeriod.weeksOf(YearMonth.of(year, 12)).last()
}

internal fun RecapPeriod.asMonth(today: LocalDate): RecapPeriod.Month = when (this) {
    is RecapPeriod.Week -> RecapPeriod.Month(month)
    is RecapPeriod.Month -> this
    is RecapPeriod.Year -> RecapPeriod.Month(if (year == today.year) YearMonth.from(today) else YearMonth.of(year, 12))
}

internal fun RecapPeriod.asYear(): RecapPeriod.Year = when (this) {
    is RecapPeriod.Week -> RecapPeriod.Year(month.year)
    is RecapPeriod.Month -> RecapPeriod.Year(month.year)
    is RecapPeriod.Year -> this
}

internal fun frameKind(clip: ClipEntity?, isFuture: Boolean) = when {
    clip != null -> FrameKind.Kept
    isFuture -> FrameKind.Future
    else -> FrameKind.Missed
}

internal fun frameState(clip: ClipEntity?, mood: Mood?, isFuture: Boolean) = when {
    isFuture -> "Not yet"
    else -> listOf(
        if (clip != null) "Second kept" else "No second",
        if (mood != null) "mood ${mood.label.lowercase()}" else "no mood",
    ).joinToString(", ")
}

/** The week as a strip of seven frames, each with its mood face on it and its day under it. */
@UnstableApi
@Composable
private fun WeekFrames(week: RecapPeriod.Week, state: RecapUiState, onOpenDay: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L..6L).forEach { offset ->
            val date = week.start.plusDays(offset)
            val clip = state.clips[date]
            val mood = state.moods[date]
            val isFuture = date.isAfter(state.today)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ContactFrame(
                    kind = frameKind(clip, isFuture),
                    label = RecapLabels.fullDate(date),
                    stateLabel = frameState(clip, mood, isFuture),
                    number = "%02d".format(date.dayOfMonth),
                    today = date == state.today,
                    mood = mood,
                    frameHeight = 64.dp,
                    small = true,
                    onClick = { onOpenDay(date) },
                ) {
                    ClipStill(clip, Modifier.fillMaxSize())
                }
                MoodBar(mood, isFuture, Modifier.padding(top = 6.dp, bottom = 4.dp))
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = AppType.LabelSmall,
                    color = Palette.Muted,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * The month as a calendar of small frames, Monday first, with the mood face under
 * each. Here the days sit under their weekdays, because a month is read by its weeks.
 */
@UnstableApi
@Composable
private fun MonthFrames(month: RecapPeriod.Month, state: RecapUiState, onOpenDay: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    val cells = buildList<LocalDate?> {
        repeat(month.start.dayOfWeek.value - 1) { add(null) }
        var day = month.start
        while (!day.isAfter(month.end)) {
            add(day)
            day = day.plusDays(1)
        }
        while (size % 7 != 0) add(null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = AppType.Meta,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val clip = state.clips[date]
                        val mood = state.moods[date]
                        val isFuture = date.isAfter(state.today)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            ContactFrame(
                                kind = frameKind(clip, isFuture),
                                label = RecapLabels.fullDate(date),
                                stateLabel = frameState(clip, mood, isFuture),
                                today = date == state.today,
                                frameHeight = 40.dp,
                                onClick = { onOpenDay(date) },
                            ) {
                                ClipStill(clip, Modifier.fillMaxSize())
                            }
                            MoodBar(mood, isFuture, Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A small year in pixels, a row to a month, that opens the full one. */
@Composable
private fun YearPreview(year: Int, moods: Map<LocalDate, Mood>, today: LocalDate, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corners.Card)
            .border(1.dp, Palette.Hairline, Corners.Card)
            .clickable(role = Role.Button, onClickLabel = "Open the year in pixels", onClick = onOpen)
            .padding(16.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(STRIP_CELL * Pixels.MONTHS + STRIP_GAP * (Pixels.MONTHS - 1))
                .semantics { contentDescription = "Small preview of your year in pixels" },
        ) {
            val gap = STRIP_GAP.toPx()
            val w = (size.width - gap * (Pixels.DAYS - 1)) / Pixels.DAYS
            val h = STRIP_CELL.toPx()
            val corner = CornerRadius(1.5.dp.toPx())
            for (month in 1..Pixels.MONTHS) {
                for (day in 1..Pixels.DAYS) {
                    val pixel = Pixels.at(year, month, day, moods, today)
                    val topLeft = Offset((day - 1) * (w + gap), (month - 1) * (h + gap))
                    when (pixel.kind) {
                        PixelKind.MOOD -> drawRoundRect(Palette.mood(pixel.mood!!), topLeft, Size(w, h), corner)
                        PixelKind.EMPTY -> drawRoundRect(Palette.Hairline, topLeft, Size(w, h), corner, style = Stroke(1.dp.toPx()))
                        PixelKind.FUTURE -> drawRoundRect(Palette.Hairline.copy(alpha = 0.5f), topLeft, Size(w, h), corner)
                        PixelKind.ABSENT -> Unit
                    }
                }
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Open year in pixels", style = AppType.Label, color = Palette.Ink, modifier = Modifier.weight(1f))
            Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
        }
    }
}

private val STRIP_CELL = 6.dp
private val STRIP_GAP = 2.dp

/**
 * Draws the year's grid to fill the canvas: months across, days down, [gap] between
 * squares. A day with a mood is filled with its colour; a day without one is a dashed
 * outline; days still to come are left as paper. Used by the pixels page.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixels(
    year: Int,
    moods: Map<LocalDate, Mood>,
    today: LocalDate,
    gap: Float,
    corner: Float,
) {
    val w = (size.width - gap * (Pixels.MONTHS - 1)) / Pixels.MONTHS
    val h = (size.height - gap * (Pixels.DAYS - 1)) / Pixels.DAYS
    val radius = CornerRadius(corner)
    val line = 1.dp.toPx()
    val dashes = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
    for (month in 1..Pixels.MONTHS) {
        for (day in 1..Pixels.DAYS) {
            val pixel = Pixels.at(year, month, day, moods, today)
            val topLeft = Offset((month - 1) * (w + gap), (day - 1) * (h + gap))
            when (pixel.kind) {
                PixelKind.MOOD -> drawRoundRect(Palette.mood(pixel.mood!!), topLeft, Size(w, h), radius)
                PixelKind.EMPTY -> drawRoundRect(
                    Palette.PixelEmpty,
                    Offset(topLeft.x + line / 2, topLeft.y + line / 2),
                    Size(w - line, h - line),
                    radius,
                    style = dashes,
                )
                PixelKind.FUTURE, PixelKind.ABSENT -> Unit
            }
        }
    }
}

// The chapters

/** A part of the letter: a hairline, a heading, then what it says. */
@Composable
internal fun Chapter(title: String, content: @Composable () -> Unit) {
    Column {
        Hairline()
        HeadingText(title, Modifier.padding(top = 20.dp, bottom = 14.dp))
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Chapters(period: RecapPeriod, content: RecapContent, name: String) {
    val locale = Locale.getDefault()

    if (content.whatHappened.isNotEmpty()) {
        Chapter("What happened") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.whatHappened.forEach { line ->
                    Row {
                        // Within a week the weekday places a line; over longer stretches the date does.
                        Text(
                            if (period is RecapPeriod.Week) {
                                line.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            } else {
                                RecapLabels.compactDay(line.date)
                            },
                            style = AppType.Body,
                            color = Palette.Muted,
                            modifier = Modifier.width(if (period is RecapPeriod.Week) 56.dp else 72.dp),
                        )
                        BodyText(line.text, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.note.isNotEmpty()) {
        Chapter("A note for you") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name.isNotBlank()) BodyText("Dear ${name.trim()},")
                // The page writes the greeting itself; one the model added anyway is dropped.
                BodyText(content.note.replace(Regex("^\\s*Dear [^,\\n]{1,40},\\s*"), ""))
            }
        }
    }

    if (content.changes.isNotEmpty()) {
        Chapter("Since last ${period.noun}") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.changes.forEach { change ->
                    Row {
                        Icon(
                            when (change.direction) {
                                Direction.UP -> LineIcons.ArrowUp
                                Direction.DOWN -> LineIcons.ArrowDown
                                Direction.SAME -> LineIcons.ArrowSame
                            },
                            contentDescription = when (change.direction) {
                                Direction.UP -> "Up:"
                                Direction.DOWN -> "Down:"
                                Direction.SAME -> "About the same:"
                            },
                            tint = Palette.Rose,
                            modifier = Modifier.padding(top = 3.dp, end = 12.dp).size(18.dp),
                        )
                        BodyText(change.text, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.tries.isNotEmpty()) {
        // Suggestions, not chores: plain bullets, nothing to tick off.
        Chapter("Something to try") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.tries.forEach { suggestion ->
                    Row {
                        Box(
                            Modifier
                                .padding(top = 9.dp, end = 12.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Palette.Rose)
                                .clearAndSetSemantics { },
                        )
                        BodyText(suggestion, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (content.places.isNotEmpty()) {
        Chapter("Where you were") {
            val most = content.places.maxOf { it.days }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content.places.forEach { place ->
                    Column(Modifier.semantics(mergeDescendants = true) { }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BodyText(place.name, Modifier.weight(1f))
                            Text(
                                "${place.days} day${if (place.days == 1) "" else "s"}",
                                style = AppType.Timecode,
                                color = Palette.Muted,
                            )
                        }
                        ProgressLine(place.days.toFloat() / most, Modifier.padding(top = 6.dp).clearAndSetSemantics { })
                    }
                }
            }
        }
    }
}

// The states

/** Tinted lines where the letter will be, so the page does not jump when it arrives. */
@Composable
private fun WritingPlaceholder(period: RecapPeriod) {
    StateCard("Writing your letter for ${RecapLabels.long(period)}…") {
        Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0.92f, 1f, 0.84f, 0.6f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Blush.copy(alpha = 0.5f)))
            }
        }
        MetaText("Takes about a minute. You can leave this screen, it will be in Recaps when it's ready.")
    }
}

@Composable
private fun NotEnough(period: RecapPeriod, daysWritten: Int, today: LocalDate, onWrite: () -> Unit) {
    val days = "$daysWritten day${if (daysWritten == 1) "" else "s"}"
    val min = RecapRules.MIN_DAYS_WITH_ENTRIES
    if (today in period.start..period.end) {
        StateCard("Your letter needs a few more days") {
            BodyText("You've written on $days this ${period.noun}. Write on $min or more and your letter arrives ${period.arrival()}.")
            RoseButton("Write about today", onWrite, icon = LineIcons.Write)
        }
    } else {
        StateCard("No letter for that ${period.noun}") {
            BodyText("You wrote on $days that ${period.noun}. A letter needs $min or more days of writing, so none was written.")
        }
    }
}

/** "Sunday night" for a week; the date a longer period's letter is due otherwise. */
private fun RecapPeriod.arrival(): String = when (this) {
    is RecapPeriod.Week -> "Sunday night"
    else -> "on ${RecapLabels.dayAndMonth(RecapRules.arrives(this))}"
}

@Composable
private fun NotDue(period: RecapPeriod, status: RecapStatus.NotDue) {
    val whenText = when (period) {
        is RecapPeriod.Week -> "on Sunday night"
        else -> "on ${RecapLabels.dayAndMonth(status.arrives)}"
    }
    val days = "${status.daysWritten} day${if (status.daysWritten == 1) "" else "s"}"
    StateCard("This ${period.noun}'s letter arrives $whenText") {
        BodyText("You've written on $days so far.")
    }
}

@Composable
private fun Missing(period: RecapPeriod, failure: RecapFailure?, onWriteNow: () -> Unit, onBack: () -> Unit) {
    val text = when (failure?.reason) {
        RecapFailureReason.OFFLINE -> "Your phone was offline ${failure.at.asMoment()}. Your entries are safe and nothing was lost."
        RecapFailureReason.FAILED, RecapFailureReason.NOT_SET_UP ->
            "The letter couldn't be written ${failure.at.asMoment()}. Your entries are safe and nothing was lost."
        null -> "This letter hasn't been written yet. Your entries are safe."
    }
    StateCard("This ${period.noun}'s letter wasn't written") {
        BodyText(text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoseButton("Write the letter now", onWriteNow)
            TextAction("Back to recaps", onBack, color = Palette.Ink)
        }
    }
}

/** "on Sunday night", "this morning": when something was last tried, in plain words. */
private fun Long.asMoment(): String {
    val at = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val part = when (at.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
    val today = LocalDateTime.now().toLocalDate()
    return when {
        at.toLocalDate() == today && part == "night" -> "tonight"
        at.toLocalDate() == today -> "this $part"
        at.toLocalDate().isAfter(today.minusDays(7)) ->
            "on ${at.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))} $part"
        else -> "on ${RecapLabels.dayAndMonth(at.toLocalDate())}"
    }
}

/** Plays the period's seconds one after another, then closes. */
@UnstableApi
@Composable
private fun PlayDialog(clips: List<ClipEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(clips.map { MediaItem.fromUri(Uri.fromFile(it.file)) })
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onDismiss()
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Paper).systemBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true)
                        .clip(Corners.Preview)
                        .background(Palette.Blush),
                ) {
                    AndroidView(
                        factory = { TextureView(it) },
                        update = { player.setVideoTextureView(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                clips.getOrNull(index)?.let { clip ->
                    MetaText(
                        RecapLabels.weekdayAndDay(clip.date) + if (clip.caption.isEmpty()) "" else ". ${clip.caption}",
                        Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
                        maxLines = 2,
                    )
                }
            }
            IconAction(LineIcons.Close, "Stop playing", onDismiss, Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
    }
}
