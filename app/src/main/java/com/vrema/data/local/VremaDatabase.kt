package com.vrema.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vrema.data.local.dao.QuotaRuleDao
import com.vrema.data.local.dao.SettingsDao
import com.vrema.data.local.dao.TimeBlockDao
import com.vrema.data.local.dao.WorkDayDao
import com.vrema.data.local.entity.QuotaRuleEntity
import com.vrema.data.local.entity.SettingsEntity
import com.vrema.data.local.entity.TimeBlockEntity
import com.vrema.data.local.entity.WorkDayEntity

@Database(
    entities = [
        WorkDayEntity::class,
        TimeBlockEntity::class,
        SettingsEntity::class,
        QuotaRuleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class VremaDatabase : RoomDatabase() {
    abstract fun workDayDao(): WorkDayDao
    abstract fun timeBlockDao(): TimeBlockDao
    abstract fun settingsDao(): SettingsDao
    abstract fun quotaRuleDao(): QuotaRuleDao
}
