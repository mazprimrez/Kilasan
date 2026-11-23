package com.onedayonesecond.backup

import com.onedayonesecond.data.Book
import com.onedayonesecond.data.BookRepository
import com.onedayonesecond.data.BookThought
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.data.DayMeta
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.ProcessingState
import com.onedayonesecond.data.Recap
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.data.JournalRepository
import com.onedayonesecond.data.PendingBookRemovals
import com.onedayonesecond.data.PendingDriveRemovals
import com.onedayonesecond.data.ThoughtType
import com.onedayonesecond.media.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * Backs every clip up to the user's Drive as an ordinary `.mp4`, one folder per
 * month (see [DriveLayout]), with each day's journal (mood, place, entries and their
 * voice notes, photos and pen pages) beside it, the recaps alongside and the books
 * (each with its cover and its thoughts) in a folder of their own, and restores them.
 *
 * There is no local record of what was uploaded. Each run asks Drive what is
 * already there and compares checksums, so a run can be repeated safely and an
 * interrupted one simply continues on the next tap.
 */
class DriveBackup(
    private val repository: ClipRepository,
    private val pendingRemovals: PendingDriveRemovals,
    private val journal: JournalRepository,
    private val books: BookRepository,
    private val pendingBookRemovals: PendingBookRemovals,
) {

    /**
     * [movedToMonthFolders] counts older flat-layout copies that were tidied up;
     * [removedFromDrive] counts days whose clip was moved to another day on the phone
     * and whose now-outdated copy was removed from Drive; [captionsUpdated] is true if
     * the captions file in Drive was written because a caption had changed.
     * [journalDays] counts days whose journal file or media went up; [recaps] counts
     * recaps that went up; [tooLarge] counts journal and book files left out because
     * they are over Drive's single-request size. [books] counts books whose file, cover
     * or thought media went up; [booksRemoved] counts books removed on the phone whose
     * files were taken out of Drive.
     */
    data class Summary(
        val uploaded: Int,
        val replaced: Int,
        val alreadyBackedUp: Int,
        val movedToMonthFolders: Int = 0,
        val removedFromDrive: Int = 0,
        val captionsUpdated: Boolean = false,
        val journalDays: Int = 0,
        val recaps: Int = 0,
        val tooLarge: Int = 0,
        val books: Int = 0,
        val booksRemoved: Int = 0,
    ) {
        val total: Int get() = uploaded + replaced + alreadyBackedUp

        /** True if the phone had nothing at all to back up. */
        val nothingToDo: Boolean
            get() = total == 0 && removedFromDrive == 0 && journalDays == 0 && recaps == 0 && !hadJournal &&
                books == 0 && booksRemoved == 0 && !hadBooks

        /** Set when there was a journal to look at, even if all of it was already in Drive. */
        var hadJournal: Boolean = false
            internal set

        /** Set when there were books to look at, even if all of them were already in Drive. */
        var hadBooks: Boolean = false
            internal set
    }

    /**
     * [onlyEncrypted] counts days present only as old encrypted copies, which cannot be
     * opened. [journalDays], [recaps] and [books] count journal days, recaps and books
     * brought back.
     */
    data class RestoreSummary(
        val restored: Int,
        val alreadyOnPhone: Int,
        val unreadable: Int,
        val onlyEncrypted: Int,
        val journalDays: Int = 0,
        val recaps: Int = 0,
        val books: Int = 0,
    )

    /** Raised when there is nothing in Drive to restore from. */
    class NoBackupException(message: String) : Exception(message)

    suspend fun clipCount(): Int = localClips().size

    /** True if there is a second, a journal day, a recap or a book on this phone to back up. */
    suspend fun hasAnything(): Boolean =
        localClips().isNotEmpty() || localJournal().isNotEmpty() || journal.allRecaps().isNotEmpty() ||
            books.allBooks().isNotEmpty()

    suspend fun backup(
        accessToken: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Summary = withContext(Dispatchers.IO) {
        val clips = localClips()
        val movedAway = pendingRemovals.all()
        val journalDays = localJournal()
        val recaps = journal.allRecaps()
        val shelf = localBooks()
        val removedBooks = pendingBookRemovals.all()
        val nothingLocal = clips.isEmpty() && journalDays.isEmpty() && recaps.isEmpty() && shelf.isEmpty()
        if (nothingLocal && movedAway.isEmpty() && removedBooks.isEmpty()) return@withContext Summary(0, 0, 0)

        val api = DriveApi(accessToken)
        val rootId = findRoot(api) ?: if (nothingLocal) {
            // Nothing was ever uploaded, so there is nothing to clean up either.
            pendingRemovals.remove(movedAway)
            pendingBookRemovals.remove(removedBooks)
            return@withContext Summary(0, 0, 0)
        } else {
            api.createFolder(DriveLayout.ROOT_FOLDER)
        }
        val rootChildren = api.listChildren(rootId)

        // Month folders that already exist, and clips left loose in the top folder by
        // the first version of the backup, which had no month folders.
        val monthFolderIds: MutableMap<YearMonth, String> = rootChildren
            .filter { it.isFolder }
            .mapNotNull { folder -> DriveLayout.parseMonthFolder(folder.name)?.let { it to folder.id } }
            .toMap()
            .toMutableMap()
        val looseByName = rootChildren.filter { !it.isFolder }.groupBy { it.name }

        // Each month folder is listed once, the first time a clip of that month is reached.
        val listings = mutableMapOf<YearMonth, List<DriveApi.RemoteFile>>()

        var uploaded = 0
        var replaced = 0
        var skipped = 0
        var moved = 0
        // Journal days and books are counted along with the clips, so the progress bar covers the whole run.
        val steps = clips.size + journalDays.size + shelf.size
        onProgress(0, steps)

        clips.forEachIndexed { index, clip ->
            // Blocking network calls do not respond to cancellation, so check
            // between files — the longest uninterruptible stretch is one clip.
            ensureActive()

            val month = YearMonth.from(clip.date)
            val folderId = monthFolderIds.getOrPut(month) {
                api.createFolder(DriveLayout.monthFolderName(clip.date), rootId)
            }
            val inFolder = listings.getOrPut(month) { api.listChildren(folderId) }

            val name = DriveLayout.clipFileName(clip.date)
            val existing = inFolder.filter { it.name == name }
            val bytes = clip.file.readBytes()
            val localMd5 = md5Hex(bytes)

            if (existing.any { it.md5 == localMd5 }) {
                skipped++
            } else {
                api.upload(folderId, name, bytes, MP4_MIME)
                // Only after the new copy is safely up: drop the outdated one.
                existing.forEach { api.delete(it.id) }
                if (existing.isEmpty()) uploaded++ else replaced++
            }

            // The day now has its copy in the right month folder, so the same day's
            // loose copy in the top folder is redundant.
            looseByName[name].orEmpty().forEach {
                api.delete(it.id)
                moved++
            }

            onProgress(index + 1, steps)
        }

        // Clips that were moved to another day on the phone: their old day's copy is
        // now a duplicate that a restore would bring back on the wrong date. This runs
        // after the uploads so the clip is safely in Drive under its new day first.
        val localDays = clips.mapTo(HashSet()) { it.date }
        var removed = 0
        for (day in movedAway.sorted()) {
            ensureActive()
            // A clip is on that day again (retaken, or moved back), and the upload
            // above has already made Drive match it.
            if (day in localDays) continue

            val name = DriveLayout.clipFileName(day)
            val month = YearMonth.from(day)
            val inMonthFolder = monthFolderIds[month]
                ?.let { id -> listings.getOrPut(month) { api.listChildren(id) } }
                .orEmpty()
            val outdated = inMonthFolder.filter { it.name == name && !it.isFolder } +
                looseByName[name].orEmpty()
            outdated.forEach { api.delete(it.id) }
            if (outdated.isNotEmpty()) removed++
        }
        // Captions are in the app's database, not in the videos, so they go up as one
        // small file beside the month folders.
        val captionsUpdated = syncCaptions(
            api = api,
            rootId = rootId,
            existing = looseByName[DriveLayout.CAPTIONS_FILE].orEmpty(),
            clips = clips,
            movedAway = movedAway,
        )

        // The journal: each day's file and media beside its clip, then the recaps.
        var journalUploaded = 0
        var tooLarge = 0
        journalDays.forEachIndexed { index, day ->
            ensureActive()
            val month = YearMonth.from(day.date)
            val folderId = monthFolderIds.getOrPut(month) {
                api.createFolder(DriveLayout.monthFolderName(day.date), rootId)
            }
            val inFolder = listings.getOrPut(month) { api.listChildren(folderId) }
            val result = syncDay(api, folderId, inFolder, day)
            if (result.changed) journalUploaded++
            tooLarge += result.tooLarge
            onProgress(clips.size + index + 1, steps)
        }

        var recapsUploaded = 0
        for (recap in recaps.sortedBy { it.key }) {
            ensureActive()
            val month = DriveLayout.recapFolder(recap.key)
            val (folderId, inFolder) = if (month == null) {
                rootId to rootChildren
            } else {
                val id = monthFolderIds.getOrPut(month) { api.createFolder(month.toString(), rootId) }
                id to listings.getOrPut(month) { api.listChildren(id) }
            }
            if (putIfChanged(api, folderId, inFolder, DriveLayout.recapFileName(recap.key), JournalFile.encodeRecap(recap))) {
                recapsUploaded++
            }
        }

        // The books: each one's cover and thought media, then the file that refers to them.
        var booksUploaded = 0
        var booksRemoved = 0
        var booksFolderId = rootChildren.firstOrNull { it.isFolder && it.name == DriveLayout.BOOKS_FOLDER }?.id
        if (booksFolderId == null && shelf.isNotEmpty()) {
            booksFolderId = api.createFolder(DriveLayout.BOOKS_FOLDER, rootId)
        }
        if (booksFolderId != null) {
            val inBooks = api.listChildren(booksFolderId).filter { !it.isFolder }
            shelf.forEachIndexed { index, book ->
                ensureActive()
                val result = syncBook(api, booksFolderId, inBooks, book)
                if (result.changed) booksUploaded++
                tooLarge += result.tooLarge
                onProgress(clips.size + journalDays.size + index + 1, steps)
            }

            // Books removed on the phone: a restore would otherwise bring them back.
            val onShelf = shelf.mapTo(HashSet()) { it.book.backupKey }
            for (key in removedBooks.sorted()) {
                ensureActive()
                // Still here after all (the app stopped half-way through removing it): its files stay.
                if (key in onShelf) continue
                val gone = inBooks.filter { DriveLayout.isBookFile(it.name, key) }
                gone.forEach { api.delete(it.id) }
                if (gone.isNotEmpty()) booksRemoved++
            }
        }

        // Cleared only now: if anything above failed, the next backup tries again.
        pendingRemovals.remove(movedAway)
        pendingBookRemovals.remove(removedBooks)

        Summary(
            uploaded, replaced, skipped, moved, removed, captionsUpdated, journalUploaded, recapsUploaded, tooLarge,
            booksUploaded, booksRemoved,
        ).also {
            it.hadJournal = journalDays.isNotEmpty() || recaps.isNotEmpty()
            it.hadBooks = shelf.isNotEmpty()
        }
    }

    /**
     * Makes one book in Drive match the phone: its cover and its thoughts' media first,
     * then the book's file that refers to them, so the file never points at something
     * that is not there yet.
     */
    private fun syncBook(
        api: DriveApi,
        folderId: String,
        inFolder: List<DriveApi.RemoteFile>,
        local: LocalBook,
    ): DayResult {
        var changed = false
        var tooLarge = 0
        val key = local.book.backupKey

        var coverName: String? = null
        local.book.coverFile?.takeIf { it.exists() }?.let { cover ->
            if (cover.length() > DriveApi.MAX_SIMPLE_UPLOAD_BYTES) {
                tooLarge++
            } else {
                // The phone keeps each new cover under a new name; Drive keeps one, under the book's key.
                val name = DriveLayout.bookCoverName(key)
                if (putIfChanged(api, folderId, inFolder, name, cover.readBytes())) changed = true
                coverName = name
            }
        }

        val backedUp = mutableListOf<ThoughtBackup>()
        for (thought in local.thoughts) {
            val words = thought.text?.ifEmpty { null }
            val file = thought.mediaFile?.takeIf { it.exists() }
            var media: String? = null
            if (file != null) {
                if (file.length() > DriveApi.MAX_SIMPLE_UPLOAD_BYTES) {
                    // Left out rather than failing the whole backup on one big file.
                    tooLarge++
                } else {
                    val name = DriveLayout.thoughtMediaName(key, thought.createdAt, file.extension)
                    if (putIfChanged(api, folderId, inFolder, name, file.readBytes())) changed = true
                    media = name
                }
            }
            // A thought with neither words nor media to show for it has nothing to keep.
            // One whose media is gone or too large still keeps its words.
            if (words == null && media == null) continue
            backedUp += ThoughtBackup(thought.page, thought.createdAt, thought.type, words, media, thought.showInDiary)
        }

        val book = local.book
        val backup = BookBackup(
            key = key,
            title = book.title,
            author = book.author,
            isbn = book.isbn,
            edition = book.edition,
            totalPages = book.totalPages,
            currentPage = book.currentPage,
            status = book.status,
            startedOn = book.startedOn,
            finishedOn = book.finishedOn,
            cover = coverName,
            thoughts = backedUp,
        )
        val name = DriveLayout.bookFileName(key)
        val existing = inFolder.filter { it.name == name }
        val localBytes = BookFile.encode(backup)
        if (existing.any { it.md5 == md5Hex(localBytes) }) return DayResult(changed, tooLarge)

        // Drive's copy differs. It may hold thoughts this phone does not have, so the
        // two are merged rather than overwritten. A failed download fails the backup
        // instead of being read as "nothing there".
        val remote = existing.firstOrNull()?.let { BookFile.decode(api.download(it.id)) }
        val merged = BookFile.encode(BookSync.merge(remote, backup))
        if (existing.any { it.md5 == md5Hex(merged) }) return DayResult(changed, tooLarge)

        api.upload(folderId, name, merged, DriveLayout.mimeOf(name))
        existing.forEach { api.delete(it.id) }
        return DayResult(true, tooLarge)
    }

    private class DayResult(val changed: Boolean, val tooLarge: Int)

    /**
     * Makes one day's journal in Drive match the phone: its media files first, then
     * the day's file that refers to them, so the file never points at media that is
     * not there yet.
     */
    /** The backup's folder, renamed first if it still has the app's former name. */
    private fun findRoot(api: DriveApi): String? =
        DriveLayout.findRoot(find = { api.findFolder(it) }, rename = api::rename)

    private fun syncDay(
        api: DriveApi,
        folderId: String,
        inFolder: List<DriveApi.RemoteFile>,
        day: LocalDay,
    ): DayResult {
        var changed = false
        var tooLarge = 0
        val backedUp = mutableListOf<EntryBackup>()

        for (entry in day.entries) {
            val file = entry.mediaFile?.takeIf { it.exists() }
            if (entry.mediaPath != null && file == null) continue // its media is gone; nothing to keep
            if (file != null) {
                if (file.length() > DriveApi.MAX_SIMPLE_UPLOAD_BYTES) {
                    // Left out rather than failing the whole backup on one big file.
                    tooLarge++
                    continue
                }
                if (putIfChanged(api, folderId, inFolder, file.name, file.readBytes())) changed = true
            }
            backedUp += EntryBackup(
                createdAt = entry.createdAt,
                type = entry.type,
                text = entry.text?.ifEmpty { null },
                media = file?.name,
                place = entry.placeName,
                updatedAt = entry.updatedAt,
            )
        }

        val local = DayBackup(day.date, day.meta?.mood, day.meta?.placeName, backedUp)
        val name = DriveLayout.dayFileName(day.date)
        val existing = inFolder.filter { it.name == name && !it.isFolder }
        val localBytes = JournalFile.encodeDay(local)
        if (existing.any { it.md5 == md5Hex(localBytes) }) return DayResult(changed, tooLarge)

        // Drive's copy differs. It may hold entries this phone does not have (a new
        // phone, before restoring), so the two are merged rather than overwritten. A
        // failed download fails the backup instead of being read as "nothing there".
        val remote = existing.firstOrNull()?.let { JournalFile.decodeDay(api.download(it.id)) }
        val merged = JournalFile.encodeDay(JournalSync.mergeDay(remote, local))
        if (existing.any { it.md5 == md5Hex(merged) }) return DayResult(changed, tooLarge)

        api.upload(folderId, name, merged, DriveLayout.mimeOf(name))
        existing.forEach { api.delete(it.id) }
        return DayResult(true, tooLarge)
    }

    /**
     * Uploads [bytes] as [name] unless a file with that name and the same content is
     * already in the folder. An outdated copy is removed only after the new one is up.
     *
     * @return true if it was uploaded.
     */
    private fun putIfChanged(
        api: DriveApi,
        folderId: String,
        inFolder: List<DriveApi.RemoteFile>,
        name: String,
        bytes: ByteArray,
    ): Boolean {
        val existing = inFolder.filter { it.name == name && !it.isFolder }
        if (existing.any { it.md5 == md5Hex(bytes) }) return false
        api.upload(folderId, name, bytes, DriveLayout.mimeOf(name))
        existing.forEach { api.delete(it.id) }
        return true
    }

    /** True if the captions file was written, false if Drive already had the right one. */
    private fun syncCaptions(
        api: DriveApi,
        rootId: String,
        existing: List<DriveApi.RemoteFile>,
        clips: List<ClipEntity>,
        movedAway: Set<LocalDate>,
    ): Boolean {
        // A failed download is not treated as "no captions yet": that would write a file
        // holding only this phone's captions over the real one. It fails the backup
        // instead, and the next run tries again.
        val remote = existing.firstOrNull()?.let { CaptionsFile.decode(api.download(it.id)) }.orEmpty()
        val merged = CaptionSync.merge(remote, clips.associate { it.date to it.caption }, movedAway)

        if (merged.isEmpty() && existing.isEmpty()) return false // there never were any
        val bytes = CaptionsFile.encode(merged)
        if (existing.any { it.md5 == md5Hex(bytes) }) return false

        api.upload(rootId, DriveLayout.CAPTIONS_FILE, bytes, CAPTIONS_MIME)
        // Only after the new one is safely up, as with clips.
        existing.forEach { api.delete(it.id) }
        return true
    }

    /**
     * Downloads every day that is in Drive but not on this phone. Days already on
     * the phone are left exactly as they are.
     */
    suspend fun restore(
        accessToken: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val api = DriveApi(accessToken)
        val rootId = findRoot(api)
            ?: throw NoBackupException("No backup found in this Google account.")
        val rootChildren = api.listChildren(rootId)

        // One file per day. Loose files from the first backup layout go in first so
        // that a copy in a month folder replaces them.
        val sources = mutableMapOf<LocalDate, DriveApi.RemoteFile>()
        val encryptedDays = mutableSetOf<LocalDate>()
        // Journal files found on the way: each day's file with the folder listing its
        // media is in, and the recaps.
        val dayFiles = mutableMapOf<LocalDate, Pair<DriveApi.RemoteFile, List<DriveApi.RemoteFile>>>()
        val recapFiles = mutableMapOf<String, DriveApi.RemoteFile>()

        rootChildren.filter { !it.isFolder }.forEach { file ->
            DriveLayout.dateOfClip(file.name)?.let { sources[it] = file }
            DriveLayout.dateOfEncryptedClip(file.name)?.let { encryptedDays += it }
            DriveLayout.recapKeyOf(file.name)?.let { recapFiles[it] = file }
        }
        rootChildren
            .filter { it.isFolder && DriveLayout.parseMonthFolder(it.name) != null }
            .forEach { folder ->
                ensureActive()
                val files = api.listChildren(folder.id).filter { !it.isFolder }
                files.forEach { file ->
                    DriveLayout.dateOfClip(file.name)?.let { sources[it] = file }
                    DriveLayout.dateOfEncryptedClip(file.name)?.let { encryptedDays += it }
                    DriveLayout.dateOfDayFile(file.name)?.let { dayFiles[it] = file to files }
                    DriveLayout.recapKeyOf(file.name)?.let { recapFiles[it] = file }
                }
            }

        // The books folder: each book's file, with the listing its cover and media are in.
        val inBooks = rootChildren
            .firstOrNull { it.isFolder && it.name == DriveLayout.BOOKS_FOLDER }
            ?.let { folder -> api.listChildren(folder.id).filter { !it.isFolder } }
            .orEmpty()
        val bookFiles = inBooks.mapNotNull { file -> DriveLayout.bookKeyOf(file.name)?.let { it to file } }.toMap()

        val onlyEncrypted = encryptedDays - sources.keys
        if (sources.isEmpty() && dayFiles.isEmpty() && recapFiles.isEmpty() && bookFiles.isEmpty()) {
            throw NoBackupException(
                if (onlyEncrypted.isEmpty()) {
                    "The backup folder in Drive has no clips in it."
                } else {
                    "The clips in Drive are in an older encrypted format that this version " +
                        "of the app can no longer open."
                },
            )
        }

        // Captions travel in their own file; days restored below get theirs from it.
        val captions = rootChildren
            .firstOrNull { !it.isFolder && it.name == DriveLayout.CAPTIONS_FILE }
            ?.let { CaptionsFile.decode(api.download(it.id)) }
            .orEmpty()

        val onPhone = localClips().map { it.date }.toSet()
        val days = sources.keys.sorted()
        // A journal day counts as missing from the phone only if the phone has nothing
        // at all for it: no mood, no place, no entry. Anything already here is left alone.
        val journalOnPhone = localJournal().mapTo(HashSet()) { it.date }
        val journalDays = dayFiles.keys.filter { it !in journalOnPhone }.sorted()
        // A book comes back only if this phone does not have it, and did not remove it:
        // one removed here since the last backup is still in Drive, and must stay gone.
        val removedHere = pendingBookRemovals.all()
        val missingBooks = bookFiles.keys.filter { it !in removedHere && books.bookByKey(it) == null }.sorted()
        val steps = days.size + journalDays.size + missingBooks.size
        var restored = 0
        var already = 0
        var unreadable = 0
        onProgress(0, steps)

        days.forEachIndexed { index, date ->
            ensureActive()
            if (date in onPhone) {
                already++
            } else if (save(date, api.download(sources.getValue(date).id), captions[date].orEmpty())) {
                restored++
            } else {
                unreadable++
            }
            onProgress(index + 1, steps)
        }

        var journalRestored = 0
        journalDays.forEachIndexed { index, date ->
            ensureActive()
            val (file, folder) = dayFiles.getValue(date)
            // A damaged day file is skipped rather than stopping the days after it.
            val day = JournalFile.decodeDay(api.download(file.id))
            if (day != null && !day.isEmpty) {
                restoreDay(api, day, folder)
                journalRestored++
            }
            onProgress(days.size + index + 1, steps)
        }

        var recapsRestored = 0
        for ((key, file) in recapFiles) {
            ensureActive()
            if (journal.recap(key) != null) continue
            val recap = JournalFile.decodeRecap(api.download(file.id)) ?: continue
            // The name is what says which recap this is; a file whose contents disagree is not trusted.
            if (recap.key != key) continue
            journal.saveRecap(recap)
            recapsRestored++
        }

        var booksRestored = 0
        missingBooks.forEachIndexed { index, key ->
            ensureActive()
            // A damaged book file is skipped rather than stopping the books after it. The
            // name is what says which book this is; a file whose contents disagree is not trusted.
            val book = BookFile.decode(api.download(bookFiles.getValue(key).id))
            if (book != null && book.key == key) {
                restoreBook(api, book, inBooks)
                booksRestored++
            }
            onProgress(days.size + journalDays.size + index + 1, steps)
        }

        RestoreSummary(restored, already, unreadable, onlyEncrypted.size, journalRestored, recapsRestored, booksRestored)
    }

    /** Brings one book back with its cover and its thoughts, downloading each thought's media. */
    private suspend fun restoreBook(api: DriveApi, backup: BookBackup, folder: List<DriveApi.RemoteFile>) {
        val storage = books.storage
        // Everything is downloaded before anything is recorded. A book counts as "on the
        // phone" as soon as it is recorded, so a connection that dropped half-way through
        // would leave the rest of its thoughts unrestorable.
        var coverScratch: File? = null
        val downloaded = mutableListOf<Pair<ThoughtBackup, File?>>()
        try {
            folder.firstOrNull { it.name == backup.cover }?.let { remote ->
                coverScratch = storage.newScratchFile("jpg").also { it.writeBytes(api.download(remote.id)) }
            }
            for (thought in backup.thoughts) {
                val remote = thought.media?.let { name -> folder.firstOrNull { it.name == name } }
                if (remote == null) {
                    // Its media is not in Drive: its words still come back, if it has any.
                    if (thought.text != null) downloaded += thought to null
                    continue
                }
                val scratch = storage.newScratchFile(File(remote.name).extension.ifEmpty { "bin" })
                scratch.writeBytes(api.download(remote.id))
                downloaded += thought to scratch
            }
        } catch (e: Exception) {
            coverScratch?.delete()
            downloaded.forEach { it.second?.delete() }
            throw e
        }

        val coverPath = coverScratch?.let { scratch ->
            storage.newCoverFile(backup.key).also { moveInto(scratch, it) }.absolutePath
        }
        val book = books.putBook(
            Book(
                backupKey = backup.key,
                title = backup.title,
                author = backup.author,
                isbn = backup.isbn,
                edition = backup.edition,
                totalPages = backup.totalPages,
                coverPath = coverPath,
                currentPage = backup.currentPage,
                status = backup.status,
                startedOn = backup.startedOn,
                finishedOn = backup.finishedOn,
            ),
        )
        for ((thought, scratch) in downloaded) {
            val path = scratch?.let {
                storage.newThoughtFile(backup.key, thought.createdAt, it.extension).also { target -> moveInto(it, target) }
                    .absolutePath
            }
            // A voice note that was backed up before its words were typed out picks up where it left off.
            val waiting = thought.type == ThoughtType.VOICE && thought.text == null && path != null
            books.putThought(
                BookThought(
                    bookId = book.id,
                    page = thought.page,
                    createdAt = thought.createdAt,
                    type = thought.type,
                    text = thought.text,
                    mediaPath = path,
                    showInDiary = thought.showInDiary,
                    processing = if (waiting) ProcessingState.PENDING else ProcessingState.DONE,
                ),
            )
        }
    }

    private fun moveInto(scratch: File, target: File) {
        if (!scratch.renameTo(target)) {
            scratch.copyTo(target, overwrite = true)
            scratch.delete()
        }
    }

    /** Brings one day's mood, place and entries back, downloading each entry's media. */
    private suspend fun restoreDay(api: DriveApi, day: DayBackup, folder: List<DriveApi.RemoteFile>) {
        // Everything is downloaded before anything is recorded. A day counts as "on the
        // phone" as soon as it has one entry, so a connection that dropped half-way
        // through recording them would leave the rest of the day unrestorable.
        val downloaded = mutableListOf<Pair<EntryBackup, File?>>()
        try {
            for (entry in day.entries) {
                if (entry.media == null) {
                    downloaded += entry to null
                    continue
                }
                // An entry whose media is not in Drive cannot be brought back; its words alone would mislead.
                val remote = folder.firstOrNull { it.name == entry.media } ?: continue
                val scratch = journal.storage.newScratchFile(File(entry.media).extension.ifEmpty { "bin" })
                scratch.writeBytes(api.download(remote.id))
                downloaded += entry to scratch
            }
        } catch (e: Exception) {
            downloaded.forEach { it.second?.delete() }
            throw e
        }

        for ((entry, scratch) in downloaded) {
            var path: String? = null
            if (scratch != null) {
                val target = journal.storage.newJournalFile(day.date, entry.createdAt, scratch.extension)
                if (!scratch.renameTo(target)) {
                    scratch.copyTo(target, overwrite = true)
                    scratch.delete()
                }
                path = target.absolutePath
            }
            val restored = Entry(
                date = day.date,
                createdAt = entry.createdAt,
                type = entry.type,
                text = entry.text,
                mediaPath = path,
                placeName = entry.place,
                updatedAt = entry.updatedAt ?: entry.createdAt,
            )
            // A voice note or page that was backed up before its words were typed out
            // picks up where it left off.
            val waiting = restored.needsText && entry.text == null
            journal.putEntry(if (waiting) restored.copy(processing = ProcessingState.PENDING) else restored)
        }
        if (day.mood != null || day.place != null) journal.putMeta(DayMeta(day.date, day.mood, day.place))
    }

    /** Writes [bytes] as the clip for [date]. False if it does not play as a video. */
    private suspend fun save(date: LocalDate, bytes: ByteArray, caption: String): Boolean {
        val scratch = repository.storage.newTrimOutputFile()
        scratch.writeBytes(bytes)
        val duration = VideoMetadata.durationMs(scratch)
        if (duration <= 0) {
            scratch.delete()
            return false
        }
        repository.saveClip(date, scratch, duration, caption)
        return true
    }

    /** A day's journal on the phone: its mood and place (if any) and its entries. */
    private class LocalDay(val date: LocalDate, val meta: DayMeta?, val entries: List<Entry>)

    /** A book on the phone with its thoughts. */
    private class LocalBook(val book: Book, val thoughts: List<BookThought>)

    /** Every book on the shelf, in a fixed order. */
    private suspend fun localBooks(): List<LocalBook> =
        books.allBooks().sortedBy { it.backupKey }.map { LocalBook(it, books.thoughtsOf(it.id)) }

    /** Every day that has a mood, a place or an entry, oldest first. */
    private suspend fun localJournal(): List<LocalDay> {
        val metas = journal.allMeta().filter { it.mood != null || it.placeName != null }.associateBy { it.date }
        val entries = journal.allEntries().groupBy { it.date }
        return (metas.keys + entries.keys).sorted().map { LocalDay(it, metas[it], entries[it].orEmpty()) }
    }

    private suspend fun localClips(): List<ClipEntity> =
        repository.observeAll().first()
            .filter { it.file.exists() }
            .sortedBy { it.date }

    private companion object {
        const val MP4_MIME = "video/mp4"
        const val CAPTIONS_MIME = "application/json"
    }
}
