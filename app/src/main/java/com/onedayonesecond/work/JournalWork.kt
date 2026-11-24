package com.onedayonesecond.work

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.ai.AiException
import com.onedayonesecond.ai.AiNotConfiguredException
import com.onedayonesecond.ai.AiSignInNeededException
import com.onedayonesecond.journal.RecapGenerator
import com.onedayonesecond.journal.RecapRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Scheduling for the journal's background jobs. */
object JournalWork {

    private const val TYPE_OUT = "journal_type_out"
    private const val TAG = "journal_tag"
    private const val RECAPS_NIGHTLY = "journal_recaps_nightly"
    private const val RECAPS_RETRY = "journal_recaps_retry"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Leaves a job that types out waiting voice notes and pages as soon as the phone
     * is online. This is the "retry automatically" behind the offline message.
     */
    fun typeOutWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<TypeOutWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // APPEND_OR_REPLACE: if a run is already under way, another follows it, so an
        // entry saved a moment after that run looked is not left behind.
        WorkManager.getInstance(context).enqueueUniqueWork(TYPE_OUT, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Leaves a job that tags entries for Patterns once the phone is online: new and
     * edited entries, and, the first time, every entry written before tags existed.
     */
    fun tagWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<TagWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // As with typing out: an entry saved while a run is under way gets a run after it.
        WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Stops tagging; what is already tagged stays. */
    fun stopTagging(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG)
    }

    /**
     * A check every night, a little after [RecapRules.NIGHT]. It writes whichever recap
     * is due and missing: the week's on Sunday, the month's on the 1st, the year's on
     * 31 December.
     */
    fun scheduleRecaps(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(RecapRules.NIGHT).plusMinutes(5)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<RecapWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(now, next))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(RECAPS_NIGHTLY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** After a night offline: try again as soon as there is a connection. */
    fun retryRecapsWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<RecapWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RECAPS_RETRY, ExistingWorkPolicy.KEEP, request)
    }
}

@UnstableApi
class TypeOutWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        if (!container.entryProcessor.isConfigured) return Result.success()
        // Voice thoughts logged on books wait in the same queue as the diary's voice notes.
        val entriesLeft = container.entryProcessor.processWaiting()
        val thoughtsLeft = container.thoughtProcessor.processWaiting()
        val left = entriesLeft || thoughtsLeft
        return if (left && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

@UnstableApi
class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        val generator = container.recapGenerator
        if (!generator.isConfigured) return Result.success()

        var offline = false
        for (period in RecapRules.latestDue(LocalDateTime.now())) {
            val existing = container.journal.recap(period.key)
            // A letter written before its period was over (the year so far, asked for in
            // Settings) is replaced by the real one once it is due.
            if (existing != null && !RecapRules.writtenEarly(period, existing.createdAt)) continue
            if (generator.generate(period) == RecapGenerator.Outcome.Offline) offline = true
        }
        // The nightly check itself always succeeds, so the daily rhythm is kept; a
        // separate job waits for the connection.
        if (offline) JournalWork.retryRecapsWhenOnline(applicationContext)
        return Result.success()
    }
}

/**
 * Tags entries a batch of 20 at a time, oldest first, with a short pause between
 * batches. Stops after [MAX_BATCHES] and leaves the rest to the next run, so a first
 * run over a long diary does not hold one job open for ever.
 */
@UnstableApi
class TagWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        val tagger = container.entryTagger
        if (!tagger.isConfigured || container.journalPrefs.taggingStopped.first()) return Result.success()
        repeat(MAX_BATCHES) { batch ->
            if (batch > 0) delay(SPACING_MS)
            if (container.journalPrefs.taggingStopped.first()) return Result.success()
            val tagged = try {
                tagger.tagNextBatch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return Result.retry()
            } catch (e: AiException) {
                Log.w(LOG_TAG, "Tagging failed: ${e.status}")
                return if (e.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            } catch (e: AiSignInNeededException) {
                // Only the app in front of the user can ask who they are; the next save tries again.
                return Result.success()
            } catch (e: AiNotConfiguredException) {
                return Result.success()
            }
            if (tagged == 0) {
                // Nothing left, or the answer left every entry out: try those again later.
                val left = tagger.pending().isNotEmpty()
                return if (left && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
        if (tagger.pending().isNotEmpty()) JournalWork.tagWhenOnline(applicationContext)
        return Result.success()
    }

    private companion object {
        const val LOG_TAG = "TagWorker"
        const val MAX_BATCHES = 30
        const val MAX_ATTEMPTS = 8
        const val SPACING_MS = 1_500L
    }
}
