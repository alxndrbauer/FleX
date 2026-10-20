package com.flex.widget

import com.flex.domain.model.WorkLocation
import java.time.LocalTime

data class WidgetState(
    val isClockRunning: Boolean,
    val isPaused: Boolean = false,
    val currentLocation: WorkLocation,
    val netMinutesToday: Long,
    val targetMinutesToday: Long,
    val flextimeBalanceMinutes: Long,
    val officeQuotaPercent: Double = 0.0,
    val targetQuotaPercent: Int = 40,
    val officeDays: Int = 0,
    val targetOfficeDays: Int = 8,
    val isQuotaMet: Boolean = false,
    val projectedEndTime: LocalTime? = null,
    val breakMinutesToday: Long = 0L
)

