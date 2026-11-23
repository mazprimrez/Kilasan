package com.onedayonesecond.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalTime
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.journalDataStore: DataStore<Preferences> by preferencesDataStore(name = "journal")

/** Why a recap that was due is not there. */
enum class RecapFailureReason { OFFLINE, FAILED, NOT_SET_UP }

data class RecapFailure(val reason: RecapFailureReason, val at: Long)

/**
 * Where "Write recaps for past weeks and months" stands. [queue] holds the recap keys
 * still to write, oldest first; a key in [forced] is written even if a recap exists
 * (one in the old layout, or this year so far). [waiting] says why it is paused, if it is.
 */
data class BackfillProgress(
    val total: Int = 0,
    val written: Int = 0,
    val skipped: Int = 0,
    val queue: List<String> = emptyList(),
    val forced: Set<String> = emptySet(),
    val waiting: String? = null,
) {
    val running: Boolean get() = queue.isNotEmpty()

    /** How many have been dealt with, written or not. */
    val handled: Int get() = written + skipped
}

/** A thought that was being written when "Log a thought" was closed, kept for next time. */
data class ThoughtDraft(
    val page: Int,
    val text: String,
    val showInDiary: Boolean,
    /** A voice note or page photo already attached. Scratch files do not outlive the app, so it may be gone. */
    val mediaPath: String? = null,
    val mediaType: ThoughtType? = null,
)

/**
 * Small things the app remembers: the name letters are addressed to, whether and when
 * to remind, whether entries get a place, when the last backup finished, and failed recaps.
 */
class JournalPrefs(context: Context) {

