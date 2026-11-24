package com.onedayonesecond.ui.patterns

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.DayPart
import com.onedayonesecond.journal.DayParts
import com.onedayonesecond.journal.HabitCount
import com.onedayonesecond.journal.MoodBucket
import com.onedayonesecond.journal.PatternsLabels
import com.onedayonesecond.journal.PatternsWords
import com.onedayonesecond.journal.PlaceMood
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.SinceLast
import com.onedayonesecond.journal.TopicMood
import com.onedayonesecond.journal.Versus
import com.onedayonesecond.journal.WordCount
import com.onedayonesecond.ui.recaps.DirectionIcon
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MoodFace
import com.onedayonesecond.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// The charts of the Patterns screen, after the Patterns boards of the Kilasan design
// canvas. The rules, everywhere: one series in Rose; the top value Rose and the rest a
// lighter pink on a pale track; counts in mono; every chart carries a full text label for
// screen readers, so nothing rests on colour.

/** The opening summary: Title Large with 30 sp between lines. */
internal val SummaryLineHeight = 30.sp

/** A hairline across the top of whatever this modifies. */
internal fun Modifier.hairlineAbove(): Modifier =
    drawBehind { drawLine(Palette.Hairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }

/** "Topics · days each topic came up.": the name in Rose, what it counts in Muted. */
@Composable
internal fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    val name = label.substringBefore(" · ")
    val rest = label.substringAfter(" · ", "")
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.Rose, fontWeight = FontWeight.SemiBold)) { append(name) }
            if (rest.isNotEmpty()) append(" · $rest")
        },
        style = AppType.Meta,
        color = Palette.Muted,
        modifier = modifier.padding(top = 4.dp),
    )
}

/**
 * A section: a hairline above (unless [first]), the finding as its heading, the label,
 * the chart, then the sentence (14 sp, Ink) and a footnote (12 sp, Muted).
 */
@Composable
internal fun PatternSection(
    section: PatternsWords.Section,
    first: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.hairlineAbove())
            .padding(vertical = 24.dp),
    ) {
        Text(section.heading, style = AppType.TitleLarge, color = Palette.Ink, modifier = Modifier.semantics { heading() })
        SectionLabel(section.label)
        if (content != null) {
            Spacer(Modifier.height(16.dp))
            content()
        }
        section.sentence?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(top = 12.dp))
        }
        section.footnote?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = AppType.Small, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A Rose text link with a chevron after it: "See November’s patterns ›". */
@Composable
internal fun LinkRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: Boolean = true,
    style: androidx.compose.ui.text.TextStyle = AppType.Label,
) {
    Row(
        modifier
            .offset(x = (-12).dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = style, color = Palette.Rose)
        if (chevron) {
            Icon(LineIcons.ChevronRight, null, tint = Palette.Rose, modifier = Modifier.padding(start = 2.dp).size(16.dp))
        }
    }
}

/** A pale track with a filled bar on it. */
@Composable
internal fun TrackBar(fraction: Float, top: Boolean, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Palette.Hairline),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (top) Palette.Rose else Palette.BlushDeep),
            )
        }
    }
}

/** "7" in mono and " days" small and Muted. */
@Composable
internal fun DayCount(n: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(n.toString(), style = AppType.Value, color = Palette.Ink)
        Text(if (n == 1) " day" else " days", style = AppType.Small, color = Palette.Muted, modifier = Modifier.padding(bottom = 1.dp))
    }
}

/** One bar of a bar chart. [note] is a short Muted line under it. */
internal data class Bar(
    val label: String,
    val count: Int,
    val top: Boolean,
    val note: String? = null,
    val description: String = "$label, ${PatternsWords.days(count)}",
)

/** Label on the left, a bar on a track, the count in mono on the right. Read out as one label. */
@Composable
internal fun CountBars(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 112.dp,
    barHeight: Dp = 8.dp,
    countWidth: Dp = 44.dp,
    unit: (Int) -> String = { it.toString() },
) {
    val max = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Column(
        modifier.clearAndSetSemantics { contentDescription = bars.joinToString(". ") { it.description } + "." },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        bars.forEach { bar ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bar.label,
                    style = AppType.Meta,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(labelWidth).padding(end = 10.dp),
                )
                TrackBar(bar.count.toFloat() / max, bar.top, Modifier.weight(1f), barHeight)
                Text(
                    unit(bar.count),
                    style = AppType.Value,
                    color = Palette.Ink,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(countWidth),
                )
            }
        }
    }
}

