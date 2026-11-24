package com.onedayonesecond.data

import com.onedayonesecond.journal.TagSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** The diary side of a day: its mood and place, its entries and their tags, and the recaps written from them. */
class JournalRepository(
    private val dao: JournalDao,
    private val tagDao: TagDao,
    val storage: ClipStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeMeta(date: LocalDate): Flow<DayMeta?> = dao.observeMeta(date)

    fun observeMetaBetween(start: LocalDate, end: LocalDate): Flow<List<DayMeta>> =
        dao.observeMetaBetween(start, end)

    suspend fun meta(date: LocalDate): DayMeta? = dao.meta(date)

    suspend fun metaBetween(start: LocalDate, end: LocalDate): List<DayMeta> = dao.metaBetween(start, end)

    suspend fun allMeta(): List<DayMeta> = dao.allMeta()

    suspend fun setMood(date: LocalDate, mood: Mood?) {
        dao.upsertMeta((dao.meta(date) ?: DayMeta(date)).copy(mood = mood))
    }

    /** Sets or, with blank text, clears the day's place. */
    suspend fun setPlace(date: LocalDate, place: String?) {
        dao.upsertMeta((dao.meta(date) ?: DayMeta(date)).copy(placeName = place.tidyPlace()))
    }

    /** Gives the day a place only if it has none yet, so a place the user chose is never overwritten. */
    suspend fun notePlace(date: LocalDate, place: String?) {
        val tidy = place.tidyPlace() ?: return
        val current = dao.meta(date)
        if (current?.placeName == null) dao.upsertMeta((current ?: DayMeta(date)).copy(placeName = tidy))
    }

    /** Used by restore, which brings back a whole day's mood and place at once. */
    suspend fun putMeta(meta: DayMeta) = dao.upsertMeta(meta)

    fun observeEntries(date: LocalDate): Flow<List<Entry>> = dao.observeEntries(date)

    fun observeEntriesBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>> =
        dao.observeEntriesBetween(start, end)

    suspend fun entriesBetween(start: LocalDate, end: LocalDate): List<Entry> = dao.entriesBetween(start, end)

    suspend fun allEntries(): List<Entry> = dao.allEntries()

    suspend fun entry(id: Long): Entry? = dao.entry(id)

    suspend fun unprocessedEntries(): List<Entry> = dao.unprocessedEntries()

    suspend fun earliestEntryDate(): LocalDate? = dao.earliestEntryDate()

    suspend fun entryDates(): List<LocalDate> = dao.entryDates()

    suspend fun addWritten(date: LocalDate, text: String, place: String?): Entry {
        val entry = Entry(
            date = date,
            createdAt = now(),
            type = EntryType.WRITTEN,
            text = text.trim(),
            placeName = place.tidyPlace(),
        )
        notePlace(date, place)
        return entry.copy(id = dao.insertEntry(entry))
    }

    /**
     * Changes an entry's words. Also how a typed-out voice note or page is corrected by
     * hand, or typed by hand when it could not be typed out. Words the user gives mark
     * the entry as done, so nothing typed out later can replace them.
     */
    suspend fun setText(entry: Entry, text: String) {
        val words = text.trim()
        if (words.isNotEmpty() || !entry.needsText) {
            dao.setEntryTextByHand(entry.id, words, now())
        } else {
            // Emptied out: the words are gone, but it may still be typed out.
            dao.clearEntryText(entry.id, words, now())
        }
    }

    /** Sets where an entry was made, once that is known, and gives the day a place if it has none. */
    suspend fun attachPlace(entries: List<Entry>, place: String?) {
        val tidy = place.tidyPlace() ?: return
        entries.forEach { dao.setEntryPlace(it.id, tidy) }
        entries.firstOrNull()?.let { notePlace(it.date, tidy) }
    }

    /**
     * Records an entry whose substance is a file. [scratch] is moved into the journal
     * folder; entries that [Entry.needsText] start out [ProcessingState.PENDING].
     *
     * @param createdAt lets several photos chosen together share one moment, which is
     * how the day page knows to show them as one row.
     */
    suspend fun addMedia(
        date: LocalDate,
        type: EntryType,
        scratch: File,
        extension: String,
        place: String?,
        createdAt: Long = now(),
    ): Entry = withContext(Dispatchers.IO) {
        require(type != EntryType.WRITTEN) { "Written entries have no file" }
        val destination = storage.newJournalFile(date, createdAt, extension)
        if (!scratch.renameTo(destination)) {
            scratch.copyTo(destination, overwrite = true)
            scratch.delete()
        }
        val entry = Entry(
            date = date,
            createdAt = createdAt,
            type = type,
            mediaPath = destination.absolutePath,
            placeName = place.tidyPlace(),
        )
        val pending = if (entry.needsText) entry.copy(processing = ProcessingState.PENDING) else entry
        notePlace(date, place)
        pending.copy(id = dao.insertEntry(pending))
    }

    /** Used by restore: the entry is recorded as it is, media already in place. */
    suspend fun putEntry(entry: Entry): Entry = entry.copy(id = dao.insertEntry(entry.copy(id = 0)))

    /** Marks an entry as waiting or failed. An entry that is already done stays done. */
    suspend fun setProcessing(id: Long, state: ProcessingState) = dao.setEntryProcessing(id, state)

    /**
     * Stores the words typed out for an entry and marks it done.
     *
     * @return false if the entry was already done, which means the user typed its words
     * while it was being typed out. Theirs are kept.
     */
    suspend fun finishTypingOut(id: Long, text: String): Boolean = dao.finishTypingOut(id, text.trim(), now()) > 0

    /** Deletes the entry, its tags and its file. */
    suspend fun deleteEntry(entry: Entry) = withContext(Dispatchers.IO) {
        tagDao.deleteEntry(entry.id)
        entry.mediaFile?.delete()
    }

    fun observeEntriesVersion(): Flow<String> = dao.observeEntriesVersion()

    // Tags

    fun observeTagsBetween(start: LocalDate, end: LocalDate): Flow<List<EntryTag>> = tagDao.observeTagsBetween(start, end)

    suspend fun tagsBetween(start: LocalDate, end: LocalDate): List<EntryTag> = tagDao.tagsBetween(start, end)

    suspend fun allTags(): List<EntryTag> = tagDao.allTags()

    /** Entry id to the fingerprint of the words its tags describe. */
    suspend fun taggedHashes(): Map<Long, String> = tagDao.taggedHashes().associate { it.entryId to it.sourceHash }

    fun observeTaggedHashes(): Flow<List<TaggedHash>> = tagDao.observeTaggedHashes()

    /** The names used so far per kind, most used first. */
    suspend fun vocabulary(): Map<String, List<String>> =
        tagDao.vocabulary().groupBy({ it.kind }, { it.value })

    /** Stores [tags] as everything [entryId] is about, in place of what it had. */
    suspend fun setTags(entryId: Long, tags: TagSet, sourceHash: String) =
        tagDao.replaceTags(entryId, tags.rows(entryId, sourceHash, now()))

    fun observeRecap(key: String): Flow<Recap?> = dao.observeRecap(key)

    fun observeRecaps(): Flow<List<Recap>> = dao.observeRecaps()

    suspend fun recap(key: String): Recap? = dao.recap(key)

    suspend fun allRecaps(): List<Recap> = dao.allRecaps()

    suspend fun saveRecap(recap: Recap) = dao.upsertRecap(recap)

    /**
     * Stores [recap] in place of the one already there, keeping that one in
     * [Recap.previousJson] so the rewrite can be undone. Only one version is kept.
     */
    suspend fun replaceRecap(recap: Recap) {
        val current = dao.recap(recap.key)
        dao.upsertRecap(recap.copy(previousJson = current?.let { RecapRows.encode(it.copy(previousJson = null)) }))
    }

    /** Puts back the version a rewrite replaced. False if there is none to put back. */
    suspend fun undoRecap(key: String): Boolean {
        val current = dao.recap(key) ?: return false
        val previous = current.previousJson?.let(RecapRows::decode)?.takeIf { it.key == key } ?: return false
        dao.upsertRecap(previous.copy(previousJson = null))
        return true
    }

    private fun String?.tidyPlace(): String? =
        this?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_PLACE_LENGTH)?.ifEmpty { null }

    companion object {
        const val MAX_PLACE_LENGTH = 60
    }
}
