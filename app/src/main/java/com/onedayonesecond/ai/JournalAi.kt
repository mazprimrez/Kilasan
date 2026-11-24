package com.onedayonesecond.ai

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpRetryException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The four things the journal asks an AI model to do. The model (Gemini Flash-Lite)
 * is never called from the phone: requests go to the app's backend, which is the only
 * thing allowed to reach the model. See docs/JOURNAL_BACKEND.md for the contract.
 *
 * Every call throws [IOException] when the phone cannot reach the backend,
 * [AiException] when the backend answers with an error, and
 * [AiSignInNeededException] when Google must first ask the user who they are.
 */
interface JournalAi {

    /** False when no backend address was set for this build; every call would fail. */
    val isConfigured: Boolean

    /** What was said in a voice note, as plain text. */
    suspend fun transcribe(audio: File): String

    /** The handwriting or print on a notebook photo or pen page, as plain text. */
    suspend fun extractText(image: File): String

    /** Writes a recap from the days in [request] and returns its JSON. */
    suspend fun writeRecap(request: JSONObject): JSONObject

    /** Writes a book's summary and review ideas from the thoughts in [request] and returns its JSON. */
    suspend fun writeBookRecap(request: JSONObject): JSONObject

    /** The topics, habits and people of up to 20 entries (see `TagRequest`). */
    suspend fun tag(request: JSONObject): JSONObject
}

/** The backend answered, but with an error. [retryable] means trying later may work. */
class AiException(val status: Int, message: String) : Exception(message) {
    val retryable: Boolean get() = status == 408 || status == 429 || status >= 500
}

/** This build has no backend address, so nothing can be typed out or written yet. */
class AiNotConfiguredException :
    Exception("The AI service is not set up for this build. Set journal.backend.url in local.properties.")

/**
 * Plain HTTPS calls to the backend, in the same style as the Drive calls: no client
 * library. Each request carries a Google access token from [tokens], which is how the
 * backend knows the request comes from its owner.
 */
class BackendJournalAi(baseUrl: String, private val tokens: AccessTokens) : JournalAi {

    private val base = baseUrl.trim().trimEnd('/')

    override val isConfigured: Boolean get() = base.isNotEmpty()

    override suspend fun transcribe(audio: File): String =
        post("/v1/transcribe", "audio/mp4") { audio.readBytes() }.text()

    override suspend fun extractText(image: File): String {
        val type = if (image.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
        return post("/v1/extract-text", type) { image.readBytes() }.text()
    }

    override suspend fun writeRecap(request: JSONObject): JSONObject =
        post("/v1/recap", "application/json; charset=UTF-8") { request.toString().toByteArray(Charsets.UTF_8) }

    override suspend fun writeBookRecap(request: JSONObject): JSONObject =
        post("/v1/book-recap", "application/json; charset=UTF-8") { request.toString().toByteArray(Charsets.UTF_8) }

    override suspend fun tag(request: JSONObject): JSONObject =
        post("/v1/tag", "application/json; charset=UTF-8") { request.toString().toByteArray(Charsets.UTF_8) }

    private fun JSONObject.text(): String = optString("text").trim()

    /** [readBody] runs off the main thread with the rest of the call: it may read a whole file. */
    private suspend fun post(path: String, contentType: String, readBody: () -> ByteArray): JSONObject =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!isConfigured) throw AiNotConfiguredException()
            val body = readBody()
            val token = tokens.current()
            var reply = send(path, contentType, body, token)
            if (reply.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // The token may simply have run out. Ask Google for a fresh one, once.
                tokens.discard(token)
                reply = send(path, contentType, body, tokens.current())
            }
            if (reply.status !in 200..299) throw AiException(reply.status, errorMessage(reply.status, reply.text))
            try {
                JSONObject(reply.text)
            } catch (e: JSONException) {
                throw AiException(reply.status, "The AI service sent an answer that could not be read.")
            }
        }

    private class Reply(val status: Int, val text: String)

    private fun send(path: String, contentType: String, body: ByteArray, token: String): Reply {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setFixedLengthStreamingMode(body.size)
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            Reply(status, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
        } catch (e: HttpRetryException) {
            // Some platforms report "not signed in" this way for a body that was streamed.
            Reply(e.responseCode(), "")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(status: Int, reply: String): String {
        val detail = runCatching { JSONObject(reply).optString("error") }.getOrNull().orEmpty()
        return detail.ifEmpty { "The AI service answered with error $status." }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000

        // A year's recap is one long model call; it has to be allowed to take a while.
        const val READ_TIMEOUT_MS = 120_000
    }
}
