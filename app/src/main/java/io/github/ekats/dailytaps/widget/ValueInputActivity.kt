package io.github.ekats.dailytaps.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.ekats.dailytaps.appScope
import io.github.ekats.dailytaps.data.EventSource
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.ValueDialog
import io.github.ekats.dailytaps.ui.theme.DailyTapsTheme
import kotlinx.coroutines.launch

/** Small dialog opened by tapping a VALUE slot on the home screen. */
class ValueInputActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slotId = intent.getLongExtra(EXTRA_SLOT_ID, -1)
        if (slotId < 0) {
            finish()
            return
        }
        setContent {
            DailyTapsTheme {
                var slot by remember { mutableStateOf<SlotEntity?>(null) }
                LaunchedEffect(slotId) {
                    slot = repository.slot(slotId)
                    if (slot == null) finish()
                }
                slot?.let { s ->
                    ValueDialog(
                        slot = s,
                        onDismiss = { finish() },
                        onSave = { value ->
                            // App scope: the write must finish even though the activity closes now.
                            appScope.launch { repository.recordValue(s.id, value, EventSource.WIDGET) }
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SLOT_ID = "slotId"

        fun intent(context: Context, slotId: Long): Intent =
            Intent(context, ValueInputActivity::class.java)
                .setData(Uri.parse("dailytaps://value/$slotId"))
                .putExtra(EXTRA_SLOT_ID, slotId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
