package io.github.ekats.dailytaps.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ekats.dailytaps.MainActivity
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.EventSource
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.SlotLogic
import io.github.ekats.dailytaps.repository
import kotlinx.coroutines.flow.Flow

class BoardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repo = context.repository
        val initial = repo.widgetBoard(appWidgetId)
        val updates = repo.observeWidgetBoard(appWidgetId)
        provideContent(content(appWidgetId, initial, updates))
    }

    // Built outside provideGlance so the composable lambda doesn't capture coroutine state.
    private fun content(
        appWidgetId: Int,
        initial: BoardWithSlots?,
        updates: Flow<BoardWithSlots?>,
    ): @Composable () -> Unit = {
        val data by remember(updates) { updates }.collectAsState(initial)
        val board = data
        if (board == null) {
            Unconfigured(appWidgetId)
        } else {
            BoardContent(board)
        }
    }
}

private fun fixed(argb: Int): ColorProvider = ColorProvider(day = Color(argb), night = Color(argb))

private val needsBitmapCorners = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

@Composable
private fun Unconfigured(appWidgetId: Int) {
    val context = LocalContext.current
    val intent = Intent(context, WidgetConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("dailytaps://configure/$appWidgetId"))
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(fixed(0xE61C1B1F.toInt()))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_choose_board),
            style = TextStyle(color = fixed(0xFFE6E1E5.toInt()), fontSize = 14.sp, textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(12.dp),
        )
    }
}

// Android limits how deeply RemoteViews may nest (10 levels), so the layout stays shallow:
// Column > Row > gap Box > slot Box > (Column >) Text. Fills that need a bitmap (gradients, and
// rounded corners before Android 12) go on as an image background rather than an extra child.
@Composable
private fun BoardContent(data: BoardWithSlots) {
    val context = LocalContext.current
    val board = data.board
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val today = Days.today()

    val gap = board.spacingDp.dp
    val outer = 4.dp + gap / 2
    val titleHeight = if (board.showTitle && board.title.isNotBlank()) (22f * board.textScale.factor).dp else 0.dp
    val cellW = ((size.width - outer * 2) / board.cols - gap).coerceAtLeast(1.dp)
    val cellH = ((size.height - outer * 2 - titleHeight) / board.rows - gap).coerceAtLeast(1.dp)
    val radius = board.cornerRadiusDp.dp
    val boardRadius = (board.cornerRadiusDp + 4).coerceAtMost(28).dp

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .fillBackground(board.background, size.width, size.height, boardRadius, density)
            .padding(outer),
    ) {
        if (titleHeight > 0.dp) {
            Text(
                text = board.title,
                maxLines = 1,
                style = TextStyle(
                    color = fixed(board.titleColor),
                    fontSize = (14f * board.textScale.factor).sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.fillMaxWidth().height(titleHeight)
                    .padding(horizontal = gap / 2)
                    .clickable(actionStartActivity(openBoardIntent(context, board.id))),
            )
        }
        for (r in 0 until board.rows) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (c in 0 until board.cols) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(gap / 2)) {
                        val slot = data.slotAt(r, c)
                        if (slot != null && slot.enabled) {
                            SlotCell(slot, board, today, cellW, cellH, radius, density)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    slot: SlotEntity,
    board: BoardEntity,
    today: Long,
    width: Dp,
    height: Dp,
    radius: Dp,
    density: Float,
) {
    val context = LocalContext.current
    val look = SlotLogic.appearance(slot, board, today)
    val action: Action = if (slot.type == SlotType.VALUE) {
        actionStartActivity(ValueInputActivity.intent(context, slot.id))
    } else {
        actionRunCallback<SlotTapAction>(actionParametersOf(SlotTapAction.SlotId to slot.id))
    }

    val base = minOf(width.value, height.value)
    val labelSize = SlotLogic.labelSizeSp(slot, board, base)
    val textColor = fixed(look.textColor)
    val label = look.label.takeIf { it.isNotBlank() }
    val state = look.stateText

    val labelLines = SlotLogic.labelMaxLines(height.value, labelSize, state != null)
    val labelStyle = TextStyle(color = textColor, fontSize = labelSize.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    val stateStyle = if (label != null) {
        TextStyle(color = textColor, fontSize = (labelSize * 0.85f).sp, textAlign = TextAlign.Center)
    } else {
        TextStyle(color = textColor, fontSize = (labelSize * 1.2f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .fillBackground(look.fill, width, height, radius, density)
            .clickable(action)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null && state != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, maxLines = labelLines, style = labelStyle)
                Text(state, maxLines = 1, style = stateStyle)
            }
            label != null -> Text(label, maxLines = labelLines, style = labelStyle)
            state != null -> Text(state, maxLines = 1, style = stateStyle)
        }
    }
}

/**
 * Solid fills are a plain background, rounded natively on Android 12+. Gradients, and rounded
 * corners on older versions, are drawn into a bitmap sized like the cell.
 */
private fun GlanceModifier.fillBackground(fill: Fill, width: Dp, height: Dp, radius: Dp, density: Float): GlanceModifier {
    val useBitmap = fill is Fill.Gradient || (needsBitmapCorners && radius > 0.dp)
    val painted = if (useBitmap || fill !is Fill.Solid) {
        val bitmap = FillBitmaps.render(
            fill,
            (width.value * density).toInt(),
            (height.value * density).toInt(),
            radius.value * density,
        )
        background(ImageProvider(bitmap), ContentScale.FillBounds)
    } else {
        background(fixed(fill.color))
    }
    return painted.cornerRadius(radius)
}

fun openBoardIntent(context: Context, boardId: Long): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("dailytaps://board/$boardId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

class SlotTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val slotId = parameters[SlotId] ?: return
        context.repository.press(slotId, EventSource.WIDGET)
    }

    companion object {
        val SlotId = ActionParameters.Key<Long>("slotId")
    }
}
