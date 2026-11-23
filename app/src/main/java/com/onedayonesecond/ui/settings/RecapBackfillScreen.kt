package com.onedayonesecond.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.BackfillProgress
import com.onedayonesecond.journal.RecapBackfill
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.work.BackfillLabels
import com.onedayonesecond.work.BackfillWork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class BackfillUiState(
    /** Null while the entries are being looked through. */
    val plan: RecapBackfill.Plan? = null,
    val includeOldLayout: Boolean = false,
    val includeThisYear: Boolean = false,
    val progress: BackfillProgress = BackfillProgress(),
    val configured: Boolean = true,
) {
    /** What "Write" would write, oldest first. */
    val queue get() = plan?.let { RecapBackfill.queue(it, includeOldLayout, includeThisYear) }.orEmpty()
}

class RecapBackfillViewModel(private val app: OneDayApp) : ViewModel() {

    private val container = app.container
    private val choices = MutableStateFlow(false to false)

    val state: StateFlow<BackfillUiState> = combine(
        container.journal.observeRecaps(),
        container.journalPrefs.backfill,
        choices,
    ) { recaps, progress, (old, thisYear) ->
        // Planned afresh whenever a recap is written, so the counts go down as it runs.
        val plan = RecapBackfill.plan(
            entryDates = container.journal.entryDates(),
            recaps = recaps.associate { it.key to it.schemaVersion },
            now = LocalDateTime.now(),
        )
        BackfillUiState(plan, old, thisYear, progress, container.recapGenerator.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackfillUiState())

    fun setIncludeOldLayout(on: Boolean) {
        choices.value = on to choices.value.second
    }

    fun setIncludeThisYear(on: Boolean) {
        choices.value = choices.value.first to on
    }

    fun start() {
        val current = state.value
        val plan = current.plan ?: return
        val forced = buildSet {
            if (current.includeOldLayout) plan.oldLayout.forEach { add(it.key) }
            if (current.includeThisYear) plan.thisYear?.let { add(it.key) }
        }
        container.appScope.launch { BackfillWork.start(app, current.queue, forced) }
    }

    fun stop() {
        container.appScope.launch { BackfillWork.stop(app) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                RecapBackfillViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp)
            }
        }
    }
}

/**
 * Settings → Recaps → "Write recaps for past weeks and months". Says what it would
 * write, with counts, before anything starts; then shows how far it is, with Stop.
 */
@Composable
fun RecapBackfillScreen(
    onBack: () -> Unit,
    viewModel: RecapBackfillViewModel = viewModel(factory = RecapBackfillViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    val progress = state.progress

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, title = "Past recaps")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BodyText(
                "Write letters for past weeks and months that don't have one yet, from your entries as they are " +
                    "now. They're written one at a time, oldest first, and carry on in the background.",
            )

            when {
                !state.configured -> StateCard("Letters can't be written yet") {
                    BodyText("The AI service isn't set up in this build of the app. Your entries are safe.")
                }
                progress.running -> Running(progress, viewModel::stop)
                plan == null -> Unit
                else -> Choose(state, viewModel)
            }
        }
    }
}

@Composable
private fun Running(progress: BackfillProgress, onStop: () -> Unit) {
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hairline()
        Text(BackfillLabels.progress(progress), style = AppType.Heading, color = Palette.Ink)
        ProgressLine(if (progress.total == 0) 0f else progress.handled.toFloat() / progress.total)
        MetaText(progress.waiting ?: "You can leave this page. A notification shows how far it is.")
        QuietButton("Stop", onStop)
        MetaText("Stopping keeps the letters already written.")
    }
}

@Composable
private fun Choose(state: BackfillUiState, viewModel: RecapBackfillViewModel) {
    val plan = state.plan ?: return
    val min = RecapRules.MIN_DAYS_WITH_ENTRIES
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hairline()
        Text("What will be written", style = AppType.TitleSmall, color = Palette.Muted)
        if (plan.missingCount > 0) {
            Text(RecapBackfill.describe(plan), style = AppType.Heading, color = Palette.Ink)
            MetaText("Have enough entries and no letter yet.")
        } else {
            BodyText("Every past week and month with enough entries already has a letter.")
        }

        if (plan.oldLayout.isNotEmpty()) {
            val n = plan.oldLayout.size
            CheckRow(
                title = "Also rewrite $n recap${if (n == 1) "" else "s"} in the old layout",
                subtitle = "Each is replaced by one in the new layout.",
                checked = state.includeOldLayout,
                onChange = viewModel::setIncludeOldLayout,
            )
        }
        plan.thisYear?.let { year ->
            CheckRow(
                title = "Also write ${year.year} so far",
                subtitle = "The full year's letter still comes on 31 December.",
                checked = state.includeThisYear,
                onChange = viewModel::setIncludeThisYear,
            )
        }

        MetaText("A week, a month or a year needs $min or more days with entries.")

        val count = state.queue.size
        Spacer(Modifier.height(4.dp))
        RoseButton(
            if (count == 0) "Nothing to write" else "Write $count recap${if (count == 1) "" else "s"}",
            viewModel::start,
            enabled = count > 0,
        )

        // What the last run did, once it is over.
        if (state.progress.total > 0) MetaText("Last time: ${BackfillLabels.done(state.progress)}")
    }
}

/** A choice that is off unless ticked, with what it means under it. */
@Composable
private fun CheckRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Palette.Rose,
                uncheckedColor = Palette.Muted,
                checkmarkColor = Palette.Paper,
            ),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = AppType.Body, color = Palette.Ink)
            MetaText(subtitle)
        }
    }
}
