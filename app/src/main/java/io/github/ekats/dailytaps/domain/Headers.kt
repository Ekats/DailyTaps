package io.github.ekats.dailytaps.domain

import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.HeaderMode
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Column and row headers as drawn by the widget and the app. */
object Headers {
    fun columns(board: BoardEntity, locale: Locale): List<String>? = when (board.colHeaderMode) {
        HeaderMode.NONE -> null
        HeaderMode.WEEKDAYS -> (0 until board.cols).map {
            DayOfWeek.MONDAY.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale)
        }
        HeaderMode.CUSTOM -> (0 until board.cols).map { board.colHeaders.getOrElse(it) { "" } }
    }

    fun rows(board: BoardEntity): List<String>? =
        if (board.showRowHeaders) (0 until board.rows).map { board.rowHeaders.getOrElse(it) { "" } } else null

    /** Header text size in sp for a board. */
    fun textSizeSp(board: BoardEntity): Float = 11f * board.textScale.factor

    /** Height of the column header strip in dp. */
    fun columnHeightDp(board: BoardEntity): Float = 18f * board.textScale.factor

    /** Width of the row header column in dp: enough for about eight characters. */
    fun rowWidthDp(board: BoardEntity): Float = 56f * board.textScale.factor
}
