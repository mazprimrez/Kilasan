package com.onedayonesecond.ui.recaps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.DatedNote
import com.onedayonesecond.journal.Direction
import com.onedayonesecond.journal.JournalMath
import com.onedayonesecond.journal.Mention
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapLetter
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.SinceLast
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.MoodFace
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ProgressLine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The chapters of a recap in layout 2: how you felt, what was on your mind, wins, what
 * is still open, the note, a question, since last time, and for longer periods who and
 * what came up most. Days are always chips that open that day's page.
 */
@Composable
internal fun LetterChapters(
    period: RecapPeriod,
    letter: RecapLetter,
    name: String,
    moods: Map<LocalDate, Mood>,
    onOpenDay: (LocalDate) -> Unit,
) {
    Chapter("How you felt") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (letter.howYouFelt.isNotEmpty()) BodyText(letter.howYouFelt)
            if (letter.howYouFeltDays.isNotEmpty()) DayChips(period, letter.howYouFeltDays, onOpenDay)
            // A month or a year also shows the usual mood of each weekday, counted on the phone.
            if (period !is RecapPeriod.Week && moods.isNotEmpty()) WeekdayMoods(JournalMath.moodByWeekday(moods))
        }
    }

    if (letter.threads.isNotEmpty() || letter.dayByDay.isNotEmpty()) {
        Chapter("On your mind") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                letter.threads.forEach { thread ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(thread.title, style = AppType.Heading, color = Palette.Ink)
                        BodyText(thread.line)
                        if (thread.days.isNotEmpty()) DayChips(period, thread.days, onOpenDay, Modifier.padding(top = 4.dp))
                    }
                }
                if (letter.dayByDay.isNotEmpty()) DayByDay(period, letter, onOpenDay)
            }
        }
    }

    if (letter.wins.isNotEmpty()) {
        Chapter("Wins") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                letter.wins.forEach { win -> DatedRow(period, win, onOpenDay, bullet = true) }
            }
        }
    }

    // A diary, not a to-do list: plain lines that lead back to the day, nothing to tick off.
    if (letter.stillOpen.isNotEmpty()) {
        Chapter("Still open") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                letter.stillOpen.forEach { open -> DatedRow(period, open, onOpenDay, bullet = false) }
            }
        }
    }

    if (letter.note.isNotEmpty()) {
        Chapter("A note for you") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name.isNotBlank()) BodyText("Dear ${name.trim()},")
                // The page writes the greeting itself; one the model added anyway is dropped.
                BodyText(letter.note.replace(Regex("^\\s*Dear [^,\\n]{1,40},\\s*"), ""))
            }
        }
    }

    if (letter.question.isNotEmpty()) {
        Chapter("A question for next ${period.noun}") {
            BodyText(letter.question)
        }
    }

    Chapter("Since last ${period.noun}") {
        SinceLastLines(SinceLast.of(period.noun, letter.stats, letter.previousStats, letter.hadEarlier))
    }

    if (period !is RecapPeriod.Week && (letter.topPeople.isNotEmpty() || letter.topTopics.isNotEmpty())) {
        Chapter("Who and what came up most") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (letter.topPeople.isNotEmpty()) Mentions("People", letter.topPeople, period)
                if (letter.topTopics.isNotEmpty()) Mentions("Topics", letter.topTopics, period)
            }
        }
    }

    if (letter.places.isNotEmpty()) {
        Chapter("Where you were") {
            val most = letter.places.maxOf { it.days }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                letter.places.forEach { place ->
                    Column(Modifier.semantics(mergeDescendants = true) { }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BodyText(place.name, Modifier.weight(1f))
                            Text(days(place.days), style = AppType.Timecode, color = Palette.Muted)
                        }
                        ProgressLine(place.days.toFloat() / most, Modifier.padding(top = 6.dp).clearAndSetSemantics { })
                    }
                }
            }
        }
    }
}

/**
 * The thin mood bar under a day's frame, in the mood scale: darker is a harder day.
 * A day without a mood gets an empty outline; a day still to come, nothing.
 */
@Composable
internal fun MoodBar(mood: Mood?, isFuture: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    val bar = modifier.fillMaxWidth().height(5.dp).clearAndSetSemantics { }
    when {
        isFuture -> Box(bar)
        mood != null -> Box(bar.clip(shape).background(Palette.mood(mood)))
        else -> Box(bar.border(1.dp, Palette.Hairline, shape))
    }
}

