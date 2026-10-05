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
    version = 5,
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

        /** v3: column and row headers. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN colHeaderMode TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE boards ADD COLUMN colHeaders TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE boards ADD COLUMN showRowHeaders INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE boards ADD COLUMN rowHeaders TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * v4: reset schedules. The daily switch becomes a DAILY schedule at midnight. Slots get
         * lastChangedAt: noon UTC of their old epoch day lands inside that local day for any time
         * zone within ±11 h, which is all the reset comparison needs.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN resetMode TEXT NOT NULL DEFAULT 'NEVER'")
                db.execSQL("ALTER TABLE boards ADD COLUMN resetMinute INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boards ADD COLUMN resetWeekday INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boards ADD COLUMN resetMonthDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE boards SET resetMode = 'DAILY' WHERE resetDaily != 0")
                db.execSQL("ALTER TABLE slots ADD COLUMN lastChangedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE slots SET lastChangedAt = lastChangedDay * 86400000 + 43200000 WHERE lastChangedDay > 0")
            }
        }

        /** v5: no reset at the ambiguous 00:00; it becomes 00:01. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE boards SET resetMinute = 1 WHERE resetMinute = 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dailytaps.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
