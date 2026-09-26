package io.github.ekats.dailytaps.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefresher {
    /**
     * Re-renders every board widget. Running widget sessions already follow the database, this
     * wakes the ones whose session has ended.
     */
    suspend fun refreshAll(context: Context) {
        runCatching { BoardWidget().updateAll(context) }
    }
}
