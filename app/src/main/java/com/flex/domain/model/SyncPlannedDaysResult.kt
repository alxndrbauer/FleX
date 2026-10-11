package com.flex.domain.model

data class SyncPlannedDaysResult(
    val updatedDaysCount: Int = 0,
    val removedDaysCount: Int = 0,
    val untouchedCustomDaysCount: Int = 0
) {
    fun formatFeedbackMessage(): String = buildString {
        if (updatedDaysCount == 0 && removedDaysCount == 0) {
            append("Keine Tage angepasst")
            if (untouchedCustomDaysCount > 0) {
                append(" ($untouchedCustomDaysCount individuelle beibehalten)")
            } else {
                append(" (alle Tage bereits aktuell)")
            }
        } else {
            if (updatedDaysCount > 0) {
                append("$updatedDaysCount Tage an Regel angepasst")
            }
            if (removedDaysCount > 0) {
                if (updatedDaysCount > 0) append(", ")
                append("$removedDaysCount gelöscht")
            }
            if (untouchedCustomDaysCount > 0) {
                append(" ($untouchedCustomDaysCount individuelle beibehalten)")
            }
        }
    }
}

