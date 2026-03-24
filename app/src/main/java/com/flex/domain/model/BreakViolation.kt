package com.flex.domain.model

enum class BreakViolationType {
    /** A continuous work segment exceeds 6 hours without a qualifying break (≥15 min). */
    CONTINUOUS_WORK_EXCEEDS_6H,
    /** Total work ≥ 6h but total qualifying break time < 30 min. */
    INSUFFICIENT_TOTAL_BREAK,
    /** Total work > 9h but total qualifying break time < 45 min. */
    INSUFFICIENT_TOTAL_BREAK_9H
}

data class BreakViolation(
    val type: BreakViolationType,
    val continuousWorkMinutes: Long = 0,
    val actualBreakMinutes: Long = 0,
    val requiredBreakMinutes: Long = 0
)

data class BreakCheckResult(
    val violations: List<BreakViolation>,
    /** True when check was skipped (e.g. day has isDuration blocks — automatic break deduction active). */
    val skipped: Boolean
)
