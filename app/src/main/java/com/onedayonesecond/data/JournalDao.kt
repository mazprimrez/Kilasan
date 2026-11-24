package com.onedayonesecond.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalDao {

    // Day mood and place

    @Query("SELECT * FROM day_meta WHERE date = :date")
    fun observeMeta(date: LocalDate): Flow<DayMeta?>

    @Query("SELECT * FROM day_meta WHERE date = :date")
    suspend fun meta(date: LocalDate): DayMeta?

    @Query("SELECT * FROM day_meta WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeMetaBetween(start: LocalDate, end: LocalDate): Flow<List<DayMeta>>

    @Query("SELECT * FROM day_meta WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun metaBetween(start: LocalDate, end: LocalDate): List<DayMeta>

    @Query("SELECT * FROM day_meta ORDER BY date ASC")
    suspend fun allMeta(): List<DayMeta>

    @Upsert
    suspend fun upsertMeta(meta: DayMeta)

    // Entries

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt ASC, id ASC")
    fun observeEntries(date: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC, id ASC")
    fun observeEntriesBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC, id ASC")
    suspend fun entriesBetween(start: LocalDate, end: LocalDate): List<Entry>

    @Query("SELECT * FROM entries ORDER BY date ASC, createdAt ASC, id ASC")
    suspend fun allEntries(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): Entry?

    @Query("SELECT * FROM entries WHERE processing IN ('PENDING', 'WAITING_FOR_NETWORK')")
    suspend fun unprocessedEntries(): List<Entry>

    @Query("SELECT MIN(date) FROM entries")
    suspend fun earliestEntryDate(): LocalDate?

    /**
     * Changes whenever an entry is added, deleted or has its words changed: what starts
     * tagging in the background.
     */
    @Query("SELECT COUNT(*) || ':' || IFNULL(MAX(updatedAt), 0) || ':' || IFNULL(SUM(id), 0) FROM entries")
    fun observeEntriesVersion(): Flow<String>

    /** Every day that has at least one entry, for working out which recaps can be written. */
    @Query("SELECT DISTINCT date FROM entries ORDER BY date ASC")
    suspend fun entryDates(): List<LocalDate>

    @Insert
    suspend fun insertEntry(entry: Entry): Long

    // Entries are changed one column at a time, never by writing a whole row back.
    // An entry can be edited by the user, typed out in the background and given its
    // place all at once; whole-row writes would let the last one undo the others.

    @Query("UPDATE entries SET placeName = :place WHERE id = :id")
    suspend fun setEntryPlace(id: Long, place: String?)

    // Every change to an entry's words also moves its updatedAt, which is how a recap
    // knows it was written from an older version of the period.

    /** The user's own words. They settle the matter: nothing is typed out over them. */
    @Query("UPDATE entries SET text = :text, processing = 'DONE', updatedAt = :now WHERE id = :id")
    suspend fun setEntryTextByHand(id: Long, text: String, now: Long)

    @Query("UPDATE entries SET text = :text, updatedAt = :now WHERE id = :id")
    suspend fun clearEntryText(id: Long, text: String, now: Long)

    /** Typed-out words. Ignored if the entry is already done, because then the user typed first. */
    @Query(
        "UPDATE entries SET text = :text, processing = 'DONE', updatedAt = :now WHERE id = :id AND processing != 'DONE'",
    )
    suspend fun finishTypingOut(id: Long, text: String, now: Long): Int

    /** Never moves a finished entry back to waiting or failed. */
    @Query("UPDATE entries SET processing = :state WHERE id = :id AND processing != 'DONE'")
    suspend fun setEntryProcessing(id: Long, state: ProcessingState)

    // Recaps

    @Query("SELECT * FROM recaps WHERE `key` = :key")
    fun observeRecap(key: String): Flow<Recap?>

    @Query("SELECT * FROM recaps WHERE `key` = :key")
    suspend fun recap(key: String): Recap?

    @Query("SELECT * FROM recaps")
    fun observeRecaps(): Flow<List<Recap>>

    @Query("SELECT * FROM recaps")
    suspend fun allRecaps(): List<Recap>

    @Upsert
    suspend fun upsertRecap(recap: Recap)
}

/** The trash: seconds that were deleted or replaced and can still be brought back. */
@Dao
interface DeletedClipDao {

    @Query("SELECT * FROM deleted_clips ORDER BY deletedAt DESC, id DESC")
    fun observeAll(): Flow<List<DeletedClip>>

    @Query("SELECT * FROM deleted_clips")
    suspend fun getAll(): List<DeletedClip>

    @Query("SELECT * FROM deleted_clips WHERE id = :id")
    suspend fun get(id: Long): DeletedClip?

    @Insert
    suspend fun insert(row: DeletedClip): Long

    @Query("DELETE FROM deleted_clips WHERE id = :id")
    suspend fun delete(id: Long)
}
