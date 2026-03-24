package com.flex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.QuotaRuleEntity
import com.flex.data.local.entity.SettingsEntity
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity

@Database(
    entities = [
        WorkDayEntity::class,
        TimeBlockEntity::class,
        SettingsEntity::class,
        QuotaRuleEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class FlexDatabase : RoomDatabase() {
    abstract fun workDayDao(): WorkDayDao
    abstract fun timeBlockDao(): TimeBlockDao
    abstract fun settingsDao(): SettingsDao
    abstract fun quotaRuleDao(): QuotaRuleDao
}
