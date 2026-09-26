package io.github.ekats.dailytaps.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.Defaults
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotStyle
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.domain.SlotLogic
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.ColorField
import io.github.ekats.dailytaps.ui.components.FillEditor
import io.github.ekats.dailytaps.ui.components.SectionTitle
import io.github.ekats.dailytaps.ui.components.Stepper
import io.github.ekats.dailytaps.ui.components.SwitchRow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val MAX_STATES = 8

/**
 * Edits one slot. Works on a local draft that is saved on every change, so text fields keep their
 * cursor and the widget follows along live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditorScreen(slotId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()
    var draft by remember(slotId) { mutableStateOf<SlotEntity?>(null) }
    LaunchedEffect(slotId) { draft = repo.observeSlot(slotId).filterNotNull().first() }
    val slot = draft
    val boardData by remember(slot?.boardId) {
        slot?.let { repo.observeBoard(it.boardId) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)

    fun update(s: SlotEntity) {
        draft = s
        scope.launch { repo.updateSlotConfig(s) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_slot)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (slot == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
        ) {
            boardData?.let { PreviewRow(it, slot) }

            SectionTitle(stringResource(R.string.section_text))
            OutlinedTextField(
                value = slot.label,
                onValueChange = { update(slot.copy(label = it)) },
                label = { Text(stringResource(R.string.slot_label)) },
                supportingText = { Text(stringResource(R.string.slot_label_help)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper(
                stringResource(R.string.label_size),
                slot.labelSizeSp,
                0..40,
                format = { if (it == 0) stringResource(R.string.board_default) else "$it sp" },
            ) { update(slot.copy(labelSizeSp = it)) }
            SwitchRow(
                stringResource(R.string.show_state_text),
                slot.showStateText,
                supporting = stringResource(R.string.show_state_text_help),
            ) { update(slot.copy(showStateText = it)) }
            SwitchRow(
                stringResource(R.string.slot_enabled),
                slot.enabled,
                supporting = stringResource(R.string.slot_enabled_help),
            ) { update(slot.copy(enabled = it)) }

            SectionTitle(stringResource(R.string.section_behaviour))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val labels = listOf(R.string.type_states, R.string.type_counter, R.string.type_value)
                SlotType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = slot.type == t,
                        onClick = { update(SlotLogic.retyped(slot, t)) },
                        shape = SegmentedButtonDefaults.itemShape(i, SlotType.entries.size),
                    ) { Text(stringResource(labels[i])) }
                }
            }
            Text(
                stringResource(
                    when (slot.type) {
                        SlotType.STATES -> R.string.type_states_help
                        SlotType.COUNTER -> R.string.type_counter_help
                        SlotType.VALUE -> R.string.type_value_help
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            when (slot.type) {
                SlotType.COUNTER -> {
                    Stepper(stringResource(R.string.counter_step), slot.counterStep, 1..100) { update(slot.copy(counterStep = it)) }
                    NumberField(
                        stringResource(R.string.counter_target),
                        slot.counterTarget,
                        help = stringResource(R.string.counter_target_help),
                    ) { update(slot.copy(counterTarget = it)) }
                }
                SlotType.VALUE -> {
                    OutlinedTextField(
                        value = slot.valueUnit,
                        onValueChange = { update(slot.copy(valueUnit = it)) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.value_unit)) },
                        supportingText = { Text(stringResource(R.string.value_unit_help)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                SlotType.STATES -> Unit
            }

            SectionTitle(stringResource(R.string.section_colors))
            slot.states.forEachIndexed { i, style ->
                StateCard(
                    index = i,
                    style = style,
                    title = stateTitle(slot.type, i),
                    canEditName = slot.type == SlotType.STATES,
                    canDelete = slot.type == SlotType.STATES && slot.states.size > 2,
                    canMoveUp = slot.type == SlotType.STATES && i > 0,
                    canMoveDown = slot.type == SlotType.STATES && i < slot.states.lastIndex,
                    onChange = { new -> update(slot.copy(states = slot.states.toMutableList().also { it[i] = new })) },
                    onDelete = { update(slot.copy(states = slot.states.filterIndexed { j, _ -> j != i })) },
                    onMove = { dir ->
                        val list = slot.states.toMutableList()
                        list.add(i + dir, list.removeAt(i))
                        update(slot.copy(states = list))
                    },
                )
            }
            if (slot.type == SlotType.STATES && slot.states.size < MAX_STATES) {
                OutlinedButton(
                    onClick = {
                        val base = slot.states.last()
                        val next = Defaults.swatches[(slot.states.size * 5) % (Defaults.swatches.size - 6)]
                        update(slot.copy(states = slot.states + base.copy(name = "State ${slot.states.size + 1}", fill = Fill.Solid(next))))
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_state), modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle(stringResource(R.string.section_shortcuts))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        repo.applyStyleToBoard(slot)
                        Toast.makeText(context, R.string.style_applied, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FormatPaint, contentDescription = null)
                Text(stringResource(R.string.apply_style_all), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun stateTitle(type: SlotType, i: Int): String = when (type) {
    SlotType.STATES -> stringResource(R.string.state_n, i + 1)
    SlotType.COUNTER -> stringResource(if (i == 0) R.string.counter_start_look else R.string.counter_target_look)
    SlotType.VALUE -> stringResource(if (i == 0) R.string.value_empty_look else R.string.value_logged_look)
}

@Composable
private fun PreviewRow(board: BoardWithSlots, slot: SlotEntity) {
    // The whole board with this slot outlined, and the slot itself shown larger in each state.
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BoardGrid(
            board.copy(slots = board.slots.map { if (it.id == slot.id) slot else it }),
            Modifier.fillMaxWidth().height(200.dp),
            selected = slot,
            showDisabled = true,
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val previews = when (slot.type) {
            SlotType.STATES -> slot.states.indices.map { slot.copy(stateIndex = it) }
            SlotType.COUNTER -> {
                val t = slot.counterTarget.coerceAtLeast(1)
                listOf(0, (t + 1) / 2, t).distinct().map { slot.copy(count = it) }
            }
            SlotType.VALUE -> listOf(slot.copy(lastValue = null), slot.copy(lastValue = slot.lastValue ?: 42.0))
        }.take(4)
        previews.forEach { p ->
            val single = BoardWithSlots(board.board.copy(rows = 1, cols = 1, showTitle = false, resetDaily = false), listOf(p.copy(row = 0, col = 0, enabled = true)))
            Box(Modifier.weight(1f).aspectRatio(1f)) {
                BoardGrid(single, Modifier.fillMaxSize())
            }
        }
        repeat(4 - previews.size) { Box(Modifier.weight(1f).height(1.dp)) }
    }
}

@Composable
private fun StateCard(
    index: Int,
    style: SlotStyle,
    title: String,
    canEditName: Boolean,
    canDelete: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (SlotStyle) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge) }
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canMoveUp) {
                    IconButton(onClick = { onMove(-1) }) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up)) }
                }
                if (canMoveDown) {
                    IconButton(onClick = { onMove(1) }) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down)) }
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                }
            }
            if (canEditName) {
                OutlinedTextField(
                    value = style.name,
                    onValueChange = { onChange(style.copy(name = it)) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.state_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FillEditor(stringResource(R.string.fill), style.fill, Modifier.padding(top = 8.dp)) { onChange(style.copy(fill = it)) }
            ColorField(stringResource(R.string.text_color), style.textColor) { onChange(style.copy(textColor = it)) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, help: String, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter(Char::isDigit).take(6)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        singleLine = true,
        label = { Text(label) },
        supportingText = { Text(help) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
