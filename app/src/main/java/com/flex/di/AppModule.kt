package com.flex.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flex.data.holidays.HolidayApiService
import com.flex.data.local.FlexDatabase
import com.flex.data.local.dao.CalendarEventDao
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.calendar.CalendarSyncService
import com.flex.data.repository.HolidayRepositoryImpl
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.data.repository.WorkDayRepositoryImpl
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.repository.HolidayRepository
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quota_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                validFrom TEXT NOT NULL,
                officeQuotaPercent INTEGER NOT NULL,
                officeQuotaMinDays INTEGER NOT NULL
            )
        """)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_blocks ADD COLUMN isDuration INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN monthlyWorkMinutes INTEGER NOT NULL DEFAULT 9252")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN initialOvertimeMinutes INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_blocks ADD COLUMN location TEXT NOT NULL DEFAULT 'OFFICE'")
        db.execSQL("""
            UPDATE time_blocks SET location = (
                SELECT work_days.location FROM work_days
                WHERE work_days.id = time_blocks.workDayId
            )
        """)
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN geofenceEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE settings ADD COLUMN geofenceLat REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE settings ADD COLUMN geofenceLon REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE settings ADD COLUMN geofenceRadiusMeters REAL NOT NULL DEFAULT 150.0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN geofenceAddress TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN wifiAutoStampEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE settings ADD COLUMN wifiSsid TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN breakWarningEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN workTimerNotificationEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN federalState TEXT NOT NULL DEFAULT 'HH'")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarEventNoAlarm INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarEventPrefix TEXT NOT NULL DEFAULT 'FleX'")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarSyncEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarId INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarSyncTypes TEXT NOT NULL DEFAULT 'WORK,VACATION,SICK_DAY,FLEX_DAY,SPECIAL_VACATION,OVERTIME_DAY,SATURDAY_BONUS'")
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarSyncOffice INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE settings ADD COLUMN calendarSyncHomeOffice INTEGER NOT NULL DEFAULT 1")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calendar_events (
                workDayId INTEGER PRIMARY KEY NOT NULL,
                calendarEventId INTEGER NOT NULL,
                calendarId INTEGER NOT NULL
            )
        """)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlexDatabase {
        return Room.databaseBuilder(
            context,
            FlexDatabase::class.java,
            "flex_database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, FlexDatabase.MIGRATION_15_16).build()
    }

    @Provides
    fun provideWorkDayDao(database: FlexDatabase): WorkDayDao = database.workDayDao()

    @Provides
    fun provideTimeBlockDao(database: FlexDatabase): TimeBlockDao = database.timeBlockDao()

    @Provides
    fun provideSettingsDao(database: FlexDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideQuotaRuleDao(database: FlexDatabase): QuotaRuleDao = database.quotaRuleDao()

    @Provides
    fun provideCalendarEventDao(database: FlexDatabase): CalendarEventDao = database.calendarEventDao()

    @Provides
    fun provideHolidayCacheDao(database: FlexDatabase): HolidayCacheDao = database.holidayCacheDao()

    @Provides
    @Singleton
    fun provideWorkDayRepository(
        workDayDao: WorkDayDao,
        timeBlockDao: TimeBlockDao,
        calendarSyncService: CalendarSyncService,
        settingsRepository: SettingsRepository
    ): WorkDayRepository = WorkDayRepositoryImpl(workDayDao, timeBlockDao, calendarSyncService, settingsRepository)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsDao: SettingsDao,
        quotaRuleDao: QuotaRuleDao
    ): SettingsRepository = SettingsRepositoryImpl(settingsDao, quotaRuleDao)

    @Provides
    @Singleton
    fun provideDataChangeEventBus(): DataChangeEventBus = DataChangeEventBus()

    @Provides
    @Singleton
    fun provideHolidayApiService(): HolidayApiService = HolidayApiService()

    @Provides
    @Singleton
    fun provideHolidayRepository(
        apiService: HolidayApiService,
        holidayCacheDao: HolidayCacheDao
    ): HolidayRepository = HolidayRepositoryImpl(apiService, holidayCacheDao)
}
