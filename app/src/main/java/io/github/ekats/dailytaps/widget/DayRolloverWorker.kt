package io.github.ekats.dailytaps.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ekats.dailytaps.domain.ResetSchedule
import io.github.ekats.dailytaps.repository
import java.util.concurrent.TimeUnit

/**
 * Redraws the widgets just after the next board reset, so a reset board looks fresh without
 * waiting for a tap, then schedules itself for the reset after that. The reset itself doesn't
 * depend on this running on time: slots are compared with the schedule whenever they are drawn
 * or tapped.
 */
class DayRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext)
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "day-rollover"

        /** Schedules the next wake-up from the boards' current schedules, replacing any earlier one. */
        suspend fun schedule(context: Context) {
            val now = System.currentTimeMillis()
            val next = context.repository.allBoards()
                .mapNotNull { ResetSchedule.of(it.board).nextReset(now) }
                .minOrNull()
            val work = WorkManager.getInstance(context)
            if (next == null) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            // A few seconds late so the new period has certainly begun when the widget redraws.
            val delay = (next - now + 5_000).coerceAtLeast(5_000)
            val request = OneTimeWorkRequestBuilder<DayRolloverWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
