package io.github.ekats.dailytaps.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.ekats.dailytaps.appScope
import io.github.ekats.dailytaps.repository
import kotlinx.coroutines.launch

/** "Add to home screen" from inside the app: asks the launcher to pin a widget already bound to a board. */
object PinWidget {
    private const val EXTRA_BOARD_ID = "boardId"

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context, boardId: Long): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            boardId.toInt(),
            Intent(context, PinnedReceiver::class.java)
                .setData(Uri.parse("dailytaps://pinned/$boardId"))
                .putExtra(EXTRA_BOARD_ID, boardId),
            // Mutable so the launcher can add EXTRA_APPWIDGET_ID for the new widget.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, BoardWidgetReceiver::class.java), null, callback)
    }

    class PinnedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val boardId = intent.getLongExtra(EXTRA_BOARD_ID, -1)
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || boardId < 0) return
            val pending = goAsync()
            context.appScope.launch {
                try {
                    context.repository.bindWidget(widgetId, boardId)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
