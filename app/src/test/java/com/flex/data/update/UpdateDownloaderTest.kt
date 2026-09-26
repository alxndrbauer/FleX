package com.flex.data.update

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock

class UpdateDownloaderTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://github.com/alxndrbauer/FleX/releases/download/v1.7.7/app-release.apk",
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456?token=abc",
            "https://raw.githubusercontent.com/alxndrbauer/FleX/main/release.apk",
            "https://api.github.com/repos/alxndrbauer/FleX/releases/assets/1",
            "https://sub.github.com/download.apk",
            "https://sub.objects.githubusercontent.com/asset.apk",
            "https://sub.raw.githubusercontent.com/asset.apk",
            "https://github.com:443/releases/download/v1.0.0/app.apk",
            "HTTPS://GITHUB.COM/releases/download/v1.0.0/app.apk"
        ]
    )
    fun validGithubUrls_areAllowed(url: String) {
        assertThat(UpdateDownloader.isAllowedDownloadUrl(url)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://github.com/alxndrbauer/FleX/releases/download/v1.7.7/app-release.apk",
            "http://objects.githubusercontent.com/asset.apk",
            "http://raw.githubusercontent.com/asset.apk",
            "ftp://github.com/release.apk",
            "file:///android_asset/update.apk",
            "content://com.flex.provider/apk",
            "javascript:alert(1)"
        ]
    )
    fun insecureOrInvalidSchemes_areRejected(url: String) {
        assertThat(UpdateDownloader.isAllowedDownloadUrl(url)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://evil.com/app.apk",
            "https://attacker.githubusercontent.com/app.apk",
            "https://github.com.evil.com/app.apk",
            "https://fakegithub.com/app.apk",
            "https://evil-github.com/app.apk",
            "https://evilobjects.githubusercontent.com/app.apk",
            "https://notgithub.com/app.apk",
            "https://google.com/app.apk",
            "https://192.168.1.1/update.apk"
        ]
    )
    fun foreignOrUntrustedDomains_areRejected(url: String) {
        assertThat(UpdateDownloader.isAllowedDownloadUrl(url)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "not a url",
            "https://",
            "https:///path",
            "https://:443"
        ]
    )
    fun malformedUrls_areRejected(url: String) {
        assertThat(UpdateDownloader.isAllowedDownloadUrl(url)).isFalse()
    }

    @Test
    fun downloadAndInstall_throwsException_onInvalidUrl() {
        val mockContext = mock(Context::class.java)
        val invalidUrl = "http://evil.com/update.apk"

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                UpdateDownloader.downloadAndInstall(mockContext, invalidUrl)
            }
        }

        assertThat(exception).hasMessageThat().isEqualTo("Ungültige oder unsichere Download-URL: $invalidUrl")
    }

    @Test
    fun actionInstallCompleteConstant_hasExpectedValue() {
        assertThat(UpdateDownloader.ACTION_INSTALL_COMPLETE).isEqualTo("com.flex.ACTION_INSTALL_COMPLETE")
    }
}
