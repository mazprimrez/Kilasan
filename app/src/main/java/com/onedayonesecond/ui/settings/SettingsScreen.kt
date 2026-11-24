package com.onedayonesecond.ui.settings

import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.journal.EntryTagger
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.BuildConfig
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.location.PlaceFinder
import com.onedayonesecond.media.MediaStoreExporter
import com.onedayonesecond.ui.backup.BackupLabels
import com.onedayonesecond.ui.backup.BackupViewModel
import com.onedayonesecond.ui.compile.CompileViewModel
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.work.BackfillLabels
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.SwitchRow
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.TintCard
import com.onedayonesecond.ui.theme.appTextFieldColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything that is set once and then left alone, in one list: what letters call you,
 * the daily reminder, the text on a reel, places, the backup, and what was deleted.
 * It is reached from the gear on each tab.
 *
 * The reel's and the backup's view models are the activity's, the same ones the Reel
 * tab shows, so their lines here say what those pages say.
 */
@UnstableApi
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTextSettings: () -> Unit,
    onBackup: () -> Unit,
    onRecapBackfill: () -> Unit,
    onRecentlyDeleted: () -> Unit,
    onAbout: () -> Unit,
    compile: CompileViewModel,
    backup: BackupViewModel,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reel by compile.state.collectAsStateWithLifecycle()
    val drive by backup.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingName by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    // Both switches need the phone's leave as well as the user's. That leave can be
    // given or taken away in the phone's own settings, so it is looked at again on return.
    var canNotify by remember { mutableStateOf(context.canNotify()) }
    var canLocate by remember { mutableStateOf(viewModel.hasLocationPermission) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = context.canNotify()
        canLocate = viewModel.hasLocationPermission
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canNotify = granted
        if (!granted) viewModel.explainBlocked("Notifications aren't allowed for Kilasan yet.") { context.openAppSettings() }
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        canLocate = result.values.any { it }
        if (!canLocate) viewModel.explainBlocked("Location isn't allowed for Kilasan yet.") { context.openAppSettings() }
    }

    val reminderOn = state.reminderEnabled && canNotify
    val placesOn = state.tagPlaces && canLocate

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, title = "Settings")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Letters", first = true) {
                ActionRow(
                    "What letters call you",
                    { editingName = true },
                    subtitle = if (state.name.isBlank()) "Letters start without a name" else "“Dear ${state.name},”",
                    icon = LineIcons.Write,
                    trailing = { Chevron() },
                )
                ActionRow(
                    "When letters arrive",
                    onClick = null,
                    subtitle = "Weekly on Sunday night, monthly on the 1st, yearly on 31 December",
                    icon = LineIcons.Letter,
                )
            }

            Section("Recaps") {
                ActionRow(
                    "Write recaps for past weeks and months",
                    onRecapBackfill,
                    subtitle = if (state.backfill.running) {
                        BackfillLabels.progress(state.backfill)
                    } else {
                        "For weeks and months that have no letter yet"
                    },
                    icon = LineIcons.Recaps,
                    trailing = { Chevron() },
                )
                SwitchRow(
                    "Show people in Patterns",
                    state.showPeople,
                    viewModel::setShowPeople,
                    subtitle = "The names you write about, and how often they come up",
                    icon = LineIcons.Person,
                )
                state.tagging?.let { progress -> TaggingRow(progress, state.taggingStopped, viewModel::setTaggingStopped) }
            }

            Section("Reminder") {
                SwitchRow(
                    "Daily reminder",
                    reminderOn,
                    { on ->
                        viewModel.setReminderEnabled(on)
                        if (on && !canNotify) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.explainBlocked("Notifications aren't allowed for Kilasan yet.") {
                                    context.openAppSettings()
                                }
                            }
                        }
                    },
                    subtitle = "If today has no second yet",
                    icon = LineIcons.Bell,
                )
                ActionRow(
                    "Remind me at",
                    { pickingTime = true },
                    icon = LineIcons.Clock,
                    enabled = reminderOn,
                    trailing = {
                        Text(
                            state.reminderTime.format(CLOCK),
                            style = AppType.Value,
                            color = if (reminderOn) Palette.Ink else Palette.Muted,
                        )
                    },
                )
            }

            Section("Reel") {
                ActionRow(
                    "Text on each second",
                    onTextSettings,
                    subtitle = reel.textSummary,
                    icon = LineIcons.Text,
                    trailing = { Chevron() },
                )
                ActionRow(
                    "Saves to",
                    onClick = null,
                    subtitle = "Movies/${MediaStoreExporter.ALBUM}",
                    icon = LineIcons.Download,
                )
            }

            Section("Places") {
                SwitchRow(
                    "Tag entries with a place",
                    placesOn,
                    { on ->
                        viewModel.setTagPlaces(on)
                        if (on && !canLocate) locationLauncher.launch(PlaceFinder.PERMISSIONS)
                    },
                    subtitle = "From your phone’s location, when you add something",
                    icon = LineIcons.Place,
                )
            }

            Section("Backup") {
                ActionRow(
                    "Google Drive",
                    onBackup,
                    subtitle = BackupLabels.status(drive),
                    icon = LineIcons.Cloud,
                    trailing = { Chevron() },
                )
            }

            Section("Your data") {
                ActionRow(
                    "Recently deleted",
                    onRecentlyDeleted,
                    subtitle = "Kept for 30 days, then removed",
                    icon = LineIcons.Trash,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.deletedCount > 0) {
                                Text(state.deletedCount.toString(), style = AppType.Value, color = Palette.Ink)
                                Spacer(Modifier.width(8.dp))
                            }
                            Chevron()
                        }
                    },
                )
                ActionRow(
                    "About Kilasan",
                    onAbout,
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · what is sent where",
                    icon = LineIcons.Info,
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editingName) {
        NameDialog(
            current = state.name,
            onSave = {
                viewModel.setName(it)
                editingName = false
            },
            onDismiss = { editingName = false },
        )
    }

    if (pickingTime) {
        ReminderTimeDialog(
            current = state.reminderTime,
            onSave = {
                viewModel.setReminderTime(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** A group of rows under a quiet heading, set off from the group above by a hairline. */
@Composable
private fun Section(title: String, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (!first) Hairline()
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            style = AppType.TitleSmall,
            color = Palette.Muted,
            modifier = Modifier
                .padding(start = ScreenPadding, top = 16.dp, end = ScreenPadding, bottom = 4.dp)
                .semantics { heading() },
        )
        content()
    }
}

/** At the end of a row that opens a page of its own. */
@Composable
private fun Chevron() {
    Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
}

/**
 * The name letters are addressed to, with how the next letter will open underneath so
 * the choice can be seen before it is saved. An empty name is allowed.
 */
@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    val name = text.trim()
    AppDialog(
        onDismiss = onDismiss,
        title = "What should letters call you?",
        buttons = {
            TextAction("Cancel", onDismiss)
            TextAction("Save", { onSave(text) })
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(JournalPrefs.MAX_NAME_LENGTH) },
            label = { Text("Name") },
            supportingText = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("A nickname is fine")
                    Text("${text.length} / ${JournalPrefs.MAX_NAME_LENGTH}")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            colors = appTextFieldColors(),
            textStyle = AppType.Body,
            modifier = Modifier.fillMaxWidth(),
        )
        TintCard(Modifier.padding(top = 16.dp), padding = 16.dp) {
            Text("Your next letter opens like this", style = AppType.Tab, color = Palette.Muted)
            if (name.isEmpty()) {
                MetaText("With its first line, and no greeting.", Modifier.padding(top = 8.dp))
            } else {
                Text("Dear $name,", style = AppType.Quote, color = Palette.Ink, modifier = Modifier.padding(top = 8.dp))
            }
        }
        SmallText(
            "Leave it empty and letters start without a name. Letters already written keep theirs.",
            Modifier.padding(top = 16.dp),
        )
    }
}

