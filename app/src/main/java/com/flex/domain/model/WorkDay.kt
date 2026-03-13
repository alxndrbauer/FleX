package com.flex.domain.model

import java.time.LocalDate

data class WorkDay(
    val id: Long = 0,
    val date: LocalDate,
    val location: WorkLocation = WorkLocation.OFFICE,
    val dayType: DayType = DayType.WORK,
    val isPlanned: Boolean = false,
    val note: String? = null,
    val timeBlocks: List<TimeBlock> = emptyList()
)
