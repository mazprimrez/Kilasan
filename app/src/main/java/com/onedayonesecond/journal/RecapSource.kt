package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import java.security.MessageDigest
import java.time.LocalDate

/**
 * What a recap was written from, as a fingerprint: every entry of the period by id and
 * the moment its words last changed, and every day's mood. A recap whose stored
 * fingerprint differs from today's was written from an older version of the period.
 */
object RecapSource {

    fun hash(entries: Collection<Entry>, moods: Map<LocalDate, Mood?>): String {
        val text = buildString {
            entries.sortedBy { it.id }.forEach { append("e:").append(it.id).append(':').append(it.updatedAt).append('\n') }
            moods.filterValues { it != null }.toSortedMap().forEach { (date, mood) ->
                append("m:").append(date).append(':').append(mood!!.name).append('\n')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * How the period moved on since [recap] was written, or null when it has not (or
     * when [recap] is in layout 1, which kept no fingerprint; it says "old layout" instead).
     *
     * @property added entries added since, when that is what changed; 0 when entries were
     * only edited or deleted, or a mood changed.
     */
    data class Staleness(val added: Int)

    fun staleness(recap: Recap, entries: Collection<Entry>, moods: Map<LocalDate, Mood?>): Staleness? {
        if (recap.schemaVersion < Recap.LAYOUT_V2 || recap.sourceHash.isEmpty()) return null
        if (hash(entries, moods) == recap.sourceHash) return null
        return Staleness(added = (entries.size - recap.entryCount).coerceAtLeast(0))
    }

    /** "You've added 2 entries since this was written." or, when nothing was added, a plainer line. */
    fun describe(staleness: Staleness): String = when (staleness.added) {
        0 -> "Your entries changed since this was written."
        1 -> "You've added 1 entry since this was written."
        else -> "You've added ${staleness.added} entries since this was written."
    }
}
