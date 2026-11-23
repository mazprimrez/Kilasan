package com.onedayonesecond.backup

import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.RecapRows
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** One entry as it is written in a day's backup file. [media] is the name of its file beside it. */
data class EntryBackup(
    val createdAt: Long,
    val type: EntryType,
    val text: String?,
    val media: String?,
    val place: String?,
    /** When its words last changed. Null in older backups and when it equals [createdAt]. */
    val updatedAt: Long? = null,
)

/** A day's journal as it is written in the backup: mood, place and entries. */
data class DayBackup(
    val date: LocalDate,
    val mood: Mood?,
    val place: String?,
    val entries: List<EntryBackup>,
) {
    val isEmpty: Boolean get() = mood == null && place == null && entries.isEmpty()
}

/**
 * The journal's files in Drive: one small, readable JSON file per day next to that
 * day's clip, and one per recap.
 *
 * ```
 * {
 *   "date": "2026-11-16",
 *   "mood": "GOOD",
 *   "place": "Kemang, Jakarta Selatan",
 *   "entries": [
 *     {"createdAt": 1793500000000, "type": "WRITTEN", "text": "Coffee before work."},
 *     {"createdAt": 1793510000000, "type": "VOICE", "text": "…", "media": "2026-11-16-1793510000000.m4a"}
 *   ]
 * }
 * ```
 */
object JournalFile {

    /**
     * Entries are written oldest first and keys in a fixed order, so the same day
     * always produces the same bytes. That is what lets a backup see "nothing
     * changed" by comparing checksums instead of uploading every file every time.
     */
    fun encodeDay(day: DayBackup): ByteArray {
        val entries = JSONArray()
        for (entry in day.entries.sortedWith(ORDER)) {
            val json = JSONObject()
                .put("createdAt", entry.createdAt)
                .put("type", entry.type.name)
            entry.text?.let { json.put("text", it) }
            entry.media?.let { json.put("media", it) }
            entry.place?.let { json.put("place", it) }
            // Only written for an entry edited after it was made, so the files of days
            // nobody touched keep their bytes and are not uploaded again.
            entry.updatedAt?.takeIf { it != entry.createdAt }?.let { json.put("updatedAt", it) }
            entries.put(json)
        }
        val json = JSONObject().put("date", day.date.toString())
        day.mood?.let { json.put("mood", it.name) }
        day.place?.let { json.put("place", it) }
        json.put("entries", entries)
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    /**
     * Reads a day's file back. Null for a damaged file or one without a date; an
     * entry that cannot be understood is skipped rather than failing the whole day.
     */
    fun decodeDay(bytes: ByteArray): DayBackup? {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            return null
        }
        val date = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull() ?: return null
        val array = json.optJSONArray("entries")
        val entries = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val entry = array?.optJSONObject(index) ?: return@mapNotNull null
            val type = EntryType.entries.firstOrNull { it.name == entry.optString("type") } ?: return@mapNotNull null
            val createdAt = entry.optLong("createdAt", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            EntryBackup(
                createdAt = createdAt,
                type = type,
                text = entry.textOrNull("text"),
                media = entry.textOrNull("media"),
                place = entry.textOrNull("place"),
                updatedAt = entry.optLong("updatedAt", -1).takeIf { it >= 0 && it != createdAt },
            )
        }
        return DayBackup(
            date = date,
            mood = Mood.entries.firstOrNull { it.name == json.optString("mood") },
            place = json.textOrNull("place"),
            entries = entries.sortedWith(ORDER),
        )
    }

    /** A recap's file: the whole row, layout 2 fields and the version kept for Undo included. */
    fun encodeRecap(recap: Recap): ByteArray = RecapRows.toJson(recap).toString(2).toByteArray(Charsets.UTF_8)

    /**
     * Null for a damaged file or one missing its key, title or quote date. Files written
     * before layout 2 have none of its fields and are read as layout-1 recaps.
     */
    fun decodeRecap(bytes: ByteArray): Recap? = RecapRows.decode(String(bytes, Charsets.UTF_8))

    private fun JSONObject.textOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }

    private val ORDER = compareBy<EntryBackup>({ it.createdAt }, { it.type.name }, { it.media.orEmpty() })
}

/**
 * Works out what a day's file in Drive should say after a backup. Pure, so it can be
 * unit-tested on a plain JVM.
 *
 * Like the captions, it is a merge and not a copy of the phone, for the same reason:
 * a new phone that has only a mood for a day must not wipe that day's entries from
 * the backup. So entries that are in Drive but not on the phone are kept. (That
 * also means an entry deleted on the phone stays in Drive, as a deleted clip does.)
 */
object JournalSync {

    fun mergeDay(remote: DayBackup?, local: DayBackup): DayBackup {
        if (remote == null) return local
        // The same entry on both sides is the phone's version: it may have been edited.
        val merged = LinkedHashMap<Triple<Long, EntryType, String>, EntryBackup>()
        for (entry in remote.entries) merged[keyOf(entry)] = entry
        for (entry in local.entries) merged[keyOf(entry)] = entry
        return DayBackup(
            date = local.date,
            // The phone's mood and place win where it has them; otherwise Drive's are kept.
            mood = local.mood ?: remote.mood,
            place = local.place ?: remote.place,
            entries = merged.values.toList(),
        )
    }

    private fun keyOf(entry: EntryBackup) = Triple(entry.createdAt, entry.type, entry.media.orEmpty())
}
