package com.onedayonesecond.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File
import java.time.LocalDate

/** How a day felt, from hardest to best. [score] is what averages are worked out from. */
enum class Mood(val score: Int, val label: String) {
    ROUGH(1, "Rough"),
    BAD(2, "Bad"),
    NEUTRAL(3, "Neutral"),
    GOOD(4, "Good"),
    GREAT(5, "Great"),
}

/** What belongs to a day as a whole rather than to its clip or to one entry. */
@Entity(tableName = "day_meta")
data class DayMeta(
    @PrimaryKey val date: LocalDate,
    val mood: Mood? = null,
    /** A short place name such as "Kemang, Jakarta Selatan". Null hides the place line. */
    val placeName: String? = null,
)

enum class EntryType { WRITTEN, VOICE, NOTEBOOK_PHOTO, PEN, PHOTO }

/**
 * Where an entry is in being turned into text. Only voice notes, notebook photos and
 * pen pages are ever anything but [DONE]: their words are typed out for the user.
 */
enum class ProcessingState {
    DONE,

    /** Waiting its turn or being worked on right now. */
    PENDING,

    /** The phone was offline. It is tried again by itself once it is back online. */
    WAITING_FOR_NETWORK,

    /** Tried and could not be done. The media itself is safe; the user can try again. */
    FAILED,
}

/** One thing added to a day: some writing, a voice note, a photo, a pen page. */
@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val createdAt: Long,
    val type: EntryType,
    /** Written text, a voice note's transcript, or text typed out from a page. */
    val text: String? = null,
    /** The audio, photo or pen image file, for every type but [EntryType.WRITTEN]. */
    val mediaPath: String? = null,
    val placeName: String? = null,
    val processing: ProcessingState = ProcessingState.DONE,
    /**
     * When the entry's words last changed: written, edited by hand or typed out.
     * Recaps compare it to tell whether they were written from the entries as they are.
     */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
) {
    val mediaFile: File? get() = mediaPath?.let(::File)

    /** Types whose words are typed out by the AI service after they are saved. */
    val needsText: Boolean
        get() = type == EntryType.VOICE || type == EntryType.NOTEBOOK_PHOTO || type == EntryType.PEN
}

/**
 * A written recap of a week, month or year. [key] names the period, for example
 * "week-2026-11-09" (the Monday), "month-2026-10" or "year-2026".
 */
@Entity(tableName = "recaps")
data class Recap(
    @PrimaryKey val key: String,
    val title: String,
    /** One sentence taken word for word from the user's own entries. */
    val quote: String,
    val quoteDate: LocalDate,
    /**
     * The chapters. Layout 1: what happened, the note, changes, things to try, places
     * (`RecapContent`). Layout 2: see `RecapLetter`.
     */
    val json: String,
    val createdAt: Long,
    /** Which layout [json] is in: 1 for recaps written before version 5 of the database, 2 now. */
    @ColumnInfo(defaultValue = "1") val schemaVersion: Int = 1,
    /**
     * A fingerprint of the period's entries and moods when this was written (see
     * `RecapSource`). When today's differs, the recap is out of date. Empty for layout 1.
     */
    @ColumnInfo(defaultValue = "") val sourceHash: String = "",
    /** How many entries the period had when this was written, for "You've added 2 entries since". */
    @ColumnInfo(defaultValue = "0") val entryCount: Int = 0,
    /** The version this one replaced, as a whole row in JSON, so a rewrite can be undone. One deep. */
    val previousJson: String? = null,
) {
    companion object {
        const val LAYOUT_V1 = 1
        const val LAYOUT_V2 = 2
    }
}

/**
 * One thing an entry is about, as the AI service tagged it: a topic ("Work"), a habit
 * ("Gym") or a person ("Mama"). Patterns counts these on the phone; the model only
 * names them. [sourceHash] is `TagHash.of` the entry's words when it was tagged, so an
 * entry whose words changed since is tagged again, and its old tags are not counted.
 *
 * An entry that was tagged and is about nothing in particular keeps one row of kind
 * [KIND_NONE], so it is not sent again until its words change.
 */
@Entity(
    tableName = "entry_tags",
    indices = [Index("entryId"), Index("kind", "value")],
)
data class EntryTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** [KIND_TOPIC], [KIND_HABIT], [KIND_PERSON] or [KIND_NONE]. */
    val kind: String,
    /** Normalized, e.g. "Work", "Gym", "Mama". Empty for [KIND_NONE]. */
    val value: String,
    val taggedAt: Long,
    val sourceHash: String,
) {
    companion object {
        const val KIND_TOPIC = "topic"
        const val KIND_HABIT = "habit"
        const val KIND_PERSON = "person"
        const val KIND_NONE = "none"
    }
}

/**
 * A second that was deleted or replaced. Its video sits in the trash folder until it
 * is restored or thirty days have passed, so deleting is never the point of no return.
 */
@Entity(tableName = "deleted_clips")
data class DeletedClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val caption: String,
    val deletedAt: Long,
) {
    val file: File get() = File(filePath)

    companion object {
        const val KEEP_DAYS = 30L
        const val KEEP_MS = KEEP_DAYS * 24 * 60 * 60 * 1000
    }
}

/**
 * What version 5 changes, statement for statement: entries learn when their words last
 * changed, and recaps learn their layout, what they were written from, and the version
 * they replaced. Columns are only added, so nothing that exists is lost. Each column's
 * type, NOT NULL and default must match what Room generates for the entities above
 * (app/schemas/.../5.json), or Room refuses to open the database; a unit test compares them.
 */
object JournalSchema {
    val MIGRATION_4_5_SQL = listOf(
        "ALTER TABLE `entries` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        // An entry that was never edited last changed when it was made.
        "UPDATE `entries` SET `updatedAt` = `createdAt`",
        "ALTER TABLE `recaps` ADD COLUMN `schemaVersion` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `recaps` ADD COLUMN `sourceHash` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `recaps` ADD COLUMN `entryCount` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `recaps` ADD COLUMN `previousJson` TEXT",
    )

    /**
     * Version 6 adds the tags Patterns counts from. Only a new table: entries that exist
     * have no tags yet and are tagged in the background. The statements must match
     * app/schemas/.../6.json; a unit test compares them.
     */
    val MIGRATION_5_6_SQL = listOf(
        "CREATE TABLE IF NOT EXISTS `entry_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`entryId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, " +
            "`taggedAt` INTEGER NOT NULL, `sourceHash` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_entry_tags_entryId` ON `entry_tags` (`entryId`)",
        "CREATE INDEX IF NOT EXISTS `index_entry_tags_kind_value` ON `entry_tags` (`kind`, `value`)",
    )
}
