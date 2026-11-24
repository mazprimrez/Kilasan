package com.onedayonesecond.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** An entry id with the fingerprint of the words it was tagged from. */
data class TaggedHash(val entryId: Long, val sourceHash: String)

/** The tags Patterns counts from, and the entry deletes that must take them along. */
@Dao
abstract class TagDao {

    @Query(
        "SELECT t.* FROM entry_tags t JOIN entries e ON e.id = t.entryId " +
            "WHERE e.date BETWEEN :start AND :end ORDER BY t.entryId, t.id",
    )
    abstract fun observeTagsBetween(start: LocalDate, end: LocalDate): Flow<List<EntryTag>>

    @Query(
        "SELECT t.* FROM entry_tags t JOIN entries e ON e.id = t.entryId " +
            "WHERE e.date BETWEEN :start AND :end ORDER BY t.entryId, t.id",
    )
    abstract suspend fun tagsBetween(start: LocalDate, end: LocalDate): List<EntryTag>

    @Query("SELECT * FROM entry_tags ORDER BY entryId, id")
    abstract suspend fun allTags(): List<EntryTag>

    /** One fingerprint per tagged entry (every row of an entry carries the same one). */
    @Query("SELECT entryId, MAX(sourceHash) AS sourceHash FROM entry_tags GROUP BY entryId")
    abstract suspend fun taggedHashes(): List<TaggedHash>

    @Query("SELECT entryId, MAX(sourceHash) AS sourceHash FROM entry_tags GROUP BY entryId")
    abstract fun observeTaggedHashes(): Flow<List<TaggedHash>>

    /** Every name used so far, most used first, so the model keeps spelling them the same way. */
    @Query(
        "SELECT kind, value FROM entry_tags WHERE kind != 'none' GROUP BY kind, value " +
            "ORDER BY COUNT(*) DESC, value ASC",
    )
    abstract suspend fun vocabulary(): List<KindValue>

    @Insert
    protected abstract suspend fun insertAll(tags: List<EntryTag>)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    abstract suspend fun deleteTagsOf(entryId: Long)

    @Query("DELETE FROM entries WHERE id = :entryId")
    protected abstract suspend fun deleteEntryRow(entryId: Long)

    /** Swaps an entry's tags for [tags] in one go, so Patterns never sees half of each. */
    @Transaction
    open suspend fun replaceTags(entryId: Long, tags: List<EntryTag>) {
        deleteTagsOf(entryId)
        if (tags.isNotEmpty()) insertAll(tags.map { it.copy(id = 0, entryId = entryId) })
    }

    /** Deletes an entry and its tags together. */
    @Transaction
    open suspend fun deleteEntry(entryId: Long) {
        deleteTagsOf(entryId)
        deleteEntryRow(entryId)
    }
}

data class KindValue(val kind: String, val value: String)
