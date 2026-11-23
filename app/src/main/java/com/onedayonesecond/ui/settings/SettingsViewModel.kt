package com.onedayonesecond.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.BackfillProgress
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.ui.theme.MessageAction
import com.onedayonesecond.work.Reminders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class SettingsUiState(
    /** What letters call the user. Empty until they give a name. */
    val name: String = "",
    val reminderEnabled: Boolean = true,
    val reminderTime: LocalTime = JournalPrefs.DEFAULT_REMINDER_TIME,
    /** The switch itself; whether a place is really found also needs the phone's permission. */
    val tagPlaces: Boolean = true,
    /** How many seconds are waiting in Recently deleted. */
    val deletedCount: Int = 0,
    /** Where writing past recaps stands, for the row's second line. */
    val backfill: BackfillProgress = BackfillProgress(),
)

/**
 * The settings that are not about one reel or one backup: the name on letters, the
 * daily reminder, and whether entries get a place. The reel's text and the backup keep
 * their own view models, which their pages share with the Reel tab.
 */
class SettingsViewModel(private val app: OneDayApp) : ViewModel() {

    private val prefs = app.container.journalPrefs

    val state: StateFlow<SettingsUiState> = combine(
        prefs.name,
        prefs.reminderEnabled,
        prefs.reminderTime,
        prefs.tagPlaces,
        app.container.repository.observeDeleted(),
    ) { name, reminderEnabled, reminderTime, tagPlaces, deleted ->
        SettingsUiState(name, reminderEnabled, reminderTime, tagPlaces, deleted.size)
    }.combine(prefs.backfill) { state, backfill -> state.copy(backfill = backfill) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Whether the phone lets the app know where it is. Asked again each time, since it can change outside the app. */
    val hasLocationPermission: Boolean
        get() = app.container.places.hasPermission

    fun setName(name: String) {
        viewModelScope.launch { prefs.setName(name) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setReminderEnabled(enabled)
            if (enabled) {
                Reminders.schedule(app, state.value.reminderTime, replace = true)
            } else {
                Reminders.cancel(app)
            }
        }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch {
            prefs.setReminderTime(time)
            // The check already waiting is for the old time, so it is replaced.
            if (state.value.reminderEnabled) Reminders.schedule(app, time, replace = true)
        }
    }

    fun setTagPlaces(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setTagPlaces(enabled)
            // Asked here, so the add sheet does not ask a second time.
            if (enabled) prefs.setLocationAsked()
        }
    }

    /** The phone said no: say so, with the way to the place where it can still be allowed. */
    fun explainBlocked(text: String, openSettings: () -> Unit) {
        app.container.messages.show(text, MessageAction("Open settings", run = { openSettings() }))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp)
            }
        }
    }
}
