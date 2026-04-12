package com.flex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flex.data.local.dao.CalendarEventDao
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.CalendarEventEntity
import com.flex.data.local.entity.HolidayCacheEntity
import com.flex.data.local.entity.QuotaRuleEntity
import com.flex.data.local.entity.SettingsEntity
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity

@Database(
    entities = [
        WorkDayEntity::class,
        TimeBlockEntity::class,
        SettingsEntity::class,
        QuotaRuleEntity::class,
        CalendarEventEntity::class,
        HolidayCacheEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class FlexDatabase : RoomDatabase() {
    abstract fun workDayDao(): WorkDayDao
    abstract fun timeBlockDao(): TimeBlockDao
    abstract fun settingsDao(): SettingsDao
    abstract fun quotaRuleDao(): QuotaRuleDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun holidayCacheDao(): HolidayCacheDao

    companion object {
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS holiday_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        name TEXT NOT NULL,
                        federalState TEXT NOT NULL,
                        year INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