/**
 * A month's topics: the name and its days above a full-width bar, and under it how it
 * compares with the same days of last month. A row opens out into [below].
 */
@Composable
internal fun TopicRows(
    rows: List<Bar>,
    previous: List<Int>,
    open: String?,
    onToggle: (String) -> Unit,
    below: @Composable (Bar) -> Unit,
) {
    val max = (rows.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Column {
        rows.forEachIndexed { i, bar ->
            val expanded = open == bar.label
            Column(Modifier.fillMaxWidth().then(if (i == 0) Modifier else Modifier.hairlineAbove())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onToggle(bar.label) }
                        .padding(vertical = 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = bar.description
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bar.label,
                            style = AppType.Heading,
                            color = Palette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        DayCount(bar.count)
                    }
                    TrackBar(bar.count.toFloat() / max, bar.top, Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val before = previous.getOrElse(i) { 0 }
                        val icon = when {
                            before == 0 -> null
                            bar.count > before -> LineIcons.ArrowUp
                            bar.count < before -> LineIcons.ArrowDown
                            else -> LineIcons.ArrowSame
                        }
                        if (icon != null) Icon(icon, null, tint = Palette.Muted, modifier = Modifier.padding(end = 2.dp).size(14.dp))
                        Text(bar.note.orEmpty(), style = AppType.Small, color = Palette.Muted, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) LineIcons.ChevronUp else LineIcons.ChevronDown,
                            contentDescription = null,
                            tint = Palette.Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (expanded) Column(Modifier.padding(bottom = 16.dp)) { below(bar) }
            }
        }
    }
}

/** Days per weekday as seven columns, Monday first, the count above each. */
@Composable
internal fun WeekdayBars(byWeekday: Map<DayOfWeek, Int>, name: String, modifier: Modifier = Modifier) {
    ColumnBars(
        columns = DayOfWeek.entries.map {
            Triple(PatternsLabels.weekday(it, TextStyle.NARROW), PatternsLabels.weekday(it), byWeekday[it] ?: 0)
        },
        name = "Days you wrote about ${PatternsWords.inline(name)}, by weekday",
        modifier = modifier,
        height = 56.dp,
    )
}

/** Days per month as twelve columns; months still to come are left empty. */
@Composable
internal fun MonthBars(byMonth: List<Int>, lastMonth: Int, name: String, modifier: Modifier = Modifier) {
    ColumnBars(
        columns = (1..12).map {
            Triple(PatternsLabels.monthName(it, TextStyle.NARROW), PatternsLabels.monthName(it), byMonth[it - 1])
        },
        name = "$name per month",
        futureFrom = lastMonth + 1,
        modifier = modifier,
        height = 64.dp,
    )
}

/**
 * Upright bars with the count above and a short label below, on a hairline base. Each
 * column is (short label, full label for screen readers, count). Columns from
 * [futureFrom] on (1-based) have not happened yet: no bar, no count.
 */
@Composable
internal fun ColumnBars(
    columns: List<Triple<String, String, Int>>,
    name: String,
    modifier: Modifier = Modifier,
    futureFrom: Int = Int.MAX_VALUE,
    height: Dp = 56.dp,
) {
    val past = columns.take((futureFrom - 1).coerceAtMost(columns.size))
    val top = past.maxOfOrNull { it.third } ?: 0
    val max = top.coerceAtLeast(1)
    val label = "$name: " + past.joinToString(", ") { "${it.second} ${it.third}" } + "."
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height + 20.dp)
                .drawBehind { drawLine(Palette.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            columns.forEachIndexed { index, (_, _, n) ->
                val future = index + 1 >= futureFrom
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!future) {
                        Text(n.toString(), style = AppType.FrameNumber, color = if (n > 0) Palette.Ink else Palette.Muted)
                    }
                    if (!future && n > 0) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height(height * (n.toFloat() / max))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (n == top) Palette.Rose else Palette.BlushDeep),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            columns.forEach { (short, _, _) ->
                Text(short, style = AppType.LabelSmall, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Day chips, "Mon 2", each opening its day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayChipRow(days: List<LocalDate>, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { date ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.FrameLine, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open ${RecapLabels.weekdayAndDay(date)}") { onOpenDay(date) }
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${date.dayOfMonth}",
                    style = AppType.Tab,
                    color = Palette.Ink,
                )
            }
        }
    }
}

/** A name and its days above a strip of the whole period's days. */
@Composable
internal fun StripRow(name: String, count: Int, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.padding(bottom = 8.dp).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = AppType.Heading, color = Palette.Ink, modifier = Modifier.weight(1f))
            DayCount(count)
        }
        content()
    }
}

