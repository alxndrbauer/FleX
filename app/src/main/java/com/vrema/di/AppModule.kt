package com.vrema.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vrema.data.local.VremaDatabase
import com.vrema.data.local.dao.QuotaRuleDao
import com.vrema.data.local.dao.SettingsDao
import com.vrema.data.local.dao.TimeBlockDao
import com.vrema.data.local.dao.WorkDayDao
import com.vrema.data.repository.SettingsRepositoryImpl
import com.vrema.data.repository.WorkDayRepositoryImpl
import com.vrema.domain.events.DataChangeEventBus
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.repository.WorkDayRepository
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VremaDatabase {
        return Room.databaseBuilder(
            context,
            VremaDatabase::class.java,
            "vrema_database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    }

    @Provides
    fun provideWorkDayDao(database: VremaDatabase): WorkDayDao = database.workDayDao()

    @Provides
    fun provideTimeBlockDao(database: VremaDatabase): TimeBlockDao = database.timeBlockDao()

    @Provides
    fun provideSettingsDao(database: VremaDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideQuotaRuleDao(database: VremaDatabase): QuotaRuleDao = database.quotaRuleDao()

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