    private val dataStore = context.applicationContext.journalDataStore

    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e
    }

    /** What "Dear …," says. Empty until the user gives a name. */
    val name: Flow<String> = safeData.map { it[NAME].orEmpty() }

    suspend fun setName(name: String) {
        dataStore.edit { it[NAME] = name.trim().take(MAX_NAME_LENGTH) }
    }

    /** Whether the daily reminder is on. It is, until switched off in Settings. */
    val reminderEnabled: Flow<Boolean> = safeData.map { it[REMINDER_ENABLED] ?: true }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    /** The time of day the reminder comes, when today has no second yet. */
    val reminderTime: Flow<LocalTime> = safeData.map { prefs ->
        prefs[REMINDER_MINUTE]?.takeIf { it in 0 until MINUTES_IN_DAY }?.let { LocalTime.of(it / 60, it % 60) }
            ?: DEFAULT_REMINDER_TIME
    }

    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { it[REMINDER_MINUTE] = time.hour * 60 + time.minute }
    }

    /** Whether new entries are tagged with where the phone is. On, until switched off in Settings. */
    val tagPlaces: Flow<Boolean> = safeData.map { it[TAG_PLACES] ?: true }

    suspend fun setTagPlaces(enabled: Boolean) {
        dataStore.edit { it[TAG_PLACES] = enabled }
    }

    /** When the last backup to Google Drive finished, in epoch milliseconds. Null before the first. */
    val lastBackupAt: Flow<Long?> = safeData.map { it[LAST_BACKUP_AT] }

    suspend fun setLastBackupAt(millis: Long) {
        dataStore.edit { it[LAST_BACKUP_AT] = millis }
    }

    /** True once the app has asked for location, so it asks on first use and never nags. */
    suspend fun locationAsked(): Boolean = safeData.first()[LOCATION_ASKED] ?: false

    suspend fun setLocationAsked() {
        dataStore.edit { it[LOCATION_ASKED] = true }
    }

    /** Recaps that were tried and could not be written, by recap key. */
    val recapFailures: Flow<Map<String, RecapFailure>> = safeData.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(FAILURE_PREFIX)) return@mapNotNull null
            val parts = (value as? String)?.split('|') ?: return@mapNotNull null
            val reason = RecapFailureReason.entries.firstOrNull { it.name == parts.getOrNull(0) }
            val at = parts.getOrNull(1)?.toLongOrNull()
            if (reason == null || at == null) null else key.name.removePrefix(FAILURE_PREFIX) to RecapFailure(reason, at)
        }.toMap()
    }

    suspend fun setRecapFailure(recapKey: String, failure: RecapFailure?) {
        val key = stringPreferencesKey(FAILURE_PREFIX + recapKey)
        dataStore.edit { if (failure == null) it.remove(key) else it[key] = "${failure.reason.name}|${failure.at}" }
    }

    val backfill: Flow<BackfillProgress> = safeData.map { prefs ->
        BackfillProgress(
            total = prefs[BACKFILL_TOTAL] ?: 0,
            written = prefs[BACKFILL_WRITTEN] ?: 0,
            skipped = prefs[BACKFILL_SKIPPED] ?: 0,
            queue = prefs[BACKFILL_QUEUE].orEmpty().split(',').filter { it.isNotBlank() },
            forced = prefs[BACKFILL_FORCED].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            waiting = prefs[BACKFILL_WAITING],
        )
    }

    /** Starts a new run over [queue], forgetting the counts of the last one. */
    suspend fun startBackfill(queue: List<String>, forced: Set<String>) {
        dataStore.edit {
            it[BACKFILL_QUEUE] = queue.joinToString(",")
            it[BACKFILL_FORCED] = forced.joinToString(",")
            it[BACKFILL_TOTAL] = queue.size
            it[BACKFILL_WRITTEN] = 0
            it[BACKFILL_SKIPPED] = 0
            it.remove(BACKFILL_WAITING)
        }
    }

    /** Takes [key] off the front of the queue, counting it as written or skipped. */
    suspend fun advanceBackfill(key: String, written: Boolean) {
        dataStore.edit { prefs ->
            val queue = prefs[BACKFILL_QUEUE].orEmpty().split(',').filter { it.isNotBlank() && it != key }
            prefs[BACKFILL_QUEUE] = queue.joinToString(",")
            if (written) {
                prefs[BACKFILL_WRITTEN] = (prefs[BACKFILL_WRITTEN] ?: 0) + 1
            } else {
                prefs[BACKFILL_SKIPPED] = (prefs[BACKFILL_SKIPPED] ?: 0) + 1
            }
            prefs.remove(BACKFILL_WAITING)
        }
    }

    suspend fun setBackfillWaiting(reason: String?) {
        dataStore.edit { if (reason == null) it.remove(BACKFILL_WAITING) else it[BACKFILL_WAITING] = reason }
    }

    /** Stops a run: what is left is dropped, the counts stay so the page can say what was done. */
    suspend fun stopBackfill() {
        dataStore.edit {
            it[BACKFILL_QUEUE] = ""
            it.remove(BACKFILL_WAITING)
        }
    }

    /** The draft kept for a book, or null. One per book, so switching books does not lose either. */
    suspend fun thoughtDraft(bookId: Long): ThoughtDraft? {
        val stored = safeData.first()[stringPreferencesKey(DRAFT_PREFIX + bookId)] ?: return null
        return try {
            val json = org.json.JSONObject(stored)
            ThoughtDraft(
                page = json.optInt("page", 1),
                text = json.optString("text"),
                showInDiary = json.optBoolean("showInDiary", true),
                mediaPath = json.optString("mediaPath").ifEmpty { null },
                mediaType = ThoughtType.entries.firstOrNull { it.name == json.optString("mediaType") },
            )
        } catch (e: org.json.JSONException) {
            null
        }
    }

    suspend fun setThoughtDraft(bookId: Long, draft: ThoughtDraft?) {
        val key = stringPreferencesKey(DRAFT_PREFIX + bookId)
        dataStore.edit {
            if (draft == null) {
                it.remove(key)
            } else {
                it[key] = org.json.JSONObject()
                    .put("page", draft.page)
                    .put("text", draft.text)
                    .put("showInDiary", draft.showInDiary)
                    .put("mediaPath", draft.mediaPath.orEmpty())
                    .put("mediaType", draft.mediaType?.name.orEmpty())
                    .toString()
            }
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Eight in the evening: late enough that the day has happened, early enough to still film it. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)
        private const val MINUTES_IN_DAY = 24 * 60
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        private val TAG_PLACES = booleanPreferencesKey("tag_places")
        private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        private const val DRAFT_PREFIX = "thought_draft_"
        private val NAME = stringPreferencesKey("name")
        private val LOCATION_ASKED = booleanPreferencesKey("location_asked")
        private const val FAILURE_PREFIX = "recap_failure_"
        private val BACKFILL_QUEUE = stringPreferencesKey("backfill_queue")
        private val BACKFILL_FORCED = stringPreferencesKey("backfill_forced")
        private val BACKFILL_TOTAL = intPreferencesKey("backfill_total")
        private val BACKFILL_WRITTEN = intPreferencesKey("backfill_written")
        private val BACKFILL_SKIPPED = intPreferencesKey("backfill_skipped")
        private val BACKFILL_WAITING = stringPreferencesKey("backfill_waiting")
    }
}
