package com.flex.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flex.data.local.FlexDatabase
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.data.repository.WorkDayRepositoryImpl
import com.flex.domain.events.DataChangeEventBus
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
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
    @Singleton
    fun provideWorkDayRepository(
        workDayDao: WorkDayDao,
        timeBlockDao: TimeBlockDao
    ): WorkDayRepository = WorkDayRepositoryImpl(workDayDao, timeBlockDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsDao: SettingsDao,
        quotaRuleDao: QuotaRuleDao
    ): SettingsRepository = SettingsRepositoryImpl(settingsDao, quotaRuleDao)

    @Provides
    @Singleton
    fun provideDataChangeEventBus(): DataChangeEventBus = DataChangeEventBus()
}
