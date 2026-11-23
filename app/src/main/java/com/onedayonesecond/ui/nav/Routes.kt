package com.onedayonesecond.ui.nav

import android.util.Base64
import com.onedayonesecond.journal.RecapPeriod
import java.io.File
import java.time.LocalDate

object Routes {

    const val ARG_DATE = "date"
    const val ARG_PATH = "path"
    const val ARG_ENTRY = "entry"
    const val ARG_KEY = "key"
    const val ARG_YEAR = "year"
    const val ARG_BOOK = "book"

    // The four tabs.
    const val DAYS = "days"
    const val RECAPS = "recaps"
    const val BOOKS = "books"
    const val REEL = "reel"
    val TABS = setOf(DAYS, RECAPS, BOOKS, REEL)

    const val SETTINGS = "settings"
    const val REEL_TEXT = "settings/reel-text"
    const val BACKUP = "settings/backup"
    const val RECAP_BACKFILL = "settings/recaps"
    const val ABOUT = "about"
    const val ADD_BOOK = "books/add"
    const val BOOK_PATTERN = "book/{$ARG_BOOK}"
    const val THOUGHT_PATTERN = "thought/{$ARG_BOOK}"
    const val BOOK_SUMMARY_PATTERN = "book/{$ARG_BOOK}/summary"

    const val DELETED = "deleted"
    const val DAY_PATTERN = "day/{$ARG_DATE}"
    const val CAMERA_PATTERN = "camera/{$ARG_DATE}"
    const val GALLERY_DAY_PATTERN = "gallery/{$ARG_DATE}"
    const val TRIM_PATTERN = "trim/{$ARG_DATE}/{$ARG_PATH}"
    const val WRITE_PATTERN = "write/{$ARG_DATE}?$ARG_ENTRY={$ARG_ENTRY}"
    const val VOICE_PATTERN = "voice/{$ARG_DATE}"
    const val PEN_PATTERN = "pen/{$ARG_DATE}"
    const val RECAP_PATTERN = "recap/{$ARG_KEY}"
    const val PIXELS_PATTERN = "pixels/{$ARG_YEAR}"

    /** No entry to edit: the write screen starts a new one. */
    const val NO_ENTRY = -1L

    fun day(date: LocalDate) = "day/${date.toEpochDay()}"

    fun camera(date: LocalDate) = "camera/${date.toEpochDay()}"

    fun galleryDay(date: LocalDate) = "gallery/${date.toEpochDay()}"

    fun trim(date: LocalDate, file: File) =
        "trim/${date.toEpochDay()}/${encodePath(file.absolutePath)}"

    fun write(date: LocalDate, entryId: Long = NO_ENTRY) = "write/${date.toEpochDay()}?$ARG_ENTRY=$entryId"

    fun voice(date: LocalDate) = "voice/${date.toEpochDay()}"

    fun pen(date: LocalDate) = "pen/${date.toEpochDay()}"

    fun recap(period: RecapPeriod) = "recap/${period.key}"

    fun pixels(year: Int) = "pixels/$year"

    fun book(bookId: Long) = "book/$bookId"

    fun thought(bookId: Long) = "thought/$bookId"

    fun bookSummary(bookId: Long) = "book/$bookId/summary"

    // Base64url rather than Uri.encode: NavController percent-decodes route
    // segments before matching, so an encoded '/' in a file path would split the
    // segment and the route would never match.
    private fun encodePath(path: String): String =
        Base64.encodeToString(path.toByteArray(), BASE64_FLAGS)

    fun decodePath(encoded: String): String =
        String(Base64.decode(encoded, BASE64_FLAGS))

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}
