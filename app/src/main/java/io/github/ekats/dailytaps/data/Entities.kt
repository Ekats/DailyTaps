package io.github.ekats.dailytaps.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A table of tappable slots. One board can be shown by any number of home screen widgets. */
@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val showTitle: Boolean = true,
    val rows: Int = 3,
    val cols: Int = 3,
    val background: Fill = Fill.Solid(Defaults.BOARD_BACKGROUND),
    val titleColor: Int = Defaults.TITLE_COLOR,
    val cornerRadiusDp: Int = 12,
    val spacingDp: Int = 4,
    val textScale: TextScale = TextScale.MEDIUM,
    /** Label size in sp for every slot without its own; 0 fits the label to the cell. */
    val labelSizeSp: Int = 0,
    val colHeaderMode: HeaderMode = HeaderMode.NONE,
    /** Custom column headers, index = column. Missing entries show nothing. */
    val colHeaders: List<String> = emptyList(),
    val showRowHeaders: Boolean = false,
    /** Row headers, index = row. */
    val rowHeaders: List<String> = emptyList(),
    /** When set, every slot returns to its first state (and counters and values clear) each new day. */
    val resetDaily: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One cell of a board. Slots outside the current rows x cols are kept, not deleted, so shrinking
 * a board and growing it back restores them.
 */
@Entity(
    tableName = "slots",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["boardId", "row", "col"], unique = true)],
)
data class SlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardId: Long,
    val row: Int,
    val col: Int,
    val label: String = "",
    val enabled: Boolean = true,
    /** Show the state name, count or value under the label. */
    val showStateText: Boolean = false,
    /** Label size in sp; 0 uses the board's setting. */
    val labelSizeSp: Int = 0,
    val type: SlotType = SlotType.STATES,
    val states: List<SlotStyle> = Defaults.statesFor(SlotType.STATES),
    val stateIndex: Int = 0,
    val count: Int = 0,
    val counterStep: Int = 1,
    /** 0 means no target: any non-zero count shows the second state. */
    val counterTarget: Int = 0,
    val lastValue: Double? = null,
    val valueUnit: String = "",
    /** Local epoch day of the last change, used for the daily reset. */
    val lastChangedDay: Long = 0,
)

/** One recorded interaction. Graphs are computed from these. */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("boardId", "timestamp"), Index("slotId", "timestamp")],
)
data class TapEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardId: Long,
    val slotId: Long,
    val timestamp: Long,
    val kind: EventKind,
    val source: EventSource,
    /** State index after the event. */
    val stateIndex: Int,
    /** Counter change caused by the event (0 for non-counter slots). */
    val delta: Int = 0,
    /** Counter value after the event. */
    val count: Int = 0,
    /** Logged number for VALUE slots. */
    val value: Double? = null,
)

/** Which board a home screen widget instance shows. */
@Entity(
    tableName = "widget_bindings",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("boardId")],
)
data class WidgetBindingEntity(
    @PrimaryKey val appWidgetId: Int,
    val boardId: Long,
)

data class BoardWithSlots(
    val board: BoardEntity,
    /** Every slot of the board, including the ones outside the visible grid. */
    val slots: List<SlotEntity>,
) {
    fun slotAt(row: Int, col: Int): SlotEntity? = slots.firstOrNull { it.row == row && it.col == col }

    val visibleSlots: List<SlotEntity>
        get() = slots.filter { it.row < board.rows && it.col < board.cols }
}
