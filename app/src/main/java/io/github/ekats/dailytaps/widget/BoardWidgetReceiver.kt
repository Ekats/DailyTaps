package io.github.ekats.dailytaps.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.ekats.dailytaps.appScope
import io.github.ekats.dailytaps.repository
import kotlinx.coroutines.launch

class BoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DayRolloverWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // The base class already holds this broadcast open with goAsync(), so the cleanup runs on
        // the app scope instead. A leftover binding is harmless if it gets cut short.
        val ids = appWidgetIds.toList()
        context.appScope.launch { context.repository.unbindWidgets(ids) }
    }
}
