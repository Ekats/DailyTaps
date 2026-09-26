package io.github.ekats.dailytaps.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.EventKind
import io.github.ekats.dailytaps.data.EventSource
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.data.TapEventEntity
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.SlotLogic
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.SectionTitle
import io.github.ekats.dailytaps.widget.PinWidget
import io.github.ekats.dailytaps.ui.components.ValueDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    boardId: Long,
    onBack: () -> Unit,
    onEditBoard: () -> Unit,
    onEditSlot: (Long) -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()
    val data by remember(boardId) { repo.observeBoard(boardId) }.collectAsStateWithLifecycle(null)
    val recent by remember(boardId) { repo.observeRecentEvents(boardId, 50) }.collectAsStateWithLifecycle(emptyList())
    var valueSlot by remember { mutableStateOf<SlotEntity?>(null) }
    var actionSlot by remember { mutableStateOf<SlotEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val board = data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(board?.board?.title?.ifBlank { null } ?: stringResource(R.string.untitled_board)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = stringResource(R.string.stats))
                    }
                    IconButton(onClick = onEditBoard) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_board))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (PinWidget.isSupported(context)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_to_home)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, null) },
                                onClick = {
                                    menu = false
                                    PinWidget.request(context, boardId)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                menu = false
                                scope.launch {
                                    repo.duplicateBoard(boardId)
                                    Toast.makeText(context, R.string.duplicated, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_board)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menu = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (board == null) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                BoardGrid(
                    board,
                    Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    onSlotClick = { slot ->
                        if (slot.type == SlotType.VALUE) {
                            valueSlot = slot
                        } else {
                            scope.launch { repo.press(slot.id, EventSource.APP) }
                        }
                    },
                    onSlotLongClick = { actionSlot = it },
                )
                Text(
                    stringResource(R.string.board_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SectionTitle(stringResource(R.string.recent_activity))
                if (recent.isEmpty()) {
                    Text(stringResource(R.string.no_activity), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(recent, key = { it.id }) { e ->
                EventRow(e, board, onDelete = { scope.launch { repo.deleteEvent(e.id) } })
            }
        }
    }

    valueSlot?.let { s ->
        ValueDialog(
            slot = s,
            onDismiss = { valueSlot = null },
            onSave = { v ->
                valueSlot = null
                scope.launch { repo.recordValue(s.id, v, EventSource.APP) }
            },
        )
    }

    actionSlot?.let { s ->
        val current = board?.slots?.firstOrNull { it.id == s.id } ?: s
        SlotActionsSheet(
            slot = board?.let { SlotLogic.effective(current, it.board, Days.today()) } ?: current,
            onDismiss = { actionSlot = null },
            onEdit = {
                actionSlot = null
                onEditSlot(s.id)
            },
            onAdjust = { change -> scope.launch { repo.adjust(s.id, change) } },
            onLogValue = {
                actionSlot = null
                valueSlot = current
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_board_title)) },
            text = { Text(stringResource(R.string.delete_board_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    board?.board?.let { b ->
                        scope.launch {
                            repo.deleteBoard(b)
                            onBack()
                        }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

fun slotName(slot: SlotEntity?, fallback: String): String =
    slot?.label?.takeIf { it.isNotBlank() } ?: slot?.let { "${'A' + it.col}${it.row + 1}" } ?: fallback

@Composable
private fun EventRow(e: TapEventEntity, board: BoardWithSlots, onDelete: () -> Unit) {
    val slot = board.slots.firstOrNull { it.id == e.slotId }
    val time = remember(e.timestamp) {
        Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    }
    val what = when {
        e.kind == EventKind.VALUE -> SlotLogic.formatValue(e.value ?: 0.0, slot?.valueUnit.orEmpty())
        slot?.type == SlotType.COUNTER -> (if (e.delta >= 0) "+${e.delta}" else "${e.delta}") + " → ${e.count}"
        else -> slot?.states?.getOrNull(e.stateIndex)?.name ?: "#${e.stateIndex}"
    }
    val source = stringResource(if (e.source == EventSource.WIDGET) R.string.source_widget else R.string.source_app)
    val kind = if (e.kind == EventKind.ADJUST) " · " + stringResource(R.string.adjusted) else ""
    ListItem(
        headlineContent = { Text("${slotName(slot, stringResource(R.string.slot))}: $what") },
        supportingContent = { Text("$time · $source$kind") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_entry))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlotActionsSheet(
    slot: SlotEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: ((SlotEntity, Long) -> SlotEntity) -> Unit,
    onLogValue: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(slotName(slot, stringResource(R.string.slot)), style = MaterialTheme.typography.titleLarge)
            when (slot.type) {
                SlotType.STATES -> {
                    Text(stringResource(R.string.set_state), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slot.states.forEachIndexed { i, st ->
                            FilterChip(
                                selected = slot.stateIndex == i,
                                onClick = { onAdjust { s, today -> SlotLogic.withState(s, i, today) } },
                                label = { Text(st.name.ifBlank { "#${i + 1}" }) },
                            )
                        }
                    }
                }
                SlotType.COUNTER -> {
                    Text(stringResource(R.string.count_is, slot.count), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAdjust { s, today -> SlotLogic.withCount(s, s.count - s.counterStep, today) } }) {
                            Text("−${slot.counterStep}")
                        }
                        OutlinedButton(onClick = { onAdjust { s, today -> SlotLogic.withCount(s, s.count + s.counterStep, today) } }) {
                            Text("+${slot.counterStep}")
                        }
                        OutlinedButton(onClick = { onAdjust { s, today -> SlotLogic.withCount(s, 0, today) } }) {
                            Text(stringResource(R.string.reset))
                        }
                    }
                }
                SlotType.VALUE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onLogValue) { Text(stringResource(R.string.log_value)) }
                        OutlinedButton(onClick = { onAdjust { s, today -> SlotLogic.cleared(s, today) } }) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
            }
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text(stringResource(R.string.edit_slot), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
