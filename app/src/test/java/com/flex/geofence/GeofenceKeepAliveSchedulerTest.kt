package com.flex.geofence

import com.flex.support.TestDates
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class GeofenceKeepAliveSchedulerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val defaultStartTime = LocalTime.of(8, 0) // target = 07:15

    @Test
    fun `computeMorningCheckDelayMinutes schedules today when before target time`() {
        val now = ZonedDateTime.of(TestDates.FRIDAY, LocalTime.of(6, 0), zone)

        val delay = GeofenceKeepAliveScheduler.computeMorningCheckDelayMinutes(now, defaultStartTime)

        assertThat(delay).isEqualTo(75L)
    }

    @Test
    fun `computeMorningCheckDelayMinutes schedules tomorrow when exactly at target time`() {
        val now = ZonedDateTime.of(TestDates.FRIDAY, LocalTime.of(7, 15), zone)

        val delay = GeofenceKeepAliveScheduler.computeMorningCheckDelayMinutes(now, defaultStartTime)

        assertThat(delay).isEqualTo(1440L)
    }

    @Test
    fun `computeMorningCheckDelayMinutes schedules tomorrow when after target time`() {
        val now = ZonedDateTime.of(TestDates.FRIDAY, LocalTime.of(9, 0), zone)

        val delay = GeofenceKeepAliveScheduler.computeMorningCheckDelayMinutes(now, defaultStartTime)

        assertThat(delay).isEqualTo(1335L)
    }
}
