package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.NewBoardDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBoard: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditBoard: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()
    val boards by remember { repo.observeAllBoards() }.collectAsStateWithLifecycle(null)
    val todayStart = remember { Days.startOfDayMillis(Days.today()) }
    val todayCounts by remember { repo.observeTapCountsSince(todayStart) }.collectAsStateWithLifecycle(emptyList())
    val bindings by remember { repo.observeWidgetBindings() }.collectAsStateWithLifecycle(emptyList())
    var showNew by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = stringResource(R.string.stats))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_board)) },
            )
        },
    ) { padding ->
        val list = boards
        if (list != null && list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.width(48.dp).height(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list.orEmpty(), key = { it.board.id }) { b ->
                val taps = todayCounts.firstOrNull { it.boardId == b.board.id }?.taps ?: 0
                val widgets = bindings.count { it.boardId == b.board.id }
                ElevatedCard(onClick = { onOpenBoard(b.board.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BoardGrid(
                            b,
                            Modifier.width(112.dp).aspectRatio((b.board.cols.toFloat() / b.board.rows).coerceIn(0.5f, 2f)),
                        )
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(
                                b.board.title.ifBlank { stringResource(R.string.untitled_board) },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.grid_size, b.board.rows, b.board.cols),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                pluralStringResource(R.plurals.taps_today, taps, taps),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (widgets > 0) {
                                Text(
                                    pluralStringResource(R.plurals.widget_count, widgets, widgets),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onEditBoard(b.board.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_board))
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
                scope.launch { onOpenBoard(repo.createBoard(title, rows, cols)) }
            },
        )
    }
}
