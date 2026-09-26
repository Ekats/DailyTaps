package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.HeaderMode
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateMapOf
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
                LabelTable(current, onLabelsChange = { labels -> scope.launch { repo.setLabels(labels) } })
                Stepper(
                    stringResource(R.string.label_size),
                    board.labelSizeSp,
                    0..40,
                    format = { if (it == 0) stringResource(R.string.auto) else "$it sp" },
                ) { save(board.copy(labelSizeSp = it)) }

                SectionTitle(stringResource(R.string.section_headers))
                Text(stringResource(R.string.column_headers), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf(R.string.header_none, R.string.header_weekdays, R.string.header_custom)
                    HeaderMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = board.colHeaderMode == mode,
                            onClick = { save(board.copy(colHeaderMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(i, HeaderMode.entries.size),
                        ) { Text(stringResource(labels[i])) }
                    }
                }
                if (board.colHeaderMode == HeaderMode.CUSTOM) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until board.cols) {
                            RememberedCompactField(
                                key = "col$c",
                                value = board.colHeaders.getOrElse(c) { "" },
                                hint = "${'A' + c}",
                                modifier = Modifier.weight(1f),
                            ) { save(board.copy(colHeaders = board.colHeaders.padded(board.cols).apply { set(c, it) })) }
                        }
                    }
                }
                SwitchRow(stringResource(R.string.row_headers), board.showRowHeaders) { save(board.copy(showRowHeaders = it)) }
                if (board.showRowHeaders) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in 0 until board.rows) {
                            RememberedCompactField(
                                key = "row$r",
                                value = board.rowHeaders.getOrElse(r) { "" },
                                hint = "${r + 1}",
                                modifier = Modifier.fillMaxWidth(),
                            ) { save(board.copy(rowHeaders = board.rowHeaders.padded(board.rows).apply { set(r, it) })) }
                        }
                    }
                }

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

/**
 * One compact text field per slot, laid out like the board, for filling in labels quickly. The
 * ticks along the top and the left repeat what you type across that column or row.
 */
@Composable
private fun LabelTable(data: BoardWithSlots, onLabelsChange: (Map<Long, String>) -> Unit) {
    val board = data.board
    // Local copies keep the cursor steady while saves round-trip; repeats update them directly.
    val texts = remember(board.id) { mutableStateMapOf<Long, String>() }
    val repeatRows = remember(board.id) { mutableStateMapOf<Int, Boolean>() }
    val repeatCols = remember(board.id) { mutableStateMapOf<Int, Boolean>() }
    val tick = 32.dp

    fun change(slot: SlotEntity, label: String) {
        val targets = data.visibleSlots.filter {
            it.id == slot.id ||
                (repeatRows[slot.row] == true && it.row == slot.row) ||
                (repeatCols[slot.col] == true && it.col == slot.col)
        }
        targets.forEach { texts[it.id] = label }
        onLabelsChange(targets.associate { it.id to label })
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(tick))
            for (c in 0 until board.cols) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Checkbox(
                        checked = repeatCols[c] == true,
                        onCheckedChange = { repeatCols[c] = it },
                        modifier = Modifier.size(tick),
                    )
                }
            }
        }
        for (r in 0 until board.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = repeatRows[r] == true,
                    onCheckedChange = { repeatRows[r] = it },
                    modifier = Modifier.size(tick),
                )
                for (c in 0 until board.cols) {
                    val slot = data.slotAt(r, c)
                    if (slot == null) {
                        Box(Modifier.weight(1f))
                        continue
                    }
                    CompactField(
                        value = texts[slot.id] ?: slot.label,
                        hint = "${'A' + c}${r + 1}",
                        modifier = Modifier.weight(1f),
                    ) { change(slot, it) }
                }
            }
        }
    }
}

/** [CompactField] that keeps its own text, for fields whose value only comes back through a save. */
@Composable
private fun RememberedCompactField(key: String, value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(value) }
    CompactField(text, hint, modifier) {
        text = it
        onChange(it)
    }
}

/** A small bordered text field, fully controlled by [value]. */
@Composable
private fun CompactField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onChange,
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
                if (value.isEmpty()) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                inner()
            }
        },
        modifier = modifier,
    )
}

/** A copy of the list with at least [size] entries, so any index up to it can be set. */
private fun List<String>.padded(size: Int): MutableList<String> =
    toMutableList().apply { while (this.size < size) add("") }