/**
 * One cell per day of the month. A day it came up is Rose; a day with entries but not
 * this, pale blush; a past day with no entry, dashed; a day still to come, a thin outline.
 */
@Composable
internal fun DayStrip(
    dates: List<LocalDate>,
    on: Set<LocalDate>,
    written: Set<LocalDate>,
    today: LocalDate,
    name: String,
    modifier: Modifier = Modifier,
    ticks: Boolean = false,
) {
    val label = "$name: " + if (on.isEmpty()) "no days" else on.sorted().joinToString(", ") { PatternsLabels.day(it) }
    Column(modifier.clearAndSetSemantics { contentDescription = label }) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * (dates.size - 1)) / dates.size
            val corner = CornerRadius(2.dp.toPx())
            val stroke = 1.dp.toPx()
            dates.forEachIndexed { i, date ->
                val x = i * (cell + gap)
                val inset = Offset(x + stroke / 2, stroke / 2)
                val box = Size(cell - stroke, size.height - stroke)
                when {
                    date in on -> drawRoundRect(Palette.Rose, Offset(x, 0f), Size(cell, size.height), corner)
                    date.isAfter(today) -> drawRoundRect(Palette.Hairline, inset, box, corner, style = Stroke(stroke))
                    date in written -> drawRoundRect(Palette.BlushTint, Offset(x, 0f), Size(cell, size.height), corner)
                    else -> drawRoundRect(
                        Palette.FrameLine, inset, box, corner,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
                    )
                }
            }
        }
        if (ticks) DayTicks(dates)
    }
}

/** 01, 08, 15, 22, 29 under a month's strips, each under its own cell. */
@Composable
internal fun DayTicks(dates: List<LocalDate>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp).height(16.dp).clearAndSetSemantics { }) {
        val gap = 2.dp
        val cell = (maxWidth - gap * (dates.size - 1)) / dates.size
        dates.forEachIndexed { i, date ->
            if ((date.dayOfMonth - 1) % 7 == 0) {
                Text(
                    "%02d".format(date.dayOfMonth),
                    style = AppType.FrameNumber,
                    color = Palette.Muted,
                    modifier = Modifier.offset(x = (cell + gap) * i),
                )
            }
        }
    }
}

/** "On gym days ☺ Good, average of 5 days" beside "Other days". */
@Composable
internal fun HabitMoodCards(habit: HabitCount) {
    val on = habit.onHabitDays ?: return
    val other = habit.otherDays ?: return
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MoodCard("On ${PatternsWords.inline(habit.tag.name)} days", on.mood, on.days, Modifier.weight(1f))
        MoodCard("Other days", other.mood, other.days, Modifier.weight(1f))
    }
}

