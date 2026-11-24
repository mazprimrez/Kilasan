package com.onedayonesecond.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Runs the real migrations on a real database, from version 4 with recaps already
 * written in the old layout, through version 6 and its tags table, and checks Room
 * accepts the result.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val quoteDate = LocalDate.of(2026, 11, 15)
    private val entryDate = LocalDate.of(2026, 11, 14)

    private fun seedVersion4(name: String) {
        helper.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO recaps (`key`, title, quote, quoteDate, json, createdAt) VALUES " +
                    "('week-2026-11-09', 'A quiet week', 'Tired today.', ${quoteDate.toEpochDay()}, " +
                    "'{\"note\":\"Rest counts.\",\"tries\":[\"Sleep\"]}', 123)",
            )
            execSQL(
                "INSERT INTO recaps (`key`, title, quote, quoteDate, json, createdAt) VALUES " +
                    "('month-2026-10', 'October', '', ${quoteDate.toEpochDay()}, '{}', 456)",
            )
            execSQL(
                "INSERT INTO entries (date, createdAt, type, text, mediaPath, placeName, processing) VALUES " +
                    "(${entryDate.toEpochDay()}, 1793500000000, 'WRITTEN', 'Coffee before work.', NULL, NULL, 'DONE')",
            )
            close()
        }
    }

    @Test
    fun recapsAlreadyWrittenBecomeLayoutOneAndEntriesGetUpdatedAt() {
        seedVersion4(DB)

        // Validates every table against app/schemas/.../5.json.
        val db = helper.runMigrationsAndValidate(DB, 5, true, AppDatabase.MIGRATION_4_5)

        db.query("SELECT `key`, title, schemaVersion, sourceHash, entryCount, previousJson FROM recaps ORDER BY `key` DESC").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("week-2026-11-09", c.getString(0))
            assertEquals("A quiet week", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals("", c.getString(3))
            assertEquals(0, c.getInt(4))
            assertTrue(c.isNull(5))
        }
        db.query("SELECT createdAt, updatedAt FROM entries").use { c ->
            c.moveToFirst()
            assertEquals(c.getLong(0), c.getLong(1))
        }
        db.close()
    }

    @Test
    fun theAppOpensTheMigratedDatabaseAndStillReadsOldRecaps() {
        seedVersion4(OPENED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, OPENED)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val recap = runBlocking { database.journalDao().recap("week-2026-11-09") }!!
            assertEquals(Recap.LAYOUT_V1, recap.schemaVersion)
            assertEquals(quoteDate, recap.quoteDate)
            assertNull(recap.previousJson)
            val entry = runBlocking { database.journalDao().allEntries() }.single()
            assertEquals(entry.createdAt, entry.updatedAt)
        } finally {
            database.close()
            context.deleteDatabase(OPENED)
        }
    }

    @Test
    fun everyMigrationFromTheFirstVersionStillRuns() {
        helper.createDatabase(FULL, 1).apply {
            execSQL("INSERT INTO clips (date, filePath, durationMs, createdAt) VALUES (20000, '/x.mp4', 1000, 1)")
            close()
        }
        helper.runMigrationsAndValidate(FULL, 6, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    @Test
    fun version6AddsAnEmptyTagsTableAndKeepsEveryEntry() {
        seedVersion4(TAGS)
        helper.runMigrationsAndValidate(TAGS, 5, true, AppDatabase.MIGRATION_4_5).close()

        // Validates every table, entry_tags and its two indices included, against 6.json.
        val db = helper.runMigrationsAndValidate(TAGS, 6, true, AppDatabase.MIGRATION_5_6)

        db.query("SELECT text FROM entries").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Coffee before work.", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM entry_tags").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'entry_tags' ORDER BY name").use { c ->
            val names = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            assertEquals(listOf("index_entry_tags_entryId", "index_entry_tags_kind_value"), names)
        }
        db.close()
    }

    @Test
    fun deletingAnEntryDeletesItsTags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            runBlocking {
                val id = database.journalDao().insertEntry(
                    Entry(date = entryDate, createdAt = 1, type = EntryType.WRITTEN, text = "Gym.", processing = ProcessingState.DONE),
                )
                database.tagDao().replaceTags(id, listOf(EntryTag(entryId = id, kind = EntryTag.KIND_HABIT, value = "Gym", taggedAt = 1, sourceHash = "h")))
                assertEquals(1, database.tagDao().allTags().size)
                database.tagDao().deleteEntry(id)
                assertTrue(database.tagDao().allTags().isEmpty())
                assertTrue(database.journalDao().allEntries().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
        const val OPENED = "migration-opened"
        const val FULL = "migration-full"
        const val TAGS = "migration-tags"
    }
}
