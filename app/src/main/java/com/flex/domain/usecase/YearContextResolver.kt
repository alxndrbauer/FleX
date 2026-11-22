package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.YearClosing
import javax.inject.Inject

/**
 * Resolves year-specific [Settings] and flextime inputs across closed past years,
 * legacy unclosed past years (via backward derivation), the active [Settings.settingsYear],
 * and not-yet-closed future years (via forward chaining).
 */
class YearContextResolver @Inject constructor(
    private val calculateFlextime: CalculateFlextimeUseCase
) {

    /**
     * Resolves the effective [Settings] valid for [targetYear].
     *
     * - Case A (`targetYear == settings.settingsYear`): returns [settings] unchanged.
     * - Case B (`targetYear < settings.settingsYear` with [YearClosing] snapshot): restores exact
     *   start balances and historical vacation entitlements from the snapshot.
     * - Case C (`targetYear < settings.settingsYear` without snapshot): backward-derives start
     *   balances from the nearest higher anchor (either the next closed year or [settings]).
     *   Note: If an [DayType.OVERTIME_DAY] completely drained the overtime account to 0 in an
     *   unarchived legacy year (covering the remainder from flextime), the exact initial overtime
     *   before that drain is mathematically non-injective; the uncapped-overtime pass provides a
     *   best-effort heuristic.
     * - Case D (`targetYear > settings.settingsYear`): chains vacation carry-over forward and resets
     *   special vacation fields; if [includeForwardBalances] is true, also chains flextime and
     *   overtime balances forward.
     */
    fun resolveSettingsForYear(
        targetYear: Int,
        settings: Settings,
        closingsByYear: Map<Int, YearClosing>,
        workDaysLookup: (Int) -> List<WorkDay>,
        workTimeRules: List<WorkTimeRule>,
        includeForwardBalances: Boolean = false
    ): Settings {
        if (targetYear == settings.settingsYear) {
            return settings
        }

        if (targetYear < settings.settingsYear) {
            val closing = closingsByYear[targetYear]
            if (closing != null) {
                return settings.copy(
                    settingsYear = targetYear,
                    annualVacationDays = closing.annualVacationDays,
                    carryOverVacationDays = closing.carryOverVacationDays,
                    specialVacationDays = closing.specialVacationDays,
                    specialVacationPaidOut = closing.specialVacationPaidOut,
                    initialFlextimeMinutes = closing.startFlextimeMinutes,
                    initialOvertimeMinutes = closing.startOvertimeMinutes
                )
            }

            // Case C: Back-derive from nearest higher anchor
            val anchorClosingYear = closingsByYear.keys
                .filter { it > targetYear && it < settings.settingsYear }
                .minOrNull()
            val anchorYear = anchorClosingYear ?: settings.settingsYear
            var curFlex = anchorClosingYear?.let { closingsByYear[it]?.startFlextimeMinutes }
                ?: settings.initialFlextimeMinutes
            var curOvertime = anchorClosingYear?.let { closingsByYear[it]?.startOvertimeMinutes }
                ?: settings.initialOvertimeMinutes

            for (y in (anchorYear - 1) downTo targetYear) {
                val yClosing = closingsByYear[y]
                if (yClosing != null) {
                    curFlex = yClosing.startFlextimeMinutes
                    curOvertime = yClosing.startOvertimeMinutes
                } else {
                    val actualDays = workDaysLookup(y).filter { !it.isPlanned }
                    if (actualDays.isNotEmpty()) {
                        val candidateOvertime = if (actualDays.any { it.dayType == DayType.OVERTIME_DAY }) {
                            val uncapped = runCatching {
                                calculateFlextime(
                                    actualDays,
                                    settings.copy(initialFlextimeMinutes = 0, initialOvertimeMinutes = 1_000_000),
                                    null,
                                    workTimeRules
                                )
                            }.getOrNull()
                            maxOf(0, curOvertime - (uncapped?.earnedOvertimeMinutes?.toInt() ?: 0))
                        } else {
                            val zeroSim = runCatching {
                                calculateFlextime(
                                    actualDays,
                                    settings.copy(initialFlextimeMinutes = 0, initialOvertimeMinutes = 0),
                                    null,
                                    workTimeRules
                                )
                            }.getOrNull()
                            maxOf(0, curOvertime - (zeroSim?.earnedOvertimeMinutes?.toInt() ?: 0))
                        }
                        val sim = runCatching {
                            calculateFlextime(
                                actualDays,
                                settings.copy(initialFlextimeMinutes = 0, initialOvertimeMinutes = candidateOvertime),
                                null,
                                workTimeRules
                            )
                        }.getOrNull()
                        if (sim != null) {
                            curFlex -= sim.earnedMinutes.toInt()
                            curOvertime = candidateOvertime
                        }
                    }
                }
            }

            return settings.copy(
                settingsYear = targetYear,
                initialFlextimeMinutes = curFlex,
                initialOvertimeMinutes = curOvertime,
                specialVacationPaidOut = false
            )
        }

        // Case D: targetYear > settings.settingsYear (not-yet-closed future years)
        var curCarryOver = settings.carryOverVacationDays
        var curFlex = settings.initialFlextimeMinutes
        var curOvertime = settings.initialOvertimeMinutes

        for (y in settings.settingsYear until targetYear) {
            val actualDays = workDaysLookup(y).filter { !it.isPlanned }
            val usedVacation = actualDays.count { it.dayType == DayType.VACATION }
            curCarryOver = maxOf(0, settings.annualVacationDays + curCarryOver - usedVacation)
            if (includeForwardBalances) {
                val balance = runCatching {
                    calculateFlextime(
                        actualDays,
                        settings.copy(
                            settingsYear = y,
                            initialFlextimeMinutes = curFlex,
                            initialOvertimeMinutes = curOvertime
                        ),
                        null,
                        workTimeRules
                    )
                }.getOrNull()
                if (balance != null) {
                    curFlex = balance.totalMinutes.toInt()
                    curOvertime = balance.overtimeMinutes.toInt()
                }
            }
        }

        return settings.copy(
            settingsYear = targetYear,
            carryOverVacationDays = curCarryOver,
            specialVacationDays = 0,
            specialVacationPaidOut = false,
            initialFlextimeMinutes = curFlex,
            initialOvertimeMinutes = curOvertime
        )
    }

    /**
     * Returns `(effectiveSettings, flextimeWorkDays)` for [CalculateFlextimeUseCase] in [targetYear].
     *
     * For `targetYear <= settings.settingsYear`, only [targetYearWorkDays] are returned alongside
     * the resolved year-specific [Settings] so historical views never double-count balances.
     * For `targetYear > settings.settingsYear`, [settings] (anchored at [Settings.settingsYear])
     * is paired with all unclosed prior years' days followed by [targetYearWorkDays].
     */
    fun buildFlextimeInputForYear(
        targetYear: Int,
        settings: Settings,
        closingsByYear: Map<Int, YearClosing>,
        targetYearWorkDays: List<WorkDay>,
        otherYearsWorkDaysLookup: (Int) -> List<WorkDay>,
        workTimeRules: List<WorkTimeRule>
    ): Pair<Settings, List<WorkDay>> {
        if (targetYear <= settings.settingsYear) {
            val effectiveSettings = resolveSettingsForYear(
                targetYear = targetYear,
                settings = settings,
                closingsByYear = closingsByYear,
                workDaysLookup = { year ->
                    if (year == targetYear) targetYearWorkDays else otherYearsWorkDaysLookup(year)
                },
                workTimeRules = workTimeRules
            )
            return effectiveSettings to targetYearWorkDays
        }

        val priorDays = (settings.settingsYear until targetYear).flatMap { year ->
            otherYearsWorkDaysLookup(year)
        }
        return settings to (priorDays + targetYearWorkDays)
    }
}
