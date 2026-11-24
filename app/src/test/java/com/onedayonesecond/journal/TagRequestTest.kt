package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryTag
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.data.ProcessingState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TagRequestTest {

    private val date = LocalDate.of(2026, 11, 16)

    private fun entry(id: Long, text: String?, processing: ProcessingState = ProcessingState.DONE) =
        Entry(id = id, date = date, createdAt = id, type = EntryType.WRITTEN, text = text, processing = processing)

    @Test
    fun `the request carries each entry's id, date and words, and the vocabulary by kind`() {
        val request = TagRequest.build(
            listOf(entry(7, "  Abis gym.  "), entry(8, "x".repeat(5000))),
            mapOf(EntryTag.KIND_TOPIC to listOf("Work"), EntryTag.KIND_PERSON to listOf("Mama", "Raka")),
        )

        val first = request.getJSONArray("entries").getJSONObject(0)
        assertEquals(7L, first.getLong("id"))
        assertEquals("2026-11-16", first.getString("date"))
        assertEquals("Abis gym.", first.getString("text"))
        assertEquals(TagRequest.MAX_TEXT, request.getJSONArray("entries").getJSONObject(1).getString("text").length)
        val vocabulary = request.getJSONObject("vocabulary")
        assertEquals("Work", vocabulary.getJSONArray("topics").getString(0))
        assertEquals(0, vocabulary.getJSONArray("habits").length())
        assertEquals(2, vocabulary.getJSONArray("people").length())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no more than twenty entries go in one request`() {
        TagRequest.build((1L..21L).map { entry(it, "a") }, emptyMap())
    }

    @Test
    fun `the answer is read for the entries that were sent only, tidied, at most three topics`() {
        val reply = JSONObject(
            """
            {"tags": [
              {"entryId": 7, "topics": ["work", "Work", " launch ", "Health", "Money"], "habits": ["gym."], "people": ["Raka", ""]},
              {"entryId": 99, "topics": ["Nope"], "habits": [], "people": []},
              {"entryId": 7, "topics": ["Again"], "habits": [], "people": []},
              {"topics": ["No id"]}
            ]}
            """,
        )

        val tags = TagRequest.parse(reply, setOf(7L, 8L))

        assertEquals(setOf(7L), tags.keys) // 8 was left out by the answer: asked again later
        assertEquals(TagSet(listOf("Work", "Launch", "Health"), listOf("Gym"), listOf("Raka")), tags[7])
    }

    @Test
    fun `an entry about nothing in particular is stored as one none row`() {
        val rows = TagSet().rows(5, "hash", 10)
        assertEquals(1, rows.size)
        assertEquals(EntryTag.KIND_NONE, rows.single().kind)
        assertEquals(TagSet(), TagSet.of(rows))

        val some = TagSet(listOf("Work"), listOf("Gym"), listOf("Raka")).rows(5, "hash", 10)
        assertEquals(listOf(EntryTag.KIND_TOPIC, EntryTag.KIND_HABIT, EntryTag.KIND_PERSON), some.map { it.kind })
        assertTrue(some.all { it.sourceHash == "hash" && it.entryId == 5L })
    }

    @Test
    fun `an entry needs tags until it has tags for its words as they are`() {
        val hash = TagHash.of("Abis gym.")!!
        assertTrue(TagHash.needsTags(entry(1, "Abis gym."), null))
        assertFalse(TagHash.needsTags(entry(1, "Abis gym."), hash))
        // Spacing alone does not change the words.
        assertFalse(TagHash.needsTags(entry(1, " Abis   gym. "), hash))
        assertTrue(TagHash.needsTags(entry(1, "Ga jadi gym."), hash))
        // No words, or still being typed out: nothing to tag yet.
        assertFalse(TagHash.needsTags(entry(1, "  "), null))
        assertFalse(TagHash.needsTags(entry(1, "Typed so far", ProcessingState.PENDING), null))
        assertNull(TagHash.of(null))
    }
}
