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
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.journal.RecapGenerator
import com.onedayonesecond.journal.RecapRules
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Scheduling for the journal's background jobs. */
object JournalWork {

    private const val TYPE_OUT = "journal_type_out"
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