@Composable
private fun MoodCard(title: String, mood: Mood, days: Int, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.BlushTint)
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "$title: average mood ${PatternsWords.mood(mood)}, over ${PatternsWords.days(days)}" },
    ) {
        Text(title, style = AppType.Tab, color = Palette.Muted)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MoodFace(mood, size = 24.dp, color = Palette.Ink)
            Text(mood.label, style = AppType.Heading, color = Palette.Ink, modifier = Modifier.padding(start = 8.dp))
        }
        Text("Average of ${PatternsWords.days(days)}", style = AppType.Small, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Names × months as numbered cells: the darker, the more days. One shading scale for every
 * grid on the screen ([max]); months still to come are outlined and empty. Every cell's
 * number is printed, so the shade is never the only way to read it.
 */
@Composable
internal fun MonthGrid(
    rows: List<Pair<String, List<Int>>>,
    lastMonth: Int,
    max: Int,
    modifier: Modifier = Modifier,
    nameWidth: Dp = 96.dp,
) {
    val label = rows.joinToString(". ") { (name, months) ->
        "$name: " + (1..lastMonth).filter { months[it - 1] > 0 }.joinToString(", ") {
            "${PatternsLabels.monthName(it)} ${months[it - 1]}"
        }.ifEmpty { "no days" }
    } + "."
    Column(modifier.clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Spacer(Modifier.width(nameWidth))
            (1..12).forEach { month ->
                Text(
                    PatternsLabels.monthName(month, TextStyle.NARROW),
                    style = AppType.LabelSmall,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { (name, months) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = AppType.Meta,
                    color = Palette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(nameWidth).padding(end = 8.dp),
                )
                (1..12).forEach { month ->
                    val n = months[month - 1]
                    val future = month > lastMonth
                    val share = if (max == 0) 0f else n.toFloat() / max
                    val fill = when {
                        future -> Color.Transparent
                        n == 0 -> Palette.BlushTint
                        else -> Palette.Rose.copy(alpha = 0.15f + 0.85f * share)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(fill)
                            .then(if (future) Modifier.border(1.dp, Palette.Hairline, RoundedCornerShape(2.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!future && n > 0) {
                            Text(n.toString(), style = AppType.FrameNumber, color = if (share > 0.5f) Palette.Paper else Palette.Ink)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mood and topics: five muted faces as the axis; per topic a track with a line from the
 * usual mood to a dot at the topic's average (filled Rose above usual, hollow Muted
 * below); one Ink rule at the usual mood runs down through every row.
 */
@Composable
internal fun MoodTracks(items: List<TopicMood>, usualMood: Mood, usualAverage: Double) {
    val nameWidth = 112.dp
    val gap = 12.dp
    val inset = 9.dp
    fun fraction(score: Double) = ((score - 1) / 4).toFloat().coerceIn(0f, 1f)
    Column {
        // The axis.
        Row(Modifier.padding(bottom = 8.dp).clearAndSetSemantics { }) {
            Spacer(Modifier.width(nameWidth + gap))
            BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                val track = maxWidth - inset * 2
                Mood.entries.forEach { mood ->
                    MoodFace(
                        mood,
                        size = 18.dp,
                        color = Palette.Muted,
                        modifier = Modifier.offset(x = inset + track * fraction(mood.score.toDouble()) - 9.dp),
                    )
                }
            }
        }
        items.forEach { item ->
            val versus = when (item.versusUsual) {
                Versus.ABOVE -> "above your usual ${PatternsWords.mood(usualMood)}"
                Versus.BELOW -> "below your usual ${PatternsWords.mood(usualMood)}"
                Versus.SAME -> "the same as your usual ${PatternsWords.mood(usualMood)}"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .hairlineAbove()
                    .clearAndSetSemantics {
                        contentDescription = "${item.name}: ${PatternsWords.days(item.days)}, average mood ${PatternsWords.mood(item.mood.mood)}, $versus"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(nameWidth).padding(vertical = 10.dp)) {
                    Text(item.name, style = AppType.Meta, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${PatternsWords.days(item.days)} · ${item.mood.mood.label}", style = AppType.Small, color = Palette.Muted)
                }
                Spacer(Modifier.width(gap))
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val left = inset.toPx()
                    val width = size.width - left * 2
                    val mid = size.height / 2
                    val usualX = left + width * fraction(usualAverage)
                    val dotX = left + width * fraction(item.mood.average)
                    drawLine(Palette.Hairline, Offset(left, mid), Offset(left + width, mid), 2.dp.toPx())
                    drawLine(Palette.Ink, Offset(usualX, 0f), Offset(usualX, size.height), 2.dp.toPx())
                    val above = item.versusUsual == Versus.ABOVE
                    drawLine(if (above) Palette.Rose else Palette.Muted, Offset(usualX, mid), Offset(dotX, mid), 2.dp.toPx())
                    if (above) {
                        drawCircle(Palette.Rose, 7.dp.toPx(), Offset(dotX, mid))
                    } else {
                        drawCircle(Palette.Paper, 7.dp.toPx(), Offset(dotX, mid))
                        drawCircle(Palette.Muted, 6.dp.toPx(), Offset(dotX, mid), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
        // The rule's foot, with its name.
        Row(Modifier.clearAndSetSemantics { }) {
            Spacer(Modifier.width(nameWidth + gap))
            BoxWithConstraints(Modifier.weight(1f)) {
                val track = maxWidth - inset * 2
                val x = inset + track * fraction(usualAverage)
                Box(Modifier.offset(x = x - 1.dp).width(2.dp).height(10.dp).background(Palette.Ink))
                Text(
                    "usual: ${PatternsWords.mood(usualMood)}",
                    style = AppType.Small,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 12.dp).offset(x = (x - 36.dp).coerceIn(0.dp, maxWidth - 88.dp)),
                )
            }
        }
    }
}

/**
 * Average mood per weekday (with its word) or per month, each in a pale tile with how
 * many days it rests on. Fewer than 2 days: a dashed circle and "Too few". Cells past
 * the buckets given ([total]) have not happened yet: an empty circle without a tile.
 */
@Composable
internal fun MoodTiles(
    buckets: List<MoodBucket>,
    name: (Int) -> String,
    perRow: Int,
    showWord: Boolean,
    modifier: Modifier = Modifier,
    total: Int = buckets.size,
) {
    val label = buckets.joinToString("; ") { b ->
        val avg = b.average
        "${name(b.key)} " + if (avg != null) "${PatternsWords.mood(avg.mood)}, ${PatternsWords.days(b.days)}" else "not enough days"
    } + "."
    val cells: List<MoodBucket?> = buckets + List((total - buckets.size).coerceAtLeast(0)) { null }
    Column(modifier.clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(perRow).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, b ->
                    val index = r * perRow + c
                    val tile = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (b != null) Modifier.background(Palette.BlushTint) else Modifier)
                        .padding(vertical = if (showWord) 10.dp else 8.dp)
                    Column(
                        tile,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (showWord) 6.dp else 4.dp),
                    ) {
                        Text(name(b?.key ?: (index + 1)), style = if (showWord) AppType.Tab else AppType.LabelSmall, color = Palette.Muted)
                        val faceSize = if (showWord) 26.dp else 22.dp
                        val avg = b?.average
                        if (avg != null) MoodFace(avg.mood, size = faceSize, color = Palette.Ink) else EmptyCircle(faceSize, dashed = b != null)
                        if (showWord) {
                            Text(
                                avg?.mood?.label ?: "Too few",
                                style = AppType.LabelSmall,
                                color = if (avg != null) Palette.Ink else Palette.Muted,
                            )
                        }
                        if (b != null) {
                            Text(if (showWord) "${b.days}d" else b.days.toString(), style = AppType.FrameNumber, color = Palette.Muted)
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmptyCircle(size: Dp, dashed: Boolean) {
    Canvas(Modifier.size(size)) {
        drawCircle(
            Palette.FrameLine,
            radius = this.size.minDimension / 2 - 0.5.dp.toPx(),
            style = Stroke(1.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null),
        )
    }
}

/** Entries per part of the day: name with its hours in mono, a bar, the count. */
@Composable
internal fun DayPartBars(parts: DayParts, modifier: Modifier = Modifier) {
    val top = parts.counts.values.maxOrNull() ?: 0
    val max = top.coerceAtLeast(1)
    val label = DayPart.entries.joinToString(", ") { "${it.label} ${parts.counts[it] ?: 0}" } + "."
    Column(
        modifier.clearAndSetSemantics { contentDescription = "Entries by time of day: $label" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayPart.entries.forEach { part ->
            val n = parts.counts[part] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(104.dp)) {
                    Text(part.label, style = AppType.Meta, color = Palette.Ink)
                    Text(part.hours, style = AppType.FrameNumber, color = Palette.Muted)
                }
                TrackBar(n.toFloat() / max, n == top && n > 0, Modifier.weight(1f))
                Text(n.toString(), style = AppType.Value, color = Palette.Ink, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
            }
        }
    }
}

/** A year's parts of the day: each part's name above a pair of bars, January–June and July onwards. */
@Composable
internal fun DayPartPairs(halves: List<DayParts>, modifier: Modifier = Modifier) {
    val top = halves.flatMap { it.counts.values }.maxOrNull() ?: 0
    val max = top.coerceAtLeast(1)
    fun name(h: DayParts) =
        "${PatternsLabels.monthName(h.months.first, TextStyle.SHORT)}–${PatternsLabels.monthName(h.months.last, TextStyle.SHORT)}"
    val label = "Entries by time of day. " + halves.joinToString(". ") { h ->
        "${name(h)}: " + DayPart.entries.joinToString(", ") { "${it.label.lowercase()} ${h.counts[it] ?: 0}" }
    } + "."
    Column(modifier.clearAndSetSemantics { contentDescription = label }) {
        DayPart.entries.forEachIndexed { i, part ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (i == 0) Modifier else Modifier.hairlineAbove())
                    .padding(vertical = 8.dp),
            ) {
                Text(part.label, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.padding(bottom = 4.dp))
                halves.forEach { half ->
                    val n = half.counts[part] ?: 0
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name(half), style = AppType.Small, color = Palette.Muted, modifier = Modifier.width(64.dp))
                        TrackBar(n.toFloat() / max, n == top && n > 0, Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text(n.toString(), style = AppType.Timecode, color = Palette.Ink, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                    }
                }
            }
        }
    }
}

/** Up, down and stayed lines, each with its arrow. */
@Composable
internal fun SinceLastRows(lines: List<SinceLast.Line>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val direction = line.direction
                Box(Modifier.padding(end = 12.dp).size(18.dp), contentAlignment = Alignment.Center) {
                    if (direction != null) DirectionIcon(direction, Modifier.size(18.dp))
                }
                Text(line.text, style = AppType.Meta, color = Palette.Ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Places with a pin, their days, and the average mood's face and word where 2 or more had one. */
@Composable
internal fun PlaceRows(places: List<PlaceMood>) {
    Column {
        places.forEachIndexed { i, place ->
            val mood = place.mood
            val label = "${place.name}, ${PatternsWords.days(place.days)}" +
                (mood?.let { ", average mood ${PatternsWords.mood(it.mood)}" } ?: ", too few moods for an average")
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i == 0) Modifier else Modifier.hairlineAbove())
                    .padding(vertical = 10.dp)
                    .clearAndSetSemantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LineIcons.Place, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(place.name, style = AppType.Body, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(PatternsWords.days(place.days), style = AppType.Small, color = Palette.Muted)
                }
                if (mood != null) {
                    MoodFace(mood.mood, size = 22.dp, color = Palette.Ink)
                    Text(mood.mood.label, style = AppType.Tab, color = Palette.Ink, modifier = Modifier.padding(start = 8.dp).width(60.dp))
                } else {
                    Text("Too few", style = AppType.Tab, color = Palette.Muted, textAlign = TextAlign.End, modifier = Modifier.width(90.dp))
                }
            }
        }
    }
}

/** The words used most, as chips with their counts; the top word on a stronger blush. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordChips(words: List<WordCount>) {
    val top = words.maxOfOrNull { it.count } ?: 0
    val label = words.joinToString(", ") { "${it.word} ${it.count} times" } + "."
    FlowRow(
        Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { word ->
            val isTop = word.count == top
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isTop) Palette.Blush else Palette.BlushTint)
                    .height(36.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(word.word, style = AppType.Label, color = Palette.Ink)
                Text(
                    word.count.toString(),
                    style = AppType.Timecode,
                    color = if (isTop) Palette.Ink else Palette.Muted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * The week as a table: the seven frames head seven columns, then a row per topic, habit
 * and person with its name and count on the left and a Rose dot under each day it came up.
 */
@Composable
internal fun GlanceTable(
    dates: List<LocalDate>,
    groups: List<Pair<String, List<Pair<String, Set<LocalDate>>>>>,
    frame: @Composable (LocalDate, Modifier) -> Unit,
) {
    val nameWidth = 112.dp
    val locale = Locale.getDefault()
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(nameWidth))
            dates.forEach { date ->
                Column(Modifier.weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    frame(date, Modifier.fillMaxWidth())
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = AppType.LabelSmall,
                        color = Palette.Muted,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
        groups.forEach { (kind, rows) ->
            Text(kind, style = AppType.Label, color = Palette.Muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            rows.forEach { (name, on) ->
                val days = dates.filter { it in on }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .hairlineAbove()
                        .heightIn(min = 32.dp)
                        .clearAndSetSemantics {
                            contentDescription = "$name: " + days.joinToString(", ") { it.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.width(nameWidth).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = AppType.Meta,
                            color = Palette.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                        )
                        Text(days.size.toString(), style = AppType.Timecode, color = Palette.Muted, modifier = Modifier.padding(end = 8.dp))
                    }
                    dates.forEach { date ->
                        Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                            if (date in on) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Rose))
                            } else {
                                Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.FrameLine))
                            }
                        }
                    }
                }
            }
        }
    }
}
