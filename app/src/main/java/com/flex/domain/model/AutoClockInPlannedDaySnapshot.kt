package com.flex.domain.model

import java.time.LocalDate

data class AutoClockInPlannedDaySnapshot(
    val workDayId: Long,
    val date: LocalDate,
    val wasPlanned: Boolean,
    val durationBlocks: List<TimeBlock>
)
