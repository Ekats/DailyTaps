package io.github.ekats.dailytaps

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.ekats.dailytaps.data.AppDatabase
import io.github.ekats.dailytaps.data.Repository
import io.github.ekats.dailytaps.widget.DayRolloverWorker
import io.github.ekats.dailytaps.widget.WidgetRefresher

class DailyTapsApp : Application() {

    /** For short fire-and-forget writes that must outlive a broadcast or an activity. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val repository: Repository by lazy {
        Repository(
            database,
            onChanged = { WidgetRefresher.refreshAll(this) },
            onSchedulesChanged = { DayRolloverWorker.schedule(this) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { DayRolloverWorker.schedule(this@DailyTapsApp) }
    }
}

val Context.repository: Repository
    get() = (applicationContext as DailyTapsApp).repository

val Context.appScope: CoroutineScope
    get() = (applicationContext as DailyTapsApp).appScope