/** "Mon · Tue · Fri" within a week, "16 Nov · 21 Nov" over longer periods. Each opens its day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(period: RecapPeriod, days: List<LocalDate>, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { date ->
            Chip(
                dayLabel(period, date),
                selected = false,
                onClick = { onOpenDay(date) },
                textStyle = AppType.Tab,
                modifier = Modifier.semantics { contentDescription = "Open ${RecapLabels.weekdayAndDay(date)}" },
            )
        }
    }
}

private fun dayLabel(period: RecapPeriod, date: LocalDate): String =
    if (period is RecapPeriod.Week) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } else {
        RecapLabels.compactDay(date)
    }

/** A win or an open thread: its words, then the day it came from, which opens that day. */
@Composable
private fun DatedRow(period: RecapPeriod, note: DatedNote, onOpenDay: (LocalDate) -> Unit, bullet: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${RecapLabels.weekdayAndDay(note.date)}") { onOpenDay(note.date) }
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (bullet) {
            Box(
                Modifier
                    .padding(top = 9.dp, end = 12.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Palette.Rose)
                    .clearAndSetSemantics { },
            )
        }
        Column(Modifier.weight(1f)) {
            BodyText(note.text)
            Text(
                if (period is RecapPeriod.Week) RecapLabels.weekdayAndDay(note.date) else RecapLabels.compactDay(note.date),
                style = AppType.Meta,
                color = Palette.Rose,
            )
        }
        Icon(
            LineIcons.ChevronRight,
            contentDescription = null,
            tint = Palette.Muted,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(18.dp),
        )
    }
}

/** The old one-line-a-day list, folded away under the threads for when it is wanted. */
@Composable
private fun DayByDay(period: RecapPeriod, letter: RecapLetter, onOpenDay: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Shown" else "Hidden" }
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Day by day", style = AppType.Label, color = Palette.Ink, modifier = Modifier.weight(1f))
            Icon(
                if (open) LineIcons.ChevronUp else LineIcons.ChevronDown,
                contentDescription = null,
                tint = Palette.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                letter.dayByDay.forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Open ${RecapLabels.weekdayAndDay(line.date)}") {
                                onOpenDay(line.date)
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            dayLabel(period, line.date),
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
}

/** Seven small cells, Monday first, each in the usual mood of that weekday. */
@Composable
private fun WeekdayMoods(byWeekday: Map<DayOfWeek, Mood?>) {
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetaText("Your usual mood on each day of the week")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val mood = byWeekday[day]
                val label = "${day.getDisplayName(TextStyle.FULL, locale)}s: " +
                    (mood?.let(SinceLast::moodWord) ?: "no mood")
                Column(
                    Modifier.weight(1f).clearAndSetSemantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = RoundedCornerShape(3.dp)
                    val cell = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                    if (mood != null) {
                        Box(cell.background(Palette.mood(mood)))
                    } else {
                        Box(cell.border(1.dp, Palette.Hairline, shape))
                    }
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = AppType.LabelSmall,
                        color = Palette.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SinceLastLines(chapter: SinceLast.Chapter) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val lines = when (chapter) {
            is SinceLast.Chapter.Alone -> {
                BodyText(chapter.heading)
                chapter.totals
            }
            is SinceLast.Chapter.Compared -> chapter.lines
        }
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 2.dp, end = 12.dp).size(20.dp), contentAlignment = Alignment.Center) {
                    val mood = line.mood
                    val direction = line.direction
                    when {
                        mood != null -> MoodFace(mood, size = 20.dp)
                        direction != null -> DirectionIcon(direction)
                        else -> Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Rose).clearAndSetSemantics { })
                    }
                }
                BodyText(line.text, Modifier.weight(1f))
                val direction = line.direction
                if (line.mood != null && direction != null) DirectionIcon(direction, Modifier.padding(start = 8.dp, top = 3.dp))
            }
        }
    }
}

@Composable
private fun DirectionIcon(direction: Direction, modifier: Modifier = Modifier) {
    Icon(
        when (direction) {
            Direction.UP -> LineIcons.ArrowUp
            Direction.DOWN -> LineIcons.ArrowDown
            Direction.SAME -> LineIcons.ArrowSame
        },
        contentDescription = when (direction) {
            Direction.UP -> "Up"
            Direction.DOWN -> "Down"
            Direction.SAME -> "The same"
        },
        tint = Palette.Rose,
        modifier = modifier.size(18.dp),
    )
}

/** People or topics, most days first, with an arrow against the period before when there is one. */
@Composable
private fun Mentions(title: String, mentions: List<Mention>, period: RecapPeriod) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = AppType.TitleSmall, color = Palette.Muted)
        mentions.forEach { mention ->
            Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                BodyText(mention.name, Modifier.weight(1f))
                Text(days(mention.count), style = AppType.Value, color = Palette.Ink)
                Box(Modifier.padding(start = 10.dp).width(40.dp), contentAlignment = Alignment.CenterEnd) {
                    val direction = mention.direction
                    when {
                        mention.previous == 0 -> Text("new", style = AppType.Tab, color = Palette.Rose)
                        direction != null -> DirectionIcon(direction)
                    }
                }
            }
        }
        if (mentions.any { it.previous != null }) MetaText("Arrows compare with last ${period.noun}.")
    }
}

private fun days(n: Int) = "$n day${if (n == 1) "" else "s"}"
