package io.github.ekats.dailytaps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BoardEntity::class, SlotEntity::class, TapEventEntity::class, WidgetBindingEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun slotDao(): SlotDao
    abstract fun eventDao(): EventDao
    abstract fun widgetBindingDao(): WidgetBindingDao

    companion object {
        /** v2: label size on boards and slots. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN labelSizeSp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE slots ADD COLUMN labelSizeSp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dailytaps.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
