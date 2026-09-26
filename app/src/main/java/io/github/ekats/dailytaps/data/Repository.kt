package io.github.ekats.dailytaps.data

import androidx.room.withTransaction
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.SlotLogic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * All reads and writes go through here. [onChanged] runs after every write that affects what a
 * widget shows, so home screen widgets stay in step with the app.
 */
class Repository(
    private val db: AppDatabase,
    private val onChanged: suspend () -> Unit,
) {
    private val boards = db.boardDao()
    private val slots = db.slotDao()
    private val events = db.eventDao()
    private val bindings = db.widgetBindingDao()

    fun observeBoards(): Flow<List<BoardEntity>> = boards.observeBoards()

    fun observeBoard(id: Long): Flow<BoardWithSlots?> =
        combine(boards.observeBoard(id), slots.observeSlots(id)) { b, s -> b?.let { BoardWithSlots(it, s) } }

    fun observeAllBoards(): Flow<List<BoardWithSlots>> =
        combine(boards.observeBoards(), slots.observeAllSlots()) { bs, ss ->
            val bySlot = ss.groupBy { it.boardId }
            bs.map { BoardWithSlots(it, bySlot[it.id].orEmpty()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWidgetBoard(appWidgetId: Int): Flow<BoardWithSlots?> =
        bindings.observeBoardId(appWidgetId).flatMapLatest { id ->
            if (id == null) flowOf(null) else observeBoard(id)
        }

    suspend fun widgetBoard(appWidgetId: Int): BoardWithSlots? =
        bindings.boardId(appWidgetId)?.let { board(it) }

    suspend fun board(id: Long): BoardWithSlots? =
        boards.board(id)?.let { BoardWithSlots(it, slots.slots(id)) }

    fun observeSlot(id: Long): Flow<SlotEntity?> = slots.observeSlot(id)

    suspend fun slot(id: Long): SlotEntity? = slots.slot(id)

    fun observeEventsSince(boardId: Long?, from: Long): Flow<List<TapEventEntity>> =
        if (boardId == null) events.observeSince(from) else events.observeBoardSince(boardId, from)

    fun observeRecentEvents(boardId: Long?, limit: Int = 100): Flow<List<TapEventEntity>> =
        events.observeRecent(boardId, limit)

    fun observeTapCountsSince(from: Long): Flow<List<BoardTapCount>> = events.observeTapCountsSince(from)

    fun observeWidgetBindings(): Flow<List<WidgetBindingEntity>> = bindings.observeAll()

    suspend fun allEvents(): List<TapEventEntity> = events.all()

    suspend fun allBoards(): List<BoardWithSlots> = boards.boards().map { BoardWithSlots(it, slots.slots(it.id)) }

    // region Boards

    suspend fun createBoard(title: String, rows: Int, cols: Int): Long {
        val id = db.withTransaction {
            val boardId = boards.insert(
                BoardEntity(
                    title = title,
                    rows = rows.coerceIn(MIN_GRID, MAX_GRID),
                    cols = cols.coerceIn(MIN_GRID, MAX_GRID),
                    sortOrder = boards.nextSortOrder(),
                ),
            )
            ensureSlots(boardId, rows, cols)
            boardId
        }
        onChanged()
        return id
    }

    suspend fun updateBoard(board: BoardEntity) {
        val b = board.copy(rows = board.rows.coerceIn(MIN_GRID, MAX_GRID), cols = board.cols.coerceIn(MIN_GRID, MAX_GRID))
        db.withTransaction {
            boards.update(b)
            ensureSlots(b.id, b.rows, b.cols)
        }
        onChanged()
    }

    suspend fun deleteBoard(board: BoardEntity) {
        boards.delete(board)
        onChanged()
    }

    suspend fun duplicateBoard(id: Long): Long? {
        val src = board(id) ?: return null
        val newId = db.withTransaction {
            val boardId = boards.insert(
                src.board.copy(id = 0, title = src.board.title + " (copy)", sortOrder = boards.nextSortOrder(), createdAt = System.currentTimeMillis()),
            )
            slots.insertAll(src.slots.map { it.copy(id = 0, boardId = boardId, stateIndex = 0, count = 0, lastValue = null, lastChangedDay = 0) })
            boardId
        }
        onChanged()
        return newId
    }

    suspend fun moveBoard(id: Long, up: Boolean) {
        val list = boards.boards().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        db.withTransaction { list.forEachIndexed { index, b -> boards.update(b.copy(sortOrder = index)) } }
    }

    /** Creates any missing slots for a rows x cols grid. Existing slots are never removed. */
    private suspend fun ensureSlots(boardId: Long, rows: Int, cols: Int) {
        val existing = slots.slots(boardId).map { it.row to it.col }.toSet()
        val template = slots.slots(boardId).firstOrNull()
        val missing = buildList {
            for (r in 0 until rows) for (c in 0 until cols) {
                if (r to c !in existing) {
                    add(
                        SlotEntity(
                            boardId = boardId,
                            row = r,
                            col = c,
                            // New cells copy the look of the first one so a restyled board stays consistent.
                            states = template?.takeIf { it.type == SlotType.STATES }?.states ?: Defaults.statesFor(SlotType.STATES),
                            showStateText = template?.showStateText ?: false,
                        ),
                    )
                }
            }
        }
        if (missing.isNotEmpty()) slots.insertAll(missing)
    }

    // endregion

    // region Slots

    /**
     * Saves a slot's configuration from the editor. The editor works on a draft, so the live
     * progress (state, count, value) is taken from the database, not the draft, unless the type
     * changed, which starts the slot over.
     */
    suspend fun updateSlotConfig(draft: SlotEntity) {
        db.withTransaction {
            val current = slots.slot(draft.id) ?: return@withTransaction
            val states = draft.states.ifEmpty { Defaults.statesFor(draft.type) }
            val merged = if (current.type == draft.type) {
                draft.copy(
                    states = states,
                    stateIndex = current.stateIndex.coerceIn(0, states.lastIndex),
                    count = current.count,
                    lastValue = current.lastValue,
                    lastChangedDay = current.lastChangedDay,
                )
            } else {
                draft.copy(states = states, stateIndex = 0, count = 0, lastValue = null)
            }
            slots.update(merged)
        }
        onChanged()
    }

    /** Sets several labels at once (the label table's row/column repeat). */
    suspend fun setLabels(labels: Map<Long, String>) {
        db.withTransaction {
            labels.forEach { (id, label) ->
                slots.slot(id)?.let { if (it.label != label) slots.update(it.copy(label = label)) }
            }
        }
        onChanged()
    }

    /** Copies colors (and text settings) of [source] to every slot of its board with the same type. */
    suspend fun applyStyleToBoard(source: SlotEntity) {
        val all = slots.slots(source.boardId)
        slots.updateAll(
            all.filter { it.id != source.id && it.type == source.type }.map {
                it.copy(
                    states = source.states,
                    stateIndex = it.stateIndex.coerceIn(0, source.states.lastIndex),
                    showStateText = source.showStateText,
                )
            },
        )
        onChanged()
    }

    /** A tap from the widget or the in-app grid. VALUE slots need [recordValue] instead. */
    suspend fun press(slotId: Long, source: EventSource): SlotEntity? {
        val result = db.withTransaction {
            val slot = slots.slot(slotId) ?: return@withTransaction null
            if (!slot.enabled || slot.type == SlotType.VALUE) return@withTransaction null
            val board = boards.board(slot.boardId) ?: return@withTransaction null
            val today = Days.today()
            val current = SlotLogic.effective(slot, board, today)
            val next = SlotLogic.pressed(current, today)
            slots.update(next)
            events.insert(
                TapEventEntity(
                    boardId = slot.boardId,
                    slotId = slot.id,
                    timestamp = System.currentTimeMillis(),
                    kind = EventKind.PRESS,
                    source = source,
                    stateIndex = next.stateIndex,
                    delta = next.count - current.count,
                    count = next.count,
                ),
            )
            next
        }
        onChanged()
        return result
    }

    suspend fun recordValue(slotId: Long, value: Double, source: EventSource) {
        db.withTransaction {
            val slot = slots.slot(slotId) ?: return@withTransaction
            val next = SlotLogic.withValue(slot, value, Days.today())
            slots.update(next)
            events.insert(
                TapEventEntity(
                    boardId = slot.boardId,
                    slotId = slot.id,
                    timestamp = System.currentTimeMillis(),
                    kind = EventKind.VALUE,
                    source = source,
                    stateIndex = next.stateIndex,
                    value = value,
                ),
            )
        }
        onChanged()
    }

    /** Manual corrections from the app. Recorded as ADJUST so they are not counted as taps. */
    suspend fun adjust(slotId: Long, change: (SlotEntity, Long) -> SlotEntity) {
        db.withTransaction {
            val slot = slots.slot(slotId) ?: return@withTransaction
            val board = boards.board(slot.boardId) ?: return@withTransaction
            val today = Days.today()
            val current = SlotLogic.effective(slot, board, today)
            val next = change(current, today)
            slots.update(next)
            events.insert(
                TapEventEntity(
                    boardId = slot.boardId,
                    slotId = slot.id,
                    timestamp = System.currentTimeMillis(),
                    kind = EventKind.ADJUST,
                    source = EventSource.APP,
                    stateIndex = next.stateIndex,
                    delta = next.count - current.count,
                    count = next.count,
                ),
            )
        }
        onChanged()
    }

    /**
     * Removes one history entry and puts the slot back to what its remaining history says: the
     * state of the last remaining entry, the last remaining value, and for counters the running
     * total without the deleted amount (later entries are corrected too, so graphs agree).
     */
    suspend fun deleteEvent(id: Long) {
        db.withTransaction {
            val event = events.get(id) ?: return@withTransaction
            events.delete(id)
            val slot = slots.slot(event.slotId) ?: return@withTransaction
            val board = boards.board(slot.boardId) ?: return@withTransaction

            if (slot.type == SlotType.COUNTER && event.delta != 0) {
                // On a daily-reset board a tap only ever counted toward its own day.
                val before = if (board.resetDaily) Days.startOfDayMillis(Days.of(event.timestamp) + 1) else Long.MAX_VALUE
                events.shiftCounts(slot.id, event.timestamp, before, event.delta)
            }

            val last = events.lastForSlot(slot.id)
            val lastValue = events.lastValueForSlot(slot.id)?.value
            val day = last?.let { Days.of(it.timestamp) } ?: 0L
            val restored = when (slot.type) {
                SlotType.STATES -> slot.copy(stateIndex = (last?.stateIndex ?: 0).coerceIn(0, slot.states.lastIndex), lastChangedDay = day)
                SlotType.COUNTER -> SlotLogic.withCount(slot, last?.count ?: 0, day)
                SlotType.VALUE -> slot.copy(lastValue = lastValue, stateIndex = if (lastValue != null) 1 else 0, lastChangedDay = day)
            }
            slots.update(restored)
        }
        onChanged()
    }

    // endregion

    // region Widgets

    suspend fun bindWidget(appWidgetId: Int, boardId: Long) {
        bindings.upsert(WidgetBindingEntity(appWidgetId, boardId))
        onChanged()
    }

    suspend fun unbindWidgets(ids: List<Int>) {
        bindings.delete(ids)
    }

    // endregion
}
