package com.onedayonesecond

import kotlinx.coroutines.flow.first
import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.work.BackfillWork
import com.onedayonesecond.work.BookWork
import com.onedayonesecond.work.JournalWork
import com.onedayonesecond.work.Reminders
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@UnstableApi
class OneDayApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        BackfillWork.createChannel(this)
        // Recordings and reels from a previous run are scratch data by
        // definition — a finished clip has already been moved into filesDir.
        container.storage.clearCaptures()
        container.storage.clearReels()

        JournalWork.scheduleRecaps(this)
        container.appScope.launch {
            // The daily reminder, at the time chosen in Settings, unless it is switched off there.
            if (container.journalPrefs.reminderEnabled.first()) {
                Reminders.schedule(this@OneDayApp, container.journalPrefs.reminderTime.first())
            } else {
                Reminders.cancel(this@OneDayApp)
            }
            // Seconds deleted more than thirty days ago leave the trash for good.
            container.repository.purgeDeleted()
            // Voice notes and pages saved while offline get another go.
            if (container.journal.unprocessedEntries().isNotEmpty() || container.books.unprocessedThoughts().isNotEmpty()) {
                JournalWork.typeOutWhenOnline(this@OneDayApp)
            }
            // Books added by hand or offline still waiting for a cover get another go.
            if (container.books.booksMissingCover().isNotEmpty()) BookWork.fetchCoversWhenOnline(this@OneDayApp)
        }
        container.appScope.launch {
            // Tags for Patterns: whenever an entry is added, deleted or has its words changed
            // (written, edited, typed out, restored), and once at start for anything not yet
            // tagged, which is how entries written before tags existed get theirs.
            combine(container.journal.observeEntriesVersion(), container.journalPrefs.taggingStopped) { v, stopped -> v to stopped }
                .distinctUntilChanged()
                .debounce(TAG_SETTLE_MS)
                .collect { (_, stopped) ->
                    if (!stopped && container.entryTagger.isConfigured && container.entryTagger.pending().isNotEmpty()) {
                        JournalWork.tagWhenOnline(this@OneDayApp)
                    }
                }
        }
    }

    private companion object {
        /** Typing a few entries in a row starts one run, not one per entry. */
        const val TAG_SETTLE_MS = 5_000L
    }
}
