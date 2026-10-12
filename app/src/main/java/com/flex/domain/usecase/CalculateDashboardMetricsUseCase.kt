package com.flex.domain.usecase

import com.flex.domain.model.AbsenceSummary
import com.flex.domain.model.CurrentWeekStatus
import com.flex.domain.model.DashboardForecast
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.UpcomingEvent
import com.flex.domain.model.UpcomingEventType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkHabits
import com.flex.domain.model.WorkLocation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculateDashboardMetricsUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {

    fun calculateForecast(
        actualQuota: QuotaStatus,
        projectedQuota: QuotaStatus = actualQuota,
        effectiveQuotaMinDays: Int,
        effectiveQuotaPercent: Int = 40,
        monthWorkDays: List<WorkDay>,
        targetMonthlyMinutes: Long
    ): DashboardForecast {
        val isQuotaMet = actualQuota.quotaMet
        val isProjectedQuotaMet = projectedQuota.quotaMet

        val plannedOfficeDays = monthWorkDays.count {
            it.isPlanned && (it.location == WorkLocation.OFFICE || it.dayType == DayType.BUSINESS_TRIP) &&
            it.dayType in setOf(DayType.WORK, DayType.BUSINESS_TRIP, DayType.SATURDAY_BONUS)
        }
        val plannedHomeOfficeDays = monthWorkDays.count {
            it.isPlanned && it.location == WorkLocation.HOME_OFFICE &&
            it.dayType in setOf(DayType.WORK, DayType.SATURDAY_BONUS)
        }
        val projectedOfficeDays = projectedQuota.officeDays
        val projectedOfficePercent = projectedQuota.officePercent

        val projectedQuotaText = if (isQuotaMet) {
            "Büro-Quote für diesen Monat bereits erfüllt! 🎉"
        } else if (isProjectedQuotaMet) {
            val percentFormatted = String.format(Locale.GERMAN, "%.1f", projectedOfficePercent)
            if (plannedOfficeDays > 0) {
                "Mit $plannedOfficeDays geplanten Büro-Tagen wird das Ziel am Monatsende erreicht ($projectedOfficeDays / $effectiveQuotaMinDays Tage bzw. $percentFormatted%)! 🎯"
            } else {
                "Ziel am Monatsende voraussichtlich erreicht ($projectedOfficeDays / $effectiveQuotaMinDays Tage bzw. $percentFormatted%)! 🎯"
            }
        } else {
            val daysNeededProjected = (effectiveQuotaMinDays - projectedOfficeDays).coerceAtLeast(0)
            if (plannedOfficeDays > 0) {
                "Inkl. $plannedOfficeDays geplanten Tagen: $projectedOfficeDays / $effectiveQuotaMinDays Tage. Es fehlen noch $daysNeededProjected Büro-Tage bis zum Ziel."
            } else {
                "Bisher keine weiteren Büro-Tage geplant. Es fehlen noch $daysNeededProjected Büro-Tage bis zum Ziel."
            }
        }

        val pacingText = if (isQuotaMet) {
            "Büro-Quote für diesen Monat bereits erfüllt! 🎉"
        } else {
            val daysNeeded = (effectiveQuotaMinDays - actualQuota.officeDays).coerceAtLeast(0)
            val remainingDays = actualQuota.remainingWorkDays
            if (remainingDays <= 0) {
                "Monat beendet: Quote nicht erreicht."
            } else if (daysNeeded > remainingDays) {
                "Achtung: Noch $daysNeeded Büro-Tage nötig, aber nur noch $remainingDays Arbeitstage im Monat."
            } else if (daysNeeded == 0) {
                "Büro-Quote für diesen Monat bereits erfüllt! 🎉"
            } else {
                val interval = remainingDays / daysNeeded
                if (interval <= 1) {
                    "Noch $daysNeeded Büro-Tage an $remainingDays offenen Arbeitstagen nötig (an jedem Tag)."
                } else {
                    "Noch $daysNeeded Büro-Tage an $remainingDays offenen Arbeitstagen nötig (ca. alle $interval Tage)."
                }
            }
        }

        val actualWorkDays = monthWorkDays.filter {
            it.dayType in setOf(DayType.WORK, DayType.BUSINESS_TRIP, DayType.SATURDAY_BONUS) && !it.isPlanned
        }
        val projectedFlextimeText = if (actualWorkDays.isNotEmpty() && actualQuota.remainingWorkDays > 0) {
            val totalActualNet = actualWorkDays.sumOf {
                calculateDayWorkTime(it.timeBlocks, it.dayType == DayType.BUSINESS_TRIP).netMinutes
            }
            val avgNetPerDay = totalActualNet / actualWorkDays.size
            val projectedTotalNet = totalActualNet + (actualQuota.remainingWorkDays * avgNetPerDay)
            val projectedDelta = projectedTotalNet - targetMonthlyMinutes
            val sign = if (projectedDelta >= 0) "+" else "-"
            val absDelta = kotlin.math.abs(projectedDelta)
            val hours = absDelta / 60
            val mins = absDelta % 60
            "Voraussichtlich $sign${hours}h ${mins}min Gleitzeit zum Monatsende (bei aktuellem Schnitt)."
        } else null

        return DashboardForecast(
            quotaPacingText = pacingText,
            isQuotaMet = isQuotaMet,
            projectedFlextimeText = projectedFlextimeText,
            plannedOfficeDays = plannedOfficeDays,
            plannedHomeOfficeDays = plannedHomeOfficeDays,
            projectedOfficeDays = projectedOfficeDays,
            projectedOfficePercent = projectedOfficePercent,
            isProjectedQuotaMet = isProjectedQuotaMet,
            projectedQuotaText = projectedQuotaText
        )
    }

    fun calculateForecast(
        quotaStatus: QuotaStatus,
        effectiveQuotaMinDays: Int,
        monthWorkDays: List<WorkDay>,
        targetMonthlyMinutes: Long
    ): DashboardForecast = calculateForecast(
        actualQuota = quotaStatus,
        projectedQuota = quotaStatus,
        effectiveQuotaMinDays = effectiveQuotaMinDays,
        effectiveQuotaPercent = 40,
        monthWorkDays = monthWorkDays,
        targetMonthlyMinutes = targetMonthlyMinutes
    )

    fun calculateUpcomingEvents(
        today: LocalDate,
        yearDays: List<WorkDay>,
        year: Int
    ): List<UpcomingEvent> {
        val events = mutableListOf<UpcomingEvent>()

        val futureDays = yearDays.filter { it.date >= today }.sortedBy { it.date }

        val vacations = futureDays.filter { it.dayType == DayType.VACATION }
        if (vacations.isNotEmpty()) {
            val blocks = mutableListOf<MutableList<LocalDate>>()
            var currentBlock = mutableListOf<LocalDate>()
            for (v in vacations) {
                if (currentBlock.isEmpty()) {
                    currentBlock.add(v.date)
                } else {
                    val last = currentBlock.last()
                    val diff = java.time.temporal.ChronoUnit.DAYS.between(last, v.date)
                    if (diff == 1L || (last.dayOfWeek == DayOfWeek.FRIDAY && diff == 3L)) {
                        currentBlock.add(v.date)
                    } else {
                        blocks.add(currentBlock)
                        currentBlock = mutableListOf(v.date)
                    }
                }
            }
            if (currentBlock.isNotEmpty()) blocks.add(currentBlock)

            for (b in blocks) {
                val start = b.first()
                val end = b.last()
                events.add(
                    UpcomingEvent(
                        date = start,
                        endDate = if (start != end) end else null,
                        title = "Urlaub",
                        daysCount = b.size,
                        eventType = UpcomingEventType.VACATION
                    )
                )
            }
        }

        futureDays.filter { it.dayType == DayType.FLEX_DAY }.forEach {
            events.add(
                UpcomingEvent(
                    date = it.date,
                    title = "Gleittag",
                    daysCount = 1,
                    eventType = UpcomingEventType.FLEX_DAY
                )
            )
        }

        futureDays.filter { it.dayType == DayType.BUSINESS_TRIP }.forEach {
            events.add(
                UpcomingEvent(
                    date = it.date,
                    title = "Dienstreise",
                    daysCount = 1,
                    eventType = UpcomingEventType.BUSINESS_TRIP
                )
            )
        }

        val holidays = PublicHolidays.getHolidaysForYear(year)
        holidays.filter { it.key >= today }.forEach { (date, name) ->
            events.add(
                UpcomingEvent(
                    date = date,
                    title = name,
                    daysCount = 1,
                    eventType = UpcomingEventType.PUBLIC_HOLIDAY
                )
            )
        }

        return events.sortedBy { it.date }.take(4)
    }

    fun calculateWorkHabits(workDays: List<WorkDay>): WorkHabits {
        val daysWithBlocks = workDays.filter {
            !it.isPlanned && it.timeBlocks.isNotEmpty() && it.dayType in setOf(DayType.WORK, DayType.BUSINESS_TRIP)
        }
        if (daysWithBlocks.isEmpty()) return WorkHabits()

        var startMinutesSum = 0L
        var endMinutesSum = 0L
        var netMinutesSum = 0L
        var breakMinutesSum = 0L
        var count = 0

        for (d in daysWithBlocks) {
            val firstStart = d.timeBlocks.mapNotNull { it.startTime }.minOrNull() ?: continue
            val lastEnd = d.timeBlocks.mapNotNull { it.endTime }.maxOrNull() ?: continue
            val result = calculateDayWorkTime(d.timeBlocks, d.dayType == DayType.BUSINESS_TRIP)
            startMinutesSum += firstStart.toSecondOfDay() / 60
            endMinutesSum += lastEnd.toSecondOfDay() / 60
            netMinutesSum += result.netMinutes
            breakMinutesSum += result.breakMinutes
            count++
        }

        if (count == 0) return WorkHabits()

        val avgStartMin = (startMinutesSum / count).toInt()
        val avgEndMin = (endMinutesSum / count).toInt()
        val avgStart = LocalTime.of(avgStartMin / 60, avgStartMin % 60)
        val avgEnd = LocalTime.of(avgEndMin / 60, avgEndMin % 60)

        return WorkHabits(
            avgStartTime = avgStart,
            avgEndTime = avgEnd,
            avgNetMinutes = netMinutesSum / count,
            avgBreakMinutes = breakMinutesSum / count
        )
    }

    fun calculateAbsenceSummary(yearDays: List<WorkDay>): AbsenceSummary {
        val actualDays = yearDays.filter { !it.isPlanned }
        return AbsenceSummary(
            vacationDays = actualDays.count { it.dayType == DayType.VACATION },
            specialVacationDays = actualDays.count { it.dayType == DayType.SPECIAL_VACATION },
            flexDays = actualDays.count { it.dayType == DayType.FLEX_DAY },
            sickDays = actualDays.count { it.dayType == DayType.SICK_DAY },
            businessTripDays = actualDays.count { it.dayType == DayType.BUSINESS_TRIP }
        )
    }

    fun calculateCurrentWeekStatus(
        today: LocalDate,
        yearDays: List<WorkDay>,
        settings: Settings
    ): CurrentWeekStatus {
        val weekNumber = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val monday = today.with(DayOfWeek.MONDAY)
        val sunday = today.with(DayOfWeek.SUNDAY)

        val weekDays = yearDays.filter { !it.isPlanned && it.date in monday..sunday }
        val workedMinutes = weekDays.sumOf {
            calculateDayWorkTime(it.timeBlocks, it.dayType == DayType.BUSINESS_TRIP).netMinutes
        }
        val targetMinutes = (settings.dailyWorkMinutes * 5).toLong()

        val officeDays = weekDays.count {
            it.dayType in setOf(DayType.WORK, DayType.BUSINESS_TRIP) &&
            (it.location == WorkLocation.OFFICE || it.dayType == DayType.BUSINESS_TRIP)
        }
        val hoDays = weekDays.count {
            it.dayType == DayType.WORK && it.location == WorkLocation.HOME_OFFICE
        }

        return CurrentWeekStatus(
            weekNumber = weekNumber,
            workedMinutes = workedMinutes,
            targetMinutes = targetMinutes,
            officeDays = officeDays,
            homeOfficeDays = hoDays
        )
    }
}
