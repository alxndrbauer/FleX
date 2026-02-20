package com.vrema.domain.model

import java.time.LocalDate
import java.time.YearMonth

sealed class TimeRange {
    data class Month(val yearMonth: YearMonth) : TimeRange()
    data class Year(val year: Int) : TimeRange()
    data class Custom(val start: LocalDate, val end: LocalDate) : TimeRange()
}
