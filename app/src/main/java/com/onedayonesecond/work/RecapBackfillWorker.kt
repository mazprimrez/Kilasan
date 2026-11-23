package com.onedayonesecond.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onedayonesecond.MainActivity
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.R
import com.onedayonesecond.data.BackfillProgress
import com.onedayonesecond.journal.RecapGenerator
import com.onedayonesecond.journal.RecapPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * "Write recaps for past weeks and months": a chain of one-period jobs. Each writes the
 * recap at the front of the queue kept in [com.onedayonesecond.data.JournalPrefs], then
 * leaves the next job to start a few seconds later, so the backend is asked one recap at
 * a time and the model's quota is not hit. A job waits for a connection, retries with
 * backoff, carries on in the background, and survives the app being closed. Stopping
 * keeps every recap already written.
 */
object BackfillWork {

    private const val WORK_NAME = "recap_backfill"
    const val CHANNEL_ID = "recaps"
    const val NOTIFICATION_ID = 1002

    /** The pause between two recaps. */
    const val SPACING_SECONDS = 5L

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Starts writing [periods] (already oldest first). [forced] are written again even if a recap exists. */
    suspend fun start(context: Context, periods: List<RecapPeriod>, forced: Set<String>) {
        if (periods.isEmpty()) return
        val container = (context.applicationContext as OneDayApp).container
        container.journalPrefs.startBackfill(periods.map { it.key }, forced)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request(0))
    }

    /** Leaves the next period to the next job, a little later. */
    internal fun next(context: Context) {
        // APPEND_OR_REPLACE: called from the running job, the next one waits for it to finish.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(SPACING_SECONDS))
    }

    suspend fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        val container = (context.applicationContext as OneDayApp).container
        container.journalPrefs.stopBackfill()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun request(delaySeconds: Long) = OneTimeWorkRequestBuilder<RecapBackfillWorker>()
        .setConstraints(online)
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Recaps", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progress while past recaps are being written"
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /** "7 of 15 written", with Stop while it runs; a quiet summary once it is done. */
    internal fun notify(context: Context, progress: BackfillProgress) {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canPost) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        if (progress.running) {
            val stop = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, StopBackfillReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentTitle("Writing past recaps")
                .setContentText(progress.waiting ?: BackfillLabels.progress(progress))
                .setProgress(progress.total, progress.handled, false)
                .setOngoing(true)
                .addAction(0, "Stop", stop)
        } else {
            builder.setContentTitle("Past recaps are written")
                .setContentText(BackfillLabels.done(progress))
                .setAutoCancel(true)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}

/** Words for a run, shared by the notification and Settings. */
object BackfillLabels {
    /** "7 of 15 written". */
    fun progress(progress: BackfillProgress): String = "${progress.handled} of ${progress.total} written"

    /** "14 recaps written, 1 skipped." */
    fun done(progress: BackfillProgress): String {
        val letters = "${progress.written} recap${if (progress.written == 1) "" else "s"} written"
        return if (progress.skipped == 0) "$letters." else "$letters, ${progress.skipped} skipped."
    }
}

class RecapBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OneDayApp).container
        val prefs = container.journalPrefs
        val progress = prefs.backfill.first()
        val key = progress.queue.firstOrNull() ?: return Result.success()
        BackfillWork.notify(applicationContext, progress)

        val period = RecapPeriod.parse(key)
        // A recap the nightly job wrote in the meantime is not written twice.
        val alreadyThere = key !in progress.forced && container.journal.recap(key) != null
        if (period == null || alreadyThere) return advance(key, written = false)

        return when (val outcome = container.recapGenerator.generate(period)) {
            RecapGenerator.Outcome.Written -> advance(key, written = true)
            is RecapGenerator.Outcome.NotEnough -> advance(key, written = false)
            RecapGenerator.Outcome.Offline -> {
                // The connection constraint brings this job back once the phone is online.
                prefs.setBackfillWaiting("Waiting for a connection")
                BackfillWork.notify(applicationContext, prefs.backfill.first())
                Result.retry()
            }
            RecapGenerator.Outcome.NotSetUp -> {
                BackfillWork.stop(applicationContext)
                Result.success()
            }
            is RecapGenerator.Outcome.Failed ->
                if (runAttemptCount < MAX_ATTEMPTS - 1) {
                    prefs.setBackfillWaiting("The server had a problem. Trying again shortly.")
                    BackfillWork.notify(applicationContext, prefs.backfill.first())
                    Result.retry()
                } else {
                    // One period the server keeps refusing does not stop the rest.
                    Log.w(TAG, "Skipped $key: ${outcome.message}")
                    advance(key, written = false)
                }
        }
    }

    private suspend fun advance(key: String, written: Boolean): Result {
        val prefs = (applicationContext as OneDayApp).container.journalPrefs
        prefs.advanceBackfill(key, written)
        val progress = prefs.backfill.first()
        BackfillWork.notify(applicationContext, progress)
        if (progress.running) BackfillWork.next(applicationContext)
        return Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val TAG = "RecapBackfill"
    }
}

/** The notification's Stop. */
class StopBackfillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                BackfillWork.stop(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
