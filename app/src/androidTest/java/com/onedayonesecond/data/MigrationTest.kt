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
 * Runs the real migrations on a real database, from the version on phones today (4)
 * with recaps already written in the old layout, and checks Room accepts the result.
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
        helper.runMigrationsAndValidate(FULL, 5, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    private companion object {
        const val DB = "migration-test"
        const val OPENED = "migration-opened"
        const val FULL = "migration-full"
    }
}
