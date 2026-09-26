package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.SlotEntity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.MAX_GRID
import io.github.ekats.dailytaps.data.MIN_GRID
import io.github.ekats.dailytaps.data.TextScale
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.ColorField
import io.github.ekats.dailytaps.ui.components.FillEditor
import io.github.ekats.dailytaps.ui.components.SectionTitle
import io.github.ekats.dailytaps.ui.components.Stepper
import io.github.ekats.dailytaps.ui.components.SwitchRow
import kotlinx.coroutines.launch

/** Board settings. Every change saves at once, so placed widgets update while you edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSettingsScreen(boardId: Long, onBack: () -> Unit, onEditSlot: (Long) -> Unit) {
    val repo = LocalContext.current.repository
    val scope = rememberCoroutineScope()
    val data by remember(boardId) { repo.observeBoard(boardId) }.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_board)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val current = data ?: return@Scaffold
        val board = current.board
        // Local text so typing isn't disturbed by the database round trip. Every save carries it,
        // so a quick toggle right after typing can't write back the previous title.
        var title by remember(boardId) { mutableStateOf(board.title) }
        fun save(b: BoardEntity) = scope.launch { repo.updateBoard(b.copy(title = title)) }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Preview stays visible while the settings scroll underneath it.
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                BoardGrid(
                    current,
                    Modifier.fillMaxWidth().height(240.dp),
                    showDisabled = true,
                    onSlotClick = { onEditSlot(it.id) },
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            ) {
                Text(
                    stringResource(R.string.tap_slot_to_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                SectionTitle(stringResource(R.string.section_title))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        save(board.copy(title = it))
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.board_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(R.string.show_title), board.showTitle) { save(board.copy(showTitle = it)) }
                if (board.showTitle) {
                    ColorField(stringResource(R.string.title_color), board.titleColor) { save(board.copy(titleColor = it)) }
                }

                SectionTitle(stringResource(R.string.section_labels))
                Text(
                    stringResource(R.string.labels_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabelTable(current, onLabelChange = { slot, label ->
                    scope.launch { repo.updateSlotConfig(slot.copy(label = label)) }
                })
                Stepper(
                    stringResource(R.string.label_size),
                    board.labelSizeSp,
                    0..40,
                    format = { if (it == 0) stringResource(R.string.auto) else "$it sp" },
                ) { save(board.copy(labelSizeSp = it)) }

                SectionTitle(stringResource(R.string.section_layout))
                Stepper(stringResource(R.string.rows), board.rows, MIN_GRID..MAX_GRID) { save(board.copy(rows = it)) }
                Stepper(stringResource(R.string.columns), board.cols, MIN_GRID..MAX_GRID) { save(board.copy(cols = it)) }
                Stepper(stringResource(R.string.spacing), board.spacingDp, 0..16, format = { "$it dp" }) { save(board.copy(spacingDp = it)) }
                Stepper(stringResource(R.string.corner_radius), board.cornerRadiusDp, 0..32, format = { "$it dp" }) {
                    save(board.copy(cornerRadiusDp = it))
                }
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf(R.string.small, R.string.medium, R.string.large)
                    TextScale.entries.forEachIndexed { i, scale ->
                        SegmentedButton(
                            selected = board.textScale == scale,
                            onClick = { save(board.copy(textScale = scale)) },
                            shape = SegmentedButtonDefaults.itemShape(i, TextScale.entries.size),
                        ) { Text(stringResource(labels[i])) }
                    }
                }

                SectionTitle(stringResource(R.string.section_background))
                FillEditor(stringResource(R.string.board_background), board.background) { save(board.copy(background = it)) }

                SectionTitle(stringResource(R.string.section_behaviour))
                SwitchRow(
                    stringResource(R.string.reset_daily),
                    board.resetDaily,
                    supporting = stringResource(R.string.reset_daily_help),
                ) { save(board.copy(resetDaily = it)) }
            }
        }
    }
}

/** One compact text field per slot, laid out like the board, for filling in labels quickly. */
@Composable
private fun LabelTable(data: BoardWithSlots, onLabelChange: (SlotEntity, String) -> Unit) {
    // Local copies keep the cursor steady while the database round trip completes.
    val texts = remember(data.board.id) { mutableStateMapOf<Long, String>() }
    val shape = MaterialTheme.shapes.small
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until data.board.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until data.board.cols) {
                    val slot = data.slotAt(r, c)
                    if (slot == null) {
                        Box(Modifier.weight(1f))
                        continue
                    }
                    val text = texts[slot.id] ?: slot.label
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            texts[slot.id] = it
                            onLabelChange(slot, it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        "${'A' + c}${r + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
