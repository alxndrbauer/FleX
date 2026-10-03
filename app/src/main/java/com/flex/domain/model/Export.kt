package com.flex.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class ExportBlockRow(
    val startTime: LocalTime,
    val endTime: LocalTime?,
    val location: WorkLocation,
    val durationMinutes: Long
)

data class ExportDayRow(
    val date: LocalDate,
    val dayType: DayType?,
    val location: WorkLocation?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val grossMinutes: Long,
    val breakMinutes: Long,
    val netMinutes: Long,
    val targetMinutes: Int,
    val note: String?,
    val hasMultipleLocations: Boolean = false,
    val blocks: List<ExportBlockRow> = emptyList()
)

data class ExportData(
    val yearMonth: YearMonth,
    val rows: List<ExportDayRow>,
    val totalNetMinutes: Long,
    val totalTargetMinutes: Long,
    val settings: Settings
)
