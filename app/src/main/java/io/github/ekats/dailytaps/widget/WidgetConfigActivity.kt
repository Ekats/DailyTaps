package io.github.ekats.dailytaps.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.NewBoardDialog
import io.github.ekats.dailytaps.ui.theme.DailyTapsTheme
import kotlinx.coroutines.launch

/** Shown when a widget is placed (and on reconfigure): pick which board it displays. */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without choosing cancels the placement.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            DailyTapsTheme {
                val boards by remember { repository.observeAllBoards() }.collectAsStateWithLifecycle(emptyList())
                ConfigScreen(boards, onPick = ::bind, onCreate = { title, rows, cols ->
                    lifecycleScope.launch { bind(repository.createBoard(title, rows, cols)) }
                })
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun bind(boardId: Long) {
        lifecycleScope.launch {
            repository.bindWidget(appWidgetId, boardId)
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                BoardWidget().update(this@WidgetConfigActivity, glanceId)
            }
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    boards: List<BoardWithSlots>,
    onPick: (Long) -> Unit,
    onCreate: (String, Int, Int) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_pick_board)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_board)) },
            )
        },
    ) { padding ->
        if (boards.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.widget_no_boards), style = MaterialTheme.typography.bodyLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(boards, key = { it.board.id }) { b ->
                Card(Modifier.fillMaxWidth().clickable { onPick(b.board.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BoardGrid(
                            b,
                            Modifier.size(96.dp),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(b.board.title.ifBlank { stringResource(R.string.untitled_board) }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.grid_size, b.board.rows, b.board.cols),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewBoardDialog(
            onDismiss = { showNew = false },
            onCreate = { title, rows, cols ->
                showNew = false
                scope.launch { onCreate(title, rows, cols) }
            },
        )
    }
}
