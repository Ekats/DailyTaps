package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalResources
import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.ResetMode
import io.github.ekats.dailytaps.domain.ResetSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import io.github.ekats.dailytaps.data.HeaderMode
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

                SectionTitle(stringResource(R.string.section_reset))
                ResetEditor(board) { save(it) }
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

/** Never / daily / weekly / monthly, the time, and the weekday or day of month. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ResetEditor(board: BoardEntity, onChange: (BoardEntity) -> Unit) {
    var pickTime by remember { mutableStateOf(false) }
    val locale = LocalResources.current.configuration.locales[0]
    val schedule = ResetSchedule.of(board)

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val labels = listOf(R.string.reset_never, R.string.reset_daily, R.string.reset_weekly, R.string.reset_monthly)
        ResetMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = board.resetMode == mode,
                onClick = { onChange(board.copy(resetMode = mode)) },
                shape = SegmentedButtonDefaults.itemShape(i, ResetMode.entries.size),
            ) { Text(stringResource(labels[i]), maxLines = 1) }
        }
    }
    if (board.resetMode == ResetMode.NEVER) {
        Text(
            stringResource(R.string.reset_never_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }

    if (board.resetMode == ResetMode.WEEKLY) {
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Monday first.
            for (d in 1..7) {
                FilterChip(
                    selected = board.resetWeekday == d,
                    onClick = { onChange(board.copy(resetWeekday = d)) },
                    label = { Text(DayOfWeek.of(d).getDisplayName(JavaTextStyle.SHORT, locale)) },
                )
            }
        }
    }
    if (board.resetMode == ResetMode.MONTHLY) {
        Stepper(stringResource(R.string.reset_month_day), board.resetMonthDay, 1..31) {
            onChange(board.copy(resetMonthDay = it))
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reset_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { pickTime = true }) {
            Text("%02d:%02d".format(board.resetMinute / 60, board.resetMinute % 60), style = MaterialTheme.typography.titleMedium)
        }
    }
    // Spell out the next reset, so the setting can be checked at a glance.
    schedule.nextReset(System.currentTimeMillis())?.let { next ->
        val text = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale))
        Text(
            stringResource(R.string.reset_next, text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.reset_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    if (pickTime) {
        val state = rememberTimePickerState(board.resetMinute / 60, board.resetMinute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    onChange(board.copy(resetMinute = ResetSchedule.unambiguousMinute(state.hour * 60 + state.minute)))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}
