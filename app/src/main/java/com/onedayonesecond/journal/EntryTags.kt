package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryTag
import java.security.MessageDigest

/** What one entry is about, as the AI service tagged it. */
data class TagSet(
    val topics: List<String> = emptyList(),
    val habits: List<String> = emptyList(),
    val people: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = topics.isEmpty() && habits.isEmpty() && people.isEmpty()

    /**
     * The rows to store for [entryId]. An entry about nothing in particular gets one
     * [EntryTag.KIND_NONE] row, so it is not sent again until its words change.
     */
    fun rows(entryId: Long, sourceHash: String, taggedAt: Long): List<EntryTag> {
        fun of(kind: String, values: List<String>) =
            values.map { EntryTag(entryId = entryId, kind = kind, value = it, taggedAt = taggedAt, sourceHash = sourceHash) }
        val rows = of(EntryTag.KIND_TOPIC, topics) + of(EntryTag.KIND_HABIT, habits) + of(EntryTag.KIND_PERSON, people)
        return rows.ifEmpty { of(EntryTag.KIND_NONE, listOf("")) }
    }

    companion object {
        /** The tags in [rows] (all of one entry) as a set; the "none" marker reads as an empty set. */
        fun of(rows: Collection<EntryTag>): TagSet = TagSet(
            topics = rows.filter { it.kind == EntryTag.KIND_TOPIC }.map { it.value },
            habits = rows.filter { it.kind == EntryTag.KIND_HABIT }.map { it.value },
            people = rows.filter { it.kind == EntryTag.KIND_PERSON }.map { it.value },
        )
    }
}

/** Fingerprints an entry's words, so tags know which version of them they describe. */
object TagHash {

    /** Null for an entry without words: there is nothing to tag. */
    fun of(text: String?): String? {
        val words = text?.trim()?.replace(Regex("\\s+"), " ")?.ifEmpty { null } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(words.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * True when [entry] has words that were never tagged, or were tagged in an earlier
     * version. Entries still waiting to be typed out are left until they are done.
     */
    fun needsTags(entry: Entry, taggedHash: String?): Boolean {
        if (entry.processing != com.onedayonesecond.data.ProcessingState.DONE) return false
        val hash = of(entry.text) ?: return false
        return hash != taggedHash
    }
}
