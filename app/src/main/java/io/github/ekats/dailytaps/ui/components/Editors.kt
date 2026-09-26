package io.github.ekats.dailytaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.GradientDirection
import io.github.ekats.dailytaps.data.MAX_GRID
import io.github.ekats.dailytaps.data.MIN_GRID
import io.github.ekats.dailytaps.domain.ColorMath

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A minus / value / plus control for small integers. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, modifier: Modifier = Modifier, format: @Composable (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease))
        }
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase))
        }
    }
}

/** Solid / gradient switch, the color(s), and for gradients the direction. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FillEditor(label: String, fill: Fill, modifier: Modifier = Modifier, onChange: (Fill) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(fill.toBrush()),
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp).weight(1f))
        }
        val isGradient = fill is Fill.Gradient
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = !isGradient,
                onClick = { if (fill is Fill.Gradient) onChange(Fill.Solid(fill.start)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.solid)) }
            SegmentedButton(
                selected = isGradient,
                onClick = { if (fill is Fill.Solid) onChange(Fill.Gradient(fill.color, shift(fill.color))) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.gradient)) }
        }
        when (fill) {
            is Fill.Solid -> ColorField(stringResource(R.string.color), fill.color) { onChange(Fill.Solid(it)) }
            is Fill.Gradient -> {
                ColorField(stringResource(R.string.gradient_start), fill.start) { onChange(fill.copy(start = it)) }
                ColorField(stringResource(R.string.gradient_end), fill.end) { onChange(fill.copy(end = it)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradientDirection.entries.forEach { d ->
                        FilterChip(
                            selected = fill.direction == d,
                            onClick = { onChange(fill.copy(direction = d)) },
                            label = { Text(directionLabel(d)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun directionLabel(d: GradientDirection): String = when (d) {
    GradientDirection.TOP_BOTTOM -> "↓"
    GradientDirection.LEFT_RIGHT -> "→"
    GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> "↘"
    GradientDirection.BOTTOM_LEFT_TOP_RIGHT -> "↗"
    GradientDirection.RADIAL -> stringResource(R.string.radial)
}

/** A second gradient color derived from the first: same hue, noticeably lighter or darker. */
private fun shift(color: Int): Int {
    val (h, s, v) = ColorMath.toHsv(color).let { Triple(it[0], it[1], it[2]) }
    return ColorMath.fromHsv(ColorMath.alpha(color), (h + 35f) % 360f, s, if (v > 0.5f) v - 0.3f else v + 0.35f)
}

@Composable
fun NewBoardDialog(onDismiss: () -> Unit, onCreate: (title: String, rows: Int, cols: Int) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableIntStateOf(3) }
    var cols by rememberSaveable { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_board)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.board_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Stepper(stringResource(R.string.rows), rows, MIN_GRID..MAX_GRID) { rows = it }
                Stepper(stringResource(R.string.columns), cols, MIN_GRID..MAX_GRID) { cols = it }
                Box(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title.trim(), rows, cols) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
