package io.github.ekats.dailytaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.Defaults
import io.github.ekats.dailytaps.domain.ColorMath
import kotlin.math.roundToInt

/** A round color chip with a checkerboard behind it, so transparency is visible. */
@Composable
fun ColorSwatch(color: Int, modifier: Modifier = Modifier, size: Dp = 36.dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val outline = MaterialTheme.colorScheme.outline
    val ring = if (selected) MaterialTheme.colorScheme.primary else outline.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .drawBehind {
                val cell = this.size.width / 4
                for (x in 0 until 4) for (y in 0 until 4) {
                    drawRect(
                        if ((x + y) % 2 == 0) Color.LightGray else Color.White,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** HSV + alpha picker with preset swatches and a hex field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val hsv = remember { ColorMath.toHsv(initial) }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(ColorMath.alpha(initial) / 255f) }
    var hex by remember { mutableStateOf(ColorMath.toHex(initial)) }

    fun current(): Int = ColorMath.fromHsv((alpha * 255).roundToInt(), hue, sat, value)
    fun setFrom(c: Int, updateHex: Boolean = true) {
        val out = ColorMath.toHsv(c)
        // Keep the hue when picking a gray, so the sliders don't jump.
        if (out[1] > 0f) hue = out[0]
        sat = out[1]
        value = out[2]
        alpha = ColorMath.alpha(c) / 255f
        if (updateHex) hex = ColorMath.toHex(c)
    }

    val color = current()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = {
                            hex = it
                            ColorMath.parseHex(it)?.let { c -> setFrom(c, updateHex = false) }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        isError = ColorMath.parseHex(hex) == null,
                        label = { Text(stringResource(R.string.hex)) },
                        modifier = Modifier.padding(start = 12.dp).fillMaxWidth(),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Defaults.swatches.forEach { c ->
                        ColorSwatch(c, size = 32.dp, selected = c == color) { setFrom(c) }
                    }
                }
                SliderRow(stringResource(R.string.hue), hue / 360f, gradient = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }) {
                    hue = it * 360f
                    hex = ColorMath.toHex(current())
                }
                SliderRow(stringResource(R.string.saturation), sat, gradient = listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))) {
                    sat = it
                    hex = ColorMath.toHex(current())
                }
                SliderRow(stringResource(R.string.brightness), value, gradient = listOf(Color.Black, Color.hsv(hue, sat, 1f))) {
                    value = it
                    hex = ColorMath.toHex(current())
                }
                SliderRow(stringResource(R.string.opacity), alpha, gradient = listOf(Color(color).copy(alpha = 0f), Color(color).copy(alpha = 1f))) {
                    alpha = it
                    hex = ColorMath.toHex(current())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, gradient: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(gradient)),
            )
            Slider(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A labelled row with a swatch that opens [ColorPickerDialog]. */
@Composable
fun ColorField(label: String, color: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(color, size = 32.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                ColorMath.toHex(color),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) {
        ColorPickerDialog(label, color, onDismiss = { open = false }) {
            open = false
            onChange(it)
        }
    }
}
