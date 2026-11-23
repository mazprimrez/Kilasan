package com.onedayonesecond.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The migration to version 5 is written by hand, so it is checked against what Room
 * expects (app/schemas/.../5.json): every column it adds must have the type, NOT NULL
 * and default Room generates, or Room refuses to open a database that was migrated.
 * The instrumented MigrationTest runs it on a real database too.
 */
class JournalSchemaTest {

    private data class Column(val affinity: String, val notNull: Boolean, val default: String?)

    private fun columns(version: Int): Map<String, Map<String, Column>> {
        // Unit tests run from the app module's folder.
        val file = File("schemas/com.onedayonesecond.data.AppDatabase/$version.json")
        val entities = JSONObject(file.readText()).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index)
            val fields = entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).associate { i ->
                val field = fields.getJSONObject(i)
                field.getString("columnName") to Column(
                    field.getString("affinity"),
                    field.getBoolean("notNull"),
                    if (field.has("defaultValue")) field.getString("defaultValue") else null,
                )
            }
        }
    }

    private val added = Regex("ALTER TABLE `(\\w+)` ADD COLUMN `(\\w+)` (\\w+)( NOT NULL)?(?: DEFAULT (\\S+))?")

    @Test
    fun `every column the migration adds is the column Room expects in version 5`() {
        val expected = columns(5)
        val statements = JournalSchema.MIGRATION_4_5_SQL.mapNotNull(added::matchEntire)
        assertTrue(statements.isNotEmpty())
        for (match in statements) {
            val (table, column, affinity, notNull, default) = match.destructured
            assertEquals(
                "$table.$column",
                expected.getValue(table).getValue(column),
                Column(affinity, notNull.isNotEmpty(), default.ifEmpty { null }),
            )
        }
    }

    @Test
    fun `the migration adds exactly the columns version 5 has and version 4 did not`() {
        val before = columns(4)
        val after = columns(5)
        val new = after.flatMap { (table, cols) -> (cols.keys - before[table].orEmpty().keys).map { "$table.$it" } }.toSet()
        val migrated = JournalSchema.MIGRATION_4_5_SQL.mapNotNull(added::matchEntire)
            .map { "${it.groupValues[1]}.${it.groupValues[2]}" }.toSet()
        assertEquals(new, migrated)
        assertEquals(before.keys, after.keys)
    }

    @Test
    fun `every other column is left exactly as it was`() {
        val before = columns(4)
        val after = columns(5)
        for ((table, cols) in before) {
            for ((name, column) in cols) assertEquals("$table.$name", column, after.getValue(table)[name])
        }
    }
}
