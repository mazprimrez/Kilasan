package com.onedayonesecond.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClipEntity::class,
        DayMeta::class,
        Entry::class,
        Recap::class,
        DeletedClip::class,
        Book::class,
        BookThought::class,
        BookSummary::class,
        EntryTag::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    abstract fun journalDao(): JournalDao

    abstract fun deletedClipDao(): DeletedClipDao

    abstract fun bookDao(): BookDao

    abstract fun tagDao(): TagDao

    companion object {
        /**
         * Version 2 adds the caption. Existing clips get an empty one. This must stay
         * an explicit migration: without it Room refuses to open the database, and
         * the only way around that would be to delete the user's clips.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN caption TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Version 3 turns the app into a journal: a mood and place per day, entries,
         * recaps, and a trash for deleted seconds. Only new tables; the clips table is
         * not touched, so every existing second stays exactly as it is. The SQL has to
         * match what Room generates for the entities, column for column (see
         * app/schemas/.../3.json), or Room refuses to open the database.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_meta` (`date` INTEGER NOT NULL, `mood` TEXT, " +
                        "`placeName` TEXT, PRIMARY KEY(`date`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`text` TEXT, `mediaPath` TEXT, `placeName` TEXT, `processing` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_date` ON `entries` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recaps` (`key` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`quote` TEXT NOT NULL, `quoteDate` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `deleted_clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `caption` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * Version 4 adds the reading log: books, the thoughts logged on them, and the
         * summaries written from those thoughts. Only new tables again; nothing that
         * exists is touched. The statements live in [BookSchema] so a unit test can hold
         * them against what Room generates (app/schemas/.../4.json).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                BookSchema.MIGRATION_3_4_SQL.forEach(db::execSQL)
            }
        }

        /**
         * Version 5 is Recap v2: entries get `updatedAt`, recaps get their layout, the
         * fingerprint of what they were written from, an entry count and the version a
         * rewrite replaced. Recaps already written stay, marked as layout 1.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                JournalSchema.MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        /**
         * Version 6 adds the tags Patterns counts from (topics, habits and people per
         * entry). A new table only; entries are tagged in the background afterwards.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                JournalSchema.MIGRATION_5_6_SQL.forEach(db::execSQL)
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "onedayonesecond.db",
            ).addMigrations(*ALL_MIGRATIONS).build()
    }
}