/** The time of day the reminder comes, typed as hours and minutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(current: LocalTime, onSave: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
    AppDialog(
        onDismiss = onDismiss,
        title = "Remind me at",
        buttons = {
            TextAction("Cancel", onDismiss)
            TextAction("Save", { onSave(LocalTime.of(picker.hour, picker.minute)) })
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimeInput(
                state = picker,
                colors = TimePickerDefaults.colors(
                    timeSelectorSelectedContainerColor = Palette.Blush,
                    timeSelectorSelectedContentColor = Palette.Ink,
                    timeSelectorUnselectedContainerColor = Palette.BlushTint,
                    timeSelectorUnselectedContentColor = Palette.Ink,
                ),
            )
        }
        SmallText("Only if that day has no second yet. Otherwise the phone stays quiet.")
    }
}

/** Times of day are written the way a timecode is: 20:00. */
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** True when the phone lets the app show notifications at all. */
private fun Context.canNotify(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** The app's page in the phone's settings, where a permission that was refused can be allowed. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * How far tagging entries for Patterns has got: a progress line while older entries
 * are still being tagged, and Stop or Resume. Tagging carries on when the page is left.
 */
@Composable
private fun TaggingRow(progress: EntryTagger.Progress, stopped: Boolean, onStopped: (Boolean) -> Unit) {
    val subtitle = when {
        progress.total == 0 -> "Entries are tagged with their topics, habits and people as you write"
        progress.done -> "All ${progress.total} entries are tagged"
        stopped -> "Stopped at ${progress.tagged} of ${progress.total}. Nothing is sent until you resume."
        else -> "${progress.tagged} of ${progress.total} entries tagged. Needs a connection."
    }
    Column {
        ActionRow(
            "Tags for Patterns",
            onClick = null,
            subtitle = subtitle,
            icon = LineIcons.Bars,
            trailing = if (progress.done && !stopped) {
                null
            } else {
                { TextAction(if (stopped) "Resume" else "Stop", { onStopped(!stopped) }) }
            },
        )
        if (!progress.done && progress.total > 0) {
            ProgressLine(
                progress.tagged.toFloat() / progress.total,
                Modifier.padding(start = 72.dp, end = ScreenPadding, bottom = 8.dp),
            )
        }
    }
}
