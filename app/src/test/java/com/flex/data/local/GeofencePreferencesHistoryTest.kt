package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.location.Geofence
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class GeofencePreferencesHistoryTest {

    @Test
    fun `appendLog stores entries newest first and caps at 20`() {
        val values = mutableMapOf<String, Any>()
        val prefs = createPreferences(values)

        repeat(25) { index ->
            prefs.appendLog(
                type = GeofenceLogType.ENTER,
                atEpochMs = 1_700_000_000_000L + index * 1_000L,
                detail = "entry-$index"
            )
        }

        val history = prefs.eventHistory.value
        assertThat(history).hasSize(20)
        assertThat(history.first().detail).isEqualTo("entry-24")
        assertThat(history.last().detail).isEqualTo("entry-5")
    }

    @Test
    fun `history persists across GeofencePreferences instances and sanitizes delimiters`() {
        val values = mutableMapOf<String, Any>()
        val first = createPreferences(values)

        first.appendLog(
            type = GeofenceLogType.REGISTER_FAILED,
            atEpochMs = 1_700_000_000_000L,
            detail = "Speichern: foo|bar\nbaz"
        )

        val reloaded = createPreferences(values)
        val history = reloaded.eventHistory.value
        assertThat(history).hasSize(1)
        assertThat(history[0].type).isEqualTo(GeofenceLogType.REGISTER_FAILED)
        assertThat(history[0].atEpochMs).isEqualTo(1_700_000_000_000L)
        assertThat(history[0].detail).isEqualTo("Speichern: foo bar baz")
    }

    @Test
    fun `recordEvent and registration updates append matching log entries`() {
        val values = mutableMapOf<String, Any>()
        val prefs = createPreferences(values)

        prefs.markRegistrationSuccess(1_700_000_001_000L, source = "App-Start")
        prefs.recordEvent(
            transition = Geofence.GEOFENCE_TRANSITION_ENTER,
            errorCode = null,
            atEpochMs = 1_700_000_002_000L,
            detail = "Eingestempelt"
        )
        prefs.recordEvent(
            transition = null,
            errorCode = 1000,
            atEpochMs = 1_700_000_003_000L
        )
        prefs.markRegistrationFailure(
            atEpochMs = 1_700_000_004_000L,
            source = "Fehler-Retry",
            errorMessage = "Play Services error"
        )

        val history = prefs.eventHistory.value
        assertThat(history.map { it.type }).containsExactly(
            GeofenceLogType.REGISTER_FAILED,
            GeofenceLogType.ERROR,
            GeofenceLogType.ENTER,
            GeofenceLogType.REGISTERED
        ).inOrder()
        assertThat(history[0].detail).contains("Fehler-Retry")
        assertThat(history[0].detail).contains("Play Services error")
        assertThat(history[1].detail).contains("1000")
        assertThat(history[2].detail).isEqualTo("Eingestempelt")
        assertThat(history[3].detail).isEqualTo("App-Start")
    }

    @Test
    fun `clearEventHistory removes persisted entries and resets state flow`() {
        val values = mutableMapOf<String, Any>()
        val prefs = createPreferences(values)
        prefs.appendLog(GeofenceLogType.EXIT, 1_700_000_005_000L, "Ausstempeln geplant (30s)")

        prefs.clearEventHistory()

        assertThat(prefs.eventHistory.value).isEmpty()
        assertThat(createPreferences(values).eventHistory.value).isEmpty()
    }

    private fun createPreferences(values: MutableMap<String, Any>): GeofencePreferences {
        val context = mockk<Context>()
        val sharedPreferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)

        every {
            context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        } returns sharedPreferences
        every { sharedPreferences.contains(any()) } answers {
            values.containsKey(firstArg<String>())
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg()
        }
        every { sharedPreferences.getLong(any(), any()) } answers {
            values[firstArg()] as? Long ?: secondArg()
        }
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg()
        }
        every { sharedPreferences.getString(any(), null) } answers {
            values[firstArg()] as? String ?: secondArg()
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            values[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putString(any(), null) } answers {
            values.remove(firstArg<String>())
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }

        return GeofencePreferences(context)
    }
}
