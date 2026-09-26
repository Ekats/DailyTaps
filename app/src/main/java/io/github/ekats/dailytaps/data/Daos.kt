package io.github.ekats.dailytaps.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY sortOrder, id")
    fun observeBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards ORDER BY sortOrder, id")
    suspend fun boards(): List<BoardEntity>

    @Query("SELECT * FROM boards WHERE id = :id")
    fun observeBoard(id: Long): Flow<BoardEntity?>

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun board(id: Long): BoardEntity?

    @Insert
    suspend fun insert(board: BoardEntity): Long

    @Update
    suspend fun update(board: BoardEntity)

    @Delete
    suspend fun delete(board: BoardEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM boards")
    suspend fun nextSortOrder(): Int
}

@Dao
interface SlotDao {
    @Query("SELECT * FROM slots WHERE boardId = :boardId ORDER BY row, col")
    fun observeSlots(boardId: Long): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slots ORDER BY boardId, row, col")
    fun observeAllSlots(): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slots WHERE boardId = :boardId ORDER BY row, col")
    suspend fun slots(boardId: Long): List<SlotEntity>

    @Query("SELECT * FROM slots WHERE id = :id")
    suspend fun slot(id: Long): SlotEntity?

    @Query("SELECT * FROM slots WHERE id = :id")
    fun observeSlot(id: Long): Flow<SlotEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(slots: List<SlotEntity>)

    @Update
    suspend fun update(slot: SlotEntity)

    @Update
    suspend fun updateAll(slots: List<SlotEntity>)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: TapEventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM events WHERE timestamp >= :from ORDER BY timestamp")
    fun observeSince(from: Long): Flow<List<TapEventEntity>>

    @Query("SELECT * FROM events WHERE boardId = :boardId AND timestamp >= :from ORDER BY timestamp")
    fun observeBoardSince(boardId: Long, from: Long): Flow<List<TapEventEntity>>

    @Query("SELECT * FROM events WHERE (:boardId IS NULL OR boardId = :boardId) ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(boardId: Long?, limit: Int): Flow<List<TapEventEntity>>

    @Query("SELECT * FROM events ORDER BY timestamp")
    suspend fun all(): List<TapEventEntity>

    @Query("SELECT boardId, COUNT(*) AS taps FROM events WHERE timestamp >= :from AND kind != 'ADJUST' GROUP BY boardId")
    fun observeTapCountsSince(from: Long): Flow<List<BoardTapCount>>
}

data class BoardTapCount(val boardId: Long, val taps: Int)

@Dao
interface WidgetBindingDao {
    @Upsert
    suspend fun upsert(binding: WidgetBindingEntity)

    @Query("DELETE FROM widget_bindings WHERE appWidgetId IN (:ids)")
    suspend fun delete(ids: List<Int>)

    @Query("SELECT boardId FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    fun observeBoardId(appWidgetId: Int): Flow<Long?>

    @Query("SELECT boardId FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun boardId(appWidgetId: Int): Long?

    @Query("SELECT * FROM widget_bindings")
    fun observeAll(): Flow<List<WidgetBindingEntity>>
}
