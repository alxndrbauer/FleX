package com.flex.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import com.flex.data.local.entity.SettingsEntity
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FlexDatabaseIntegrationTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayDao: WorkDayDao
    private lateinit var timeBlockDao: TimeBlockDao
    private lateinit var settingsDao: SettingsDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FlexDatabase::class.java
        ).build()

        workDayDao = database.workDayDao()
        timeBlockDao = database.timeBlockDao()
        settingsDao = database.settingsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun database_creation_and_version() {
        assertThat(database).isNotNull()
        assertThat(database.openHelper.readableDatabase.version).isEqualTo(14)
    }

    @Test
    fun settings_crud_operations() = runTest {
        // Create
        val settings = SettingsEntity(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 120,
            initialOvertimeMinutes = 60,
            annualVacationDays = 30,
            carryOverVacationDays = 5,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsDao.insert(settings)

        // Read
        val retrieved = settingsDao.getSettings().first()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.initialFlextimeMinutes).isEqualTo(120)
        assertThat(retrieved?.initialOvertimeMinutes).isEqualTo(60)

        // Update
        val updated = settings.copy(initialFlextimeMinutes = 180)
        settingsDao.insert(updated)

        val retrievedUpdated = settingsDao.getSettings().first()
        assertThat(retrievedUpdated?.initialFlextimeMinutes).isEqualTo(180)
    }

    @Test
    fun workDay_crud_operations() = runTest {
        // Create
        val workDay = WorkDayEntity(
            id = 0,
            date = "2026-02-15",
            location = "OFFICE",
            dayType = "WORK",
            isPlanned = false,
            note = "Test day"
        )
        val workDayId = workDayDao.insert(workDay)
        assertThat(workDayId).isGreaterThan(0)

        // Read
        val retrieved = workDayDao.getWorkDayByDate("2026-02-15").first()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.dayType).isEqualTo("WORK")
        assertThat(retrieved?.note).isEqualTo("Test day")

        // Update
        val updated = retrieved!!.copy(note = "Updated note")
        workDayDao.update(updated)

        val retrievedUpdated = workDayDao.getWorkDayByDate("2026-02-15").first()
        assertThat(retrievedUpdated?.note).isEqualTo("Updated note")

        // Delete
        workDayDao.delete(retrievedUpdated!!)
        val afterDelete = workDayDao.getWorkDayByDate("2026-02-15").first()
        assertThat(afterDelete).isNull()
    }

    @Test
    fun workDay_with_timeBlocks_relationship() = runTest {
        // Insert work day
        val workDay = WorkDayEntity(
            id = 0,
            date = "2026-02-15",
            location = "OFFICE",
            dayType = "WORK",
            isPlanned = false,
            note = null
        )
        val workDayId = workDayDao.insert(workDay)

        // Insert time blocks
        val timeBlock1 = TimeBlockEntity(
            id = 0,
            workDayId = workDayId,
            startTime = "08:00:00",
            endTime = "12:00:00",
            isDuration = false
        )
        val timeBlock2 = TimeBlockEntity(
            id = 0,
            workDayId = workDayId,
            startTime = "13:00:00",
            endTime = "17:30:00",
            isDuration = false
        )

        val block1Id = timeBlockDao.insert(timeBlock1)
        val block2Id = timeBlockDao.insert(timeBlock2)

        assertThat(block1Id).isGreaterThan(0)
        assertThat(block2Id).isGreaterThan(0)

        // Retrieve time blocks for the work day
        val blocks = timeBlockDao.getTimeBlocksForDay(workDayId).first()
        assertThat(blocks).hasSize(2)
        assertThat(blocks[0].startTime).isEqualTo("08:00:00")
        assertThat(blocks[1].startTime).isEqualTo("13:00:00")

        // Test cascade delete
        val retrievedWorkDay = workDayDao.getWorkDayByDate("2026-02-15").first()
        workDayDao.delete(retrievedWorkDay!!)

        val blocksAfterDelete = timeBlockDao.getTimeBlocksForDay(workDayId).first()
        assertThat(blocksAfterDelete).isEmpty()
    }

    @Test
    fun workDay_date_unique_constraint() = runTest {
        val workDay1 = WorkDayEntity(
            id = 0,
            date = "2026-02-15",
            location = "OFFICE",
            dayType = "WORK",
            isPlanned = false,
            note = "First"
        )
        val id1 = workDayDao.insert(workDay1)

        // Insert with same date should replace (due to OnConflictStrategy.REPLACE)
        val workDay2 = WorkDayEntity(
            id = 0,
            date = "2026-02-15",
            location = "HOME_OFFICE",
            dayType = "WORK",
            isPlanned = false,
            note = "Second"
        )
        val id2 = workDayDao.insert(workDay2)

        val retrieved = workDayDao.getWorkDayByDate("2026-02-15").first()
        assertThat(retrieved?.location).isEqualTo("HOME_OFFICE")
        assertThat(retrieved?.note).isEqualTo("Second")
    }

    @Test
    fun workDay_date_range_query() = runTest {
        // Insert multiple work days
        workDayDao.insert(WorkDayEntity(0, "2026-02-10", "OFFICE", "WORK", false, null))
        workDayDao.insert(WorkDayEntity(0, "2026-02-15", "HOME_OFFICE", "WORK", false, null))
        workDayDao.insert(WorkDayEntity(0, "2026-02-20", "OFFICE", "WORK", false, null))
        workDayDao.insert(WorkDayEntity(0, "2026-03-05", "OFFICE", "WORK", false, null))

        // Query February only
        val februaryDays = workDayDao.getWorkDaysBetween("2026-02-01", "2026-02-28").first()
        assertThat(februaryDays).hasSize(3)
        assertThat(februaryDays.map { it.date }).containsExactly(
            "2026-02-10", "2026-02-15", "2026-02-20"
        )
    }

    @Test
    fun timeBlock_ordering() = runTest {
        val workDay = WorkDayEntity(0, "2026-02-15", "OFFICE", "WORK", false, null)
        val workDayId = workDayDao.insert(workDay)

        // Insert in random order
        timeBlockDao.insert(TimeBlockEntity(0, workDayId, "13:00:00", "17:00:00", false))
        timeBlockDao.insert(TimeBlockEntity(0, workDayId, "08:00:00", "12:00:00", false))
        timeBlockDao.insert(TimeBlockEntity(0, workDayId, "18:00:00", "19:30:00", false))

        // Should be sorted by startTime
        val blocks = timeBlockDao.getTimeBlocksForDay(workDayId).first()
        assertThat(blocks).hasSize(3)
        assertThat(blocks[0].startTime).isEqualTo("08:00:00")
        assertThat(blocks[1].startTime).isEqualTo("13:00:00")
        assertThat(blocks[2].startTime).isEqualTo("18:00:00")
    }

    @Test
    fun settings_persistence_across_queries() = runTest {
        val settings = SettingsEntity(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 240,
            initialOvertimeMinutes = 120,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsDao.insert(settings)

        // Close and reopen database to simulate app restart
        database.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlexDatabase::class.java).build()
        settingsDao = database.settingsDao()

        // In-memory DB won't persist, but we test the flow behavior
        val retrieved = settingsDao.getSettings().first()
        // In-memory DB is empty after restart
        assertThat(retrieved).isNull()
    }

    @Test
    fun workDay_confirmPlannedDays() = runTest {
        // Insert planned and non-planned days
        workDayDao.insert(WorkDayEntity(0, "2026-02-10", "OFFICE", "WORK", true, null))
        workDayDao.insert(WorkDayEntity(0, "2026-02-15", "HOME_OFFICE", "WORK", true, null))
        workDayDao.insert(WorkDayEntity(0, "2026-02-20", "OFFICE", "WORK", false, null))
        workDayDao.insert(WorkDayEntity(0, "2026-03-05", "OFFICE", "WORK", true, null))

        // Confirm planned days for February
        workDayDao.confirmPlannedDays("2026-02-01", "2026-02-28")

        val allDays = workDayDao.getAllWorkDays()
        val feb10 = allDays.find { it.date == "2026-02-10" }
        val feb15 = allDays.find { it.date == "2026-02-15" }
        val feb20 = allDays.find { it.date == "2026-02-20" }
        val mar05 = allDays.find { it.date == "2026-03-05" }

        assertThat(feb10?.isPlanned).isFalse()
        assertThat(feb15?.isPlanned).isFalse()
        assertThat(feb20?.isPlanned).isFalse()
        assertThat(mar05?.isPlanned).isTrue() // Not in February range
    }

    @Test
    fun timeBlock_deleteAllForDay() = runTest {
        val workDay1 = WorkDayEntity(0, "2026-02-15", "OFFICE", "WORK", false, null)
        val workDay2 = WorkDayEntity(0, "2026-02-16", "OFFICE", "WORK", false, null)
        val workDayId1 = workDayDao.insert(workDay1)
        val workDayId2 = workDayDao.insert(workDay2)

        // Insert blocks for both days
        timeBlockDao.insert(TimeBlockEntity(0, workDayId1, "08:00:00", "12:00:00", false))
        timeBlockDao.insert(TimeBlockEntity(0, workDayId1, "13:00:00", "17:00:00", false))
        timeBlockDao.insert(TimeBlockEntity(0, workDayId2, "09:00:00", "17:00:00", false))

        // Delete blocks for day 1
        timeBlockDao.deleteAllForDay(workDayId1)

        val blocks1 = timeBlockDao.getTimeBlocksForDay(workDayId1).first()
        val blocks2 = timeBlockDao.getTimeBlocksForDay(workDayId2).first()

        assertThat(blocks1).isEmpty()
        assertThat(blocks2).hasSize(1)
    }
}
