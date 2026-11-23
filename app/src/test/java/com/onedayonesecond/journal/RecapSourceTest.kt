package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.Mood
import com.onedayonesecond.data.Recap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A recap knows when the entries it was written from have changed. */
class RecapSourceTest {

    private val monday = LocalDate.of(2026, 11, 16)
    private val tuesday = monday.plusDays(1)

    private val entries = listOf(
        Entry(id = 1, date = monday, createdAt = 1000, type = EntryType.WRITTEN, text = "Gym pagi."),
        Entry(id = 2, date = tuesday, createdAt = 2000, type = EntryType.VOICE, text = "Review PR."),
    )
    private val moods = mapOf<LocalDate, Mood?>(monday to Mood.GOOD, tuesday to Mood.GREAT)

    private fun recapOf(entries: List<Entry>, moods: Map<LocalDate, Mood?>) = Recap(
        key = "week-2026-11-16",
        title = "T",
        quote = "",
        quoteDate = monday,
        json = "{}",
        createdAt = 0,
        schemaVersion = Recap.LAYOUT_V2,
        sourceHash = RecapSource.hash(entries, moods),
        entryCount = entries.size,
    )

    @Test
    fun `the same entries give the same fingerprint in any order`() {
        assertEquals(RecapSource.hash(entries, moods), RecapSource.hash(entries.reversed(), moods.toList().reversed().toMap()))
    }

    @Test
    fun `a day without a mood counts the same as a day that is not there`() {
        assertEquals(RecapSource.hash(entries, moods), RecapSource.hash(entries, moods + (monday.plusDays(2) to null)))
    }

    @Test
    fun `a recap written from the entries as they are is not out of date`() {
        assertNull(RecapSource.staleness(recapOf(entries, moods), entries, moods))
    }

    @Test
    fun `an added entry makes it out of date, and says how many`() {
        val more = entries + Entry(id = 3, date = tuesday, createdAt = 3000, type = EntryType.WRITTEN, text = "Lagi.") +
            Entry(id = 4, date = tuesday, createdAt = 4000, type = EntryType.PHOTO)
        val staleness = RecapSource.staleness(recapOf(entries, moods), more, moods)

        assertEquals(RecapSource.Staleness(added = 2), staleness)
        assertEquals("You've added 2 entries since this was written.", RecapSource.describe(staleness!!))
    }

    @Test
    fun `an edited entry makes it out of date, through its updatedAt`() {
        val edited = listOf(entries[0].copy(text = "Gym pagi, lalu kerja.", updatedAt = 5000), entries[1])

        assertNotEquals(RecapSource.hash(entries, moods), RecapSource.hash(edited, moods))
        val staleness = RecapSource.staleness(recapOf(entries, moods), edited, moods)!!
        assertEquals(0, staleness.added)
        assertEquals("Your entries changed since this was written.", RecapSource.describe(staleness))
    }

    @Test
    fun `a changed mood or a deleted entry makes it out of date`() {
        val recap = recapOf(entries, moods)
        assertEquals(RecapSource.Staleness(0), RecapSource.staleness(recap, entries, moods + (monday to Mood.BAD)))
        assertEquals(RecapSource.Staleness(0), RecapSource.staleness(recap, entries.take(1), moods))
    }

    @Test
    fun `a recap in the old layout has no fingerprint and is never called out of date`() {
        val old = recapOf(entries, moods).copy(schemaVersion = Recap.LAYOUT_V1, sourceHash = "")
        assertNull(RecapSource.staleness(old, entries + entries, moods))
    }

    @Test
    fun `an entry never edited last changed when it was made`() {
        assertEquals(1000, entries[0].updatedAt)
    }
}
