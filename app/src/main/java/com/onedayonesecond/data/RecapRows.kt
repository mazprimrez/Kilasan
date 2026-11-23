package com.onedayonesecond.data

import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * A whole recap row as JSON: how the version a rewrite replaced is kept for Undo, and
 * how recaps are written to the Drive backup. Reading accepts both layouts: a file
 * without the fields added in database version 5 is a layout-1 recap.
 */
object RecapRows {

    fun toJson(recap: Recap): JSONObject = JSONObject()
        .put("key", recap.key)
        .put("title", recap.title)
        .put("quote", recap.quote)
        .put("quoteDate", recap.quoteDate.toString())
        .put("createdAt", recap.createdAt)
        .put("schemaVersion", recap.schemaVersion)
        .put("sourceHash", recap.sourceHash)
        .put("entryCount", recap.entryCount)
        // The chapters are nested as they are, so the file reads as one document.
        .put("chapters", runCatching { JSONObject(recap.json) }.getOrDefault(JSONObject()))
        .put("previous", recap.previousJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)

    fun encode(recap: Recap): String = toJson(recap).toString()

    fun decode(text: String): Recap? = try {
        fromJson(JSONObject(text))
    } catch (e: JSONException) {
        null
    }

    /** Null for an object missing its key, title or quote date. */
    fun fromJson(json: JSONObject): Recap? {
        val key = json.textOrNull("key") ?: return null
        val title = json.textOrNull("title") ?: return null
        val quoteDate = runCatching { LocalDate.parse(json.optString("quoteDate")) }.getOrNull() ?: return null
        return Recap(
            key = key,
            title = title,
            quote = json.optString("quote"),
            quoteDate = quoteDate,
            json = (json.optJSONObject("chapters") ?: JSONObject()).toString(),
            createdAt = json.optLong("createdAt"),
            schemaVersion = json.optInt("schemaVersion", Recap.LAYOUT_V1),
            sourceHash = json.optString("sourceHash"),
            entryCount = json.optInt("entryCount"),
            previousJson = json.optJSONObject("previous")?.toString(),
        )
    }

    private fun JSONObject.textOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }
}
