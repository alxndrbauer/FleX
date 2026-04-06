package com.flex.calendar

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for VacationGroupUtil.groupConsecutiveRuns().
 * Verifies that consecutive vacation days are correctly grouped into runs,
 * bridging weekends (≤3-day gaps) but separating genuine breaks.
 */
class VacationGroupUtilTest {

    private fun day(year: Int, month: Int, day: Int, id: Long = day.toLong(), type: DayType = DayType.VACATION) =
        WorkDay(id = id, date = LocalDate.of(year, month, day), location = WorkLocation.OFFICE, dayType = type)

    // ========== Edge cases ==========

    @Test
    fun `empty list returns empty result`() {
        val result = VacationGroupUtil.groupConsecutiveRuns(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `single day returns one group of size 1`() {
        val result = VacationGroupUtil.groupConsecutiveRuns(listOf(day(2026, 4, 7)))
        assertThat(result).hasSize(1)
        assertThat(result[0]).hasSize(1)
    }

    // ========== Consecutive days ==========

    @Test
    fun `Mon-Fri consecutive days form single group`() {
        val days = (7..11).map { day(2026, 4, it) } // Apr 7 (Mon) – Apr 11 (Fri)
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(1)
        assertThat(result[0]).hasSize(5)
    }

    @Test
    fun `adjacent days (gap = 1) form single group`() {
        val days = listOf(day(2026, 4, 7), day(2026, 4, 8))
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(1)
    }

    // ========== Weekend bridging ==========

    @Test
    fun `Fri and Mon are bridged into one group (gap = 3)`() {
        // Apr 10 (Fri) → Apr 13 (Mon): gap = 3 days (Sat+Sun+Mon)
        val days = listOf(day(2026, 4, 10), day(2026, 4, 13))
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(1)
        assertThat(result[0]).hasSize(2)
    }

    @Test
    fun `two consecutive work weeks are bridged into one group`() {
        // Apr 7–11 and Apr 14–18: gap from Apr 11 (Fri) to Apr 14 (Mon) = 3 days
        val week1 = (7..11).map { day(2026, 4, it) }
        val week2 = (14..18).map { day(2026, 4, it, id = it.toLong() + 100) }
        val result = VacationGroupUtil.groupConsecutiveRuns(week1 + week2)
        assertThat(result).hasSize(1)
        assertThat(result[0]).hasSize(10)
    }

    // ========== Gap too large → separate groups ==========

    @Test
    fun `4-day gap creates two separate groups`() {
        // Apr 7 (Mon) → Apr 11 (Fri): gap = 4 days
        val days = listOf(day(2026, 4, 7), day(2026, 4, 11))
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(2)
    }

    @Test
    fun `two vacation weeks with work week between them form two groups`() {
        // Week 1: Apr 7–11, then gap of >3 days before Week 2: Apr 22–25
        val week1 = (7..11).map { day(2026, 4, it) }
        val week2 = (22..25).map { day(2026, 4, it, id = it.toLong() + 100) }
        val result = VacationGroupUtil.groupConsecutiveRuns(week1 + week2)
        assertThat(result).hasSize(2)
        assertThat(result[0]).hasSize(5)
        assertThat(result[1]).hasSize(4)
    }

    // ========== Ordering ==========

    @Test
    fun `unsorted input is sorted correctly within groups`() {
        val days = listOf(day(2026, 4, 9), day(2026, 4, 7), day(2026, 4, 8))
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(1)
        assertThat(result[0].map { it.date.dayOfMonth }).containsExactly(7, 8, 9).inOrder()
    }

    // ========== Month boundary ==========

    @Test
    fun `consecutive days across month boundary form single group`() {
        val days = listOf(
            day(2026, 3, 30),
            day(2026, 3, 31),
            day(2026, 4, 1),
        )
        val result = VacationGroupUtil.groupConsecutiveRuns(days)
        assertThat(result).hasSize(1)
        assertThat(result[0]).hasSize(3)
    }
}
