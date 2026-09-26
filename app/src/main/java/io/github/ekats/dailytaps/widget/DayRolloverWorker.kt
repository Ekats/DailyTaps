package io.github.ekats.dailytaps.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Redraws the widgets shortly after local midnight so boards with a daily reset show a fresh day
 * without waiting for the next tap. Reschedules itself for the following midnight.
 */
class DayRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext)
        schedule(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "day-rollover"

        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusSeconds(30)
            val delay = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(60_000)
            val request = OneTimeWorkRequestBuilder<DayRolloverWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
