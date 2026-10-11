package com.flex.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SyncPlannedDaysResultTest {

    @Test
    fun `formatFeedbackMessage when days updated and custom preserved`() {
        val result = SyncPlannedDaysResult(
            updatedDaysCount = 2,
            removedDaysCount = 0,
            untouchedCustomDaysCount = 1
        )
        assertThat(result.formatFeedbackMessage()).isEqualTo("2 Tage an Regel angepasst (1 individuelle beibehalten)")
    }

    @Test
    fun `formatFeedbackMessage when single day updated`() {
        val result = SyncPlannedDaysResult(
            updatedDaysCount = 1,
            removedDaysCount = 0,
            untouchedCustomDaysCount = 0
        )
        assertThat(result.formatFeedbackMessage()).isEqualTo("1 Tage an Regel angepasst")
    }

    @Test
    fun `formatFeedbackMessage when days updated and days removed`() {
        val result = SyncPlannedDaysResult(
            updatedDaysCount = 4,
            removedDaysCount = 2,
            untouchedCustomDaysCount = 1
        )
        assertThat(result.formatFeedbackMessage()).isEqualTo("4 Tage an Regel angepasst, 2 gelöscht (1 individuelle beibehalten)")
    }

    @Test
    fun `formatFeedbackMessage when 0 days updated and none custom`() {
        val result = SyncPlannedDaysResult(
            updatedDaysCount = 0,
            removedDaysCount = 0,
            untouchedCustomDaysCount = 0
        )
        assertThat(result.formatFeedbackMessage()).isEqualTo("Keine Tage angepasst (alle Tage bereits aktuell)")
    }

    @Test
    fun `formatFeedbackMessage when 0 days updated and custom preserved`() {
        val result = SyncPlannedDaysResult(
            updatedDaysCount = 0,
            removedDaysCount = 0,
            untouchedCustomDaysCount = 2
        )
        assertThat(result.formatFeedbackMessage()).isEqualTo("Keine Tage angepasst (2 individuelle beibehalten)")
    }
}
