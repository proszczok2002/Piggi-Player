package pl.proszczok.player.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class UpdateManifestParserTest {
    private val manifestUrl = URL(
        "https://github.com/proszczok2002/Piggi-Player/releases/latest/download/piggi-player-update.json",
    )

    @Test
    fun parsesGitHubReleaseManifestAndResolvesRelativeApk() {
        val result = UpdateManifestParser.parse(
            json = """
                {
                  "versionCode": 17,
                  "versionName": "1.1.0",
                  "apk": "Piggi-Player-1.1.0.apk",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "notes": "GitHub updater"
                }
            """.trimIndent(),
            manifestUrl = manifestUrl,
            currentVersionCode = 16,
        )

        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals(17, info.versionCode)
        assertEquals("1.1.0", info.versionName)
        assertEquals(
            "https://github.com/proszczok2002/Piggi-Player/releases/latest/download/Piggi-Player-1.1.0.apk",
            info.apkUrl.toString(),
        )
    }

    @Test
    fun reportsUpToDateWhenVersionCodeIsNotNewer() {
        val result = UpdateManifestParser.parse(
            json = """
                {
                  "versionCode": 17,
                  "versionName": "1.1.0",
                  "apk": "Piggi-Player-1.1.0.apk",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
            """.trimIndent(),
            manifestUrl = manifestUrl,
            currentVersionCode = 17,
        )

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpsApk() {
        UpdateManifestParser.parse(
            json = """
                {
                  "versionCode": 18,
                  "versionName": "1.2.0",
                  "apk": "http://example.com/Piggi-Player.apk",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
            """.trimIndent(),
            manifestUrl = manifestUrl,
            currentVersionCode = 17,
        )
    }
}
