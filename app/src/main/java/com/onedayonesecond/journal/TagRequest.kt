package com.onedayonesecond.journal

import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryTag
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is sent to the backend's `/tag` and how its answer is read. Pure JSON in and
 * out, so both directions can be unit-tested on a plain JVM. The shape is documented
 * in docs/JOURNAL_BACKEND.md.
 */
object TagRequest {

    /** The backend takes at most this many entries per request. */
    const val MAX_ENTRIES = 20
    const val MAX_TEXT = 4000
    const val MAX_VOCABULARY = 300
    const val MAX_TOPICS = 3
    private const val MAX_NAME = 40

    /**
     * @param entries up to [MAX_ENTRIES] entries with words.
     * @param vocabulary the names used so far, keyed by [EntryTag.KIND_TOPIC],
     * [EntryTag.KIND_HABIT] and [EntryTag.KIND_PERSON], most used first.
     */
    fun build(entries: List<Entry>, vocabulary: Map<String, List<String>>): JSONObject {
        require(entries.size <= MAX_ENTRIES) { "At most $MAX_ENTRIES entries per request" }
        fun names(kind: String) = JSONArray(vocabulary[kind].orEmpty().take(MAX_VOCABULARY))
        return JSONObject()
            .put(
                "entries",
                JSONArray(
                    entries.map { entry ->
                        JSONObject()
                            .put("id", entry.id)
                            .put("date", entry.date.toString())
                            .put("text", entry.text.orEmpty().trim().take(MAX_TEXT))
                    },
                ),
            )
            .put(
                "vocabulary",
                JSONObject()
                    .put("topics", names(EntryTag.KIND_TOPIC))
                    .put("habits", names(EntryTag.KIND_HABIT))
                    .put("people", names(EntryTag.KIND_PERSON)),
            )
    }

    /**
     * The tags per entry id, for the ids in [sent] only. An entry the answer leaves out
     * is not in the map, so it is asked for again later rather than stored as untagged.
     * The backend already tidies the names; they are tidied again here, because the
     * phone stores them.
     */
    fun parse(reply: JSONObject, sent: Set<Long>): Map<Long, TagSet> {
        val items = reply.optJSONArray("tags") ?: return emptyMap()
        val out = LinkedHashMap<Long, TagSet>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (!item.has("entryId")) continue
            val id = item.optLong("entryId", -1)
            if (id !in sent || id in out) continue
            out[id] = TagSet(
                topics = item.names("topics").take(MAX_TOPICS),
                habits = item.names("habits"),
                people = item.names("people"),
            )
        }
        return out
    }

    /** "  train   rides " → "Train rides". Empty for a name with no letters or digits. */
    fun tidy(name: String): String {
        val squashed = name.trim().replace(Regex("\\s+"), " ").trim('.', ',', ';', ':', '!', '?', '"', '\'', '#')
        if (squashed.none { it.isLetterOrDigit() }) return ""
        return (squashed.first().uppercaseChar() + squashed.drop(1)).take(MAX_NAME).trimEnd()
    }

    private fun JSONObject.names(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until array.length())
            .map { tidy(array.optString(it)) }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }
}
