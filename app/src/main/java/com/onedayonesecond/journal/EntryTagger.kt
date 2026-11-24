package com.onedayonesecond.journal

import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.JournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/**
 * Has entries tagged with their topics, habits and people, a batch at a time, oldest
 * first. An entry is tagged again when its words change; its old tags stop counting
 * as soon as they do (see [PatternsCalculator]).
 */
class EntryTagger(
    private val journal: JournalRepository,
    private val ai: JournalAi,
) {

    /** How many entries with words there are, and how many have tags for their words as they are. */
    data class Progress(val total: Int, val tagged: Int) {
        val left: Int get() = total - tagged
        val done: Boolean get() = tagged >= total
    }

    val isConfigured: Boolean get() = ai.isConfigured

    @OptIn(ExperimentalCoroutinesApi::class)
    fun progress(): Flow<Progress> =
        combine(journal.observeEntriesVersion(), journal.observeTaggedHashes()) { _, hashes -> hashes }
            .mapLatest { hashes ->
                val tagged = hashes.associate { it.entryId to it.sourceHash }
                val withWords = journal.allEntries().filter { TagHash.of(it.text) != null }
                Progress(
                    total = withWords.size,
                    tagged = withWords.count { !TagHash.needsTags(it, tagged[it.id]) },
                )
            }

    /** Entries whose words have no tags yet, or tags for older words, oldest first. */
    suspend fun pending(): List<Entry> {
        val tagged = journal.taggedHashes()
        return journal.allEntries().filter { TagHash.needsTags(it, tagged[it.id]) }
    }

    /**
     * Tags the next batch. Throws what [JournalAi.tag] throws.
     *
     * @return how many entries were tagged: 0 when nothing was waiting, or when the
     * answer left every entry out.
     */
    suspend fun tagNextBatch(): Int {
        val batch = pending().take(TagRequest.MAX_ENTRIES)
        if (batch.isEmpty()) return 0
        val hashes = batch.associate { it.id to TagHash.of(it.text)!! }
        val reply = ai.tag(TagRequest.build(batch, journal.vocabulary()))
        val tags = TagRequest.parse(reply, hashes.keys)
        var stored = 0
        for ((id, set) in tags) {
            // The words may have changed while the request was out; those tags would describe old words.
            val now = journal.entry(id) ?: continue
            if (TagHash.of(now.text) != hashes[id]) continue
            journal.setTags(id, set, hashes.getValue(id))
            stored++
        }
        return stored
    }
}
