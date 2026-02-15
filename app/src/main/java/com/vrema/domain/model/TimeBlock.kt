package com.vrema.domain.model

import java.time.LocalTime

data class TimeBlock(
    val id: Long = 0,
    val workDayId: Long,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val isDuration: Boolean = false
)
