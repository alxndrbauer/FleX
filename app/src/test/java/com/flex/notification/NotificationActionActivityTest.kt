package com.flex.notification

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NotificationActionActivityTest {

    @Test
    fun `action constants match expected values`() {
        assertThat(NotificationActionActivity.ACTION_PAUSE).isEqualTo("com.flex.notification.ACTION_PAUSE")
        assertThat(NotificationActionActivity.ACTION_CLOCK_OUT).isEqualTo("com.flex.notification.ACTION_CLOCK_OUT")
        assertThat(NotificationActionActivity.ACTION_VIEW_EXPORT).isEqualTo("com.flex.notification.ACTION_VIEW_EXPORT")
        assertThat(NotificationActionActivity.ACTION_SHARE_EXPORT).isEqualTo("com.flex.notification.ACTION_SHARE_EXPORT")
    }
}
