package io.github.ekats.dailytaps.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.GradientDirection
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.SlotLogic
import kotlin.math.hypot

fun Fill.toBrush(): Brush = when (this) {
    is Fill.Solid -> SolidColor(Color(color))
    is Fill.Gradient -> {
        val colors = listOf(Color(start), Color(end))
        val inf = Float.POSITIVE_INFINITY
        when (direction) {
            GradientDirection.TOP_BOTTOM -> Brush.verticalGradient(colors)
            GradientDirection.LEFT_RIGHT -> Brush.horizontalGradient(colors)
            GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> Brush.linearGradient(colors, Offset.Zero, Offset(inf, inf))
            GradientDirection.BOTTOM_LEFT_TOP_RIGHT -> Brush.linearGradient(colors, Offset(0f, inf), Offset(inf, 0f))
            GradientDirection.RADIAL -> CornerRadialBrush(colors)
        }
    }
}

/** Radial gradient reaching the corners, matching what the widget bitmaps draw. */
private class CornerRadialBrush(private val colors: List<Color>) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        RadialGradientShader(size.center(), (hypot(size.width, size.height) / 2f).coerceAtLeast(1f), colors)

    private fun Size.center() = Offset(width / 2f, height / 2f)

    override fun equals(other: Any?) = other is CornerRadialBrush && other.colors == colors
    override fun hashCode() = colors.hashCode()
}

/**
 * Draws a board the way the home screen widget does. With [onSlotClick] set the cells are
 * buttons; [selected] outlines one cell (used by the editor).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardGrid(
    data: BoardWithSlots,
    modifier: Modifier = Modifier,
    today: Long = Days.today(),
    selected: SlotEntity? = null,
    showDisabled: Boolean = false,
    onSlotClick: ((SlotEntity) -> Unit)? = null,
    onSlotLongClick: ((SlotEntity) -> Unit)? = null,
) {
    val board = data.board
    val haptics = LocalHapticFeedback.current
    val gap = board.spacingDp.dp
    val boardShape = RoundedCornerShape((board.cornerRadiusDp + 4).coerceAtMost(28).dp)
    val cellShape = RoundedCornerShape(board.cornerRadiusDp.dp)

    BoxWithConstraints(
        modifier = modifier
            .clip(boardShape)
            .background(board.background.toBrush(), boardShape)
            .padding(4.dp + gap / 2),
    ) {
        val cellMin = minOf(maxWidth / board.cols, maxHeight / board.rows).value
        val labelSize = (cellMin * 0.22f).coerceIn(8f, 20f) * board.textScale.factor

        Column(Modifier.fillMaxSize()) {
            if (board.showTitle && board.title.isNotBlank()) {
                Text(
                    text = board.title,
                    color = Color(board.titleColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = (14f * board.textScale.factor).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .height((22f * board.textScale.factor).dp)
                        .padding(horizontal = gap / 2),
                )
            }
            for (r in 0 until board.rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until board.cols) {
                        Box(Modifier.weight(1f).fillMaxHeight().padding(gap / 2)) {
                            val slot = data.slotAt(r, c) ?: return@Box
                            if (!slot.enabled && !showDisabled) return@Box
                            val look = SlotLogic.appearance(slot, board, today)
                            val isSelected = selected?.id == slot.id
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(cellShape)
                                    .background(look.fill.toBrush(), cellShape)
                                    .then(
                                        if (!slot.enabled) {
                                            Modifier.border(1.dp, Color(look.textColor).copy(alpha = 0.4f), cellShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, cellShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (onSlotClick != null || onSlotLongClick != null) {
                                            Modifier.combinedClickable(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    onSlotClick?.invoke(slot)
                                                },
                                                onLongClick = onSlotLongClick?.let {
                                                    {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        it(slot)
                                                    }
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val alpha = if (slot.enabled) 1f else 0.4f
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    val hasLabel = look.label.isNotBlank()
                                    if (hasLabel) {
                                        Text(
                                            text = look.label,
                                            color = Color(look.textColor).copy(alpha = alpha),
                                            fontSize = labelSize.sp,
                                            lineHeight = (labelSize * 1.1f).sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = if (look.stateText == null) 3 else 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    look.stateText?.let {
                                        Text(
                                            text = it,
                                            color = Color(look.textColor).copy(alpha = alpha),
                                            fontSize = (if (hasLabel) labelSize * 0.85f else labelSize * 1.2f).sp,
                                            fontWeight = if (hasLabel) FontWeight.Normal else FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
