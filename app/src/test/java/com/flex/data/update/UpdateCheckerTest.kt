package com.flex.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UpdateCheckerTest {

    private val stableEntry = """
        "versionCode": 25,
        "versionName": "2.0.3",
        "changelog": "- Stabil",
        "downloadUrl": "https://github.com/alxndrbauer/FleX/releases/download/v2.0.3/flex-v2.0.3.apk"
    """.trimIndent()

    private fun manifest(betaEntry: String? = null): String = buildString {
        append("{")
        append(stableEntry)
        if (betaEntry != null) {
            append(",\"beta\": {")
            append(betaEntry)
            append("}")
        }
        append("}")
    }

    private fun betaEntry(
        versionCode: Int = 26,
        versionName: String = "2.1.0-beta.1",
        downloadUrl: String = "https://github.com/alxndrbauer/FleX/releases/download/v2.1.0-beta.1/flex-v2.1.0-beta.1.apk"
    ) = """
        "versionCode": $versionCode,
        "versionName": "$versionName",
        "changelog": "- Beta",
        "downloadUrl": "$downloadUrl"
    """.trimIndent()

    @Test
    fun `newer stable version is returned`() {
        val update = UpdateChecker.selectUpdate(manifest(), currentVersionCode = 24, includeBeta = false)

        assertThat(update?.versionCode).isEqualTo(25)
        assertThat(update?.versionName).isEqualTo("2.0.3")
        assertThat(update?.isBeta).isFalse()
    }

    @Test
    fun `beta channel is ignored without opt-in`() {
        val update = UpdateChecker.selectUpdate(
            manifest(betaEntry()),
            currentVersionCode = 24,
            includeBeta = false
        )

        assertThat(update?.versionCode).isEqualTo(25)
        assertThat(update?.isBeta).isFalse()
    }

    @Test
    fun `beta channel is returned with opt-in when it is newer`() {
        val update = UpdateChecker.selectUpdate(
            manifest(betaEntry()),
            currentVersionCode = 25,
            includeBeta = true
        )

        assertThat(update?.versionCode).isEqualTo(26)
        assertThat(update?.versionName).isEqualTo("2.1.0-beta.1")
        assertThat(update?.isBeta).isTrue()
    }

    @Test
    fun `newer stable version wins over older beta even with opt-in`() {
        val update = UpdateChecker.selectUpdate(
            manifest(betaEntry(versionCode = 24)),
            currentVersionCode = 23,
            includeBeta = true
        )

        assertThat(update?.versionCode).isEqualTo(25)
        assertThat(update?.isBeta).isFalse()
    }

    @Test
    fun `no update is returned when installed version is current`() {
        val update = UpdateChecker.selectUpdate(
            manifest(betaEntry(versionCode = 25)),
            currentVersionCode = 25,
            includeBeta = true
        )

        assertThat(update).isNull()
    }

    @Test
    fun `invalid beta entry without download url is ignored`() {
        val update = UpdateChecker.selectUpdate(
            manifest(betaEntry(downloadUrl = "")),
            currentVersionCode = 25,
            includeBeta = true
        )

        assertThat(update).isNull()
    }

    @Test
    fun `legacy manifest without beta field stays compatible`() {
        val update = UpdateChecker.selectUpdate(
            """{"versionCode": 25, "versionName": "2.0.3", "downloadUrl": "https://github.com/x/flex.apk"}""",
            currentVersionCode = 24,
            includeBeta = true
        )

        assertThat(update?.versionCode).isEqualTo(25)
        assertThat(update?.isBeta).isFalse()
    }

    @Test
    fun `malformed manifest returns null`() {
        val update = UpdateChecker.selectUpdate(
            "not json",
            currentVersionCode = 24,
            includeBeta = true
        )

        assertThat(update).isNull()
    }
}
