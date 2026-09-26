package io.github.ekats.dailytaps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BoardEntity::class, SlotEntity::class, TapEventEntity::class, WidgetBindingEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun slotDao(): SlotDao
    abstract fun eventDao(): EventDao
    abstract fun widgetBindingDao(): WidgetBindingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dailytaps.db").build()
    }
}
