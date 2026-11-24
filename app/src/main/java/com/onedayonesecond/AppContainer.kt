package com.onedayonesecond

import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.backup.DriveAuth
import com.onedayonesecond.backup.DriveBackup
import com.onedayonesecond.books.BookActions
import com.onedayonesecond.books.BookLookup
import com.onedayonesecond.books.BookSummaryGenerator
import com.onedayonesecond.books.ThoughtProcessor
import com.onedayonesecond.data.AppDatabase
import com.onedayonesecond.data.BookRepository
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.data.ClipStorage
import com.onedayonesecond.data.DateStyleStore
import com.onedayonesecond.ai.BackendAuth
import com.onedayonesecond.ai.BackendJournalAi
import com.onedayonesecond.ai.JournalAi
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.PendingBookRemovals
import com.onedayonesecond.data.PendingDriveRemovals
import com.onedayonesecond.journal.EntryProcessor
import com.onedayonesecond.journal.EntryTagger
import com.onedayonesecond.journal.JournalActions
import com.onedayonesecond.journal.RecapGenerator
import com.onedayonesecond.location.PlaceFinder
import com.onedayonesecond.media.PixelsExporter
import com.onedayonesecond.ui.theme.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.onedayonesecond.media.ClipThumbnails
import com.onedayonesecond.media.GalleryImporter
import com.onedayonesecond.media.GalleryScanner
import com.onedayonesecond.media.GalleryThumbnails
import com.onedayonesecond.media.MediaStoreExporter
import com.onedayonesecond.media.VideoCompiler
import com.onedayonesecond.media.VideoTrimmer

/**
 * Hand-rolled dependency container. The graph is small enough that Hilt would
 * add more ceremony than it removes.
 */
@UnstableApi
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.build(appContext) }

    /**
     * For work that must outlive the screen that started it: an Undo tapped while
     * leaving, or a voice note being typed out after its sheet has closed.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Short messages with their actions ("Today's second is saved." Undo). */
    val messages = AppMessages()

    val storage by lazy { ClipStorage(appContext) }
    val repository by lazy { ClipRepository(database.clipDao(), storage, database.deletedClipDao()) }
    val journal by lazy { JournalRepository(database.journalDao(), database.tagDao(), storage) }
    val pendingBookRemovals by lazy { PendingBookRemovals(appContext) }
    val books by lazy { BookRepository(database.bookDao(), storage, pendingBookRemovals) }
    val journalPrefs by lazy { JournalPrefs(appContext) }
    val places by lazy { PlaceFinder(appContext) { journalPrefs.tagPlaces.first() } }
    val backendAuth by lazy { BackendAuth(appContext) }
    val ai: JournalAi by lazy { BackendJournalAi(BuildConfig.JOURNAL_BACKEND_URL, backendAuth) }
    val entryProcessor by lazy { EntryProcessor(journal, ai) }
    val journalActions by lazy { JournalActions(appContext, journal, places, entryProcessor, appScope) }
    val entryTagger by lazy { EntryTagger(journal, ai) }
    val recapGenerator by lazy { RecapGenerator(repository, journal, ai, journalPrefs) }
    val bookLookup by lazy { BookLookup(newScratchFile = storage::newScratchFile) }
    val thoughtProcessor by lazy { ThoughtProcessor(books, ai) }
    val bookActions by lazy {
        BookActions(appContext, books, bookLookup, thoughtProcessor, journalActions::importPhoto, appScope)
    }
    val bookSummaries by lazy { BookSummaryGenerator(books, ai) }

    /** Words handed to the write screen to start from: the review draft made from a book's summary. */
    @Volatile
    var writeSeed: String? = null

    /** The seed, once: the write screen that takes it is the only one that starts from it. */
    fun takeWriteSeed(): String? = writeSeed.also { writeSeed = null }
    val pixelsExporter by lazy { PixelsExporter(appContext) }
    val trimmer by lazy { VideoTrimmer(appContext) }
    val compiler by lazy { VideoCompiler(appContext) }
    val exporter by lazy { MediaStoreExporter(appContext) }
    val dateStyleStore by lazy { DateStyleStore(appContext) }
    val importer by lazy { GalleryImporter(appContext, storage) }
    val galleryScanner by lazy { GalleryScanner(appContext) }
    val thumbnails by lazy { GalleryThumbnails(appContext) }
    val clipThumbnails by lazy { ClipThumbnails() }
    val driveAuth by lazy { DriveAuth(appContext) }
    val pendingDriveRemovals by lazy { PendingDriveRemovals(appContext) }
    val driveBackup by lazy { DriveBackup(repository, pendingDriveRemovals, journal, books, pendingBookRemovals) }
}
