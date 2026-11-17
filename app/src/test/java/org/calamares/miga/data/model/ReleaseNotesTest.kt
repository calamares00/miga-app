package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The release notes the release job uploads to Google Play (fastlane changelogs): Play rejects the
 * whole upload when one is longer than 500 characters, which left 1.0.35 out of Play.
 */
class ReleaseNotesTest {

    private val versionCode = Regex("""versionCode = (\d+)""").find(File("build.gradle.kts").readText())!!.groupValues[1]

    @Test
    fun `every Play release note fits in 500 characters`() {
        listOf("es-ES", "en-US").forEach { locale ->
            File("../fastlane/metadata/android/$locale/changelogs").listFiles { file -> file.extension == "txt" }!!.forEach { file ->
                val length = file.readText().length
                assertTrue("$locale/${file.name} has $length characters (max 500)", length <= 500)
            }
        }
    }

    @Test
    fun `the current version has notes in both languages, the same in the app and in Play`() {
        mapOf("changelogs" to "es-ES", "changelogs-en" to "en-US").forEach { (assets, locale) ->
            val inApp = File("src/main/assets/$assets/$versionCode.txt")
            val inPlay = File("../fastlane/metadata/android/$locale/changelogs/$versionCode.txt")
            assertTrue("missing ${inApp.path}", inApp.exists())
            assertTrue("missing ${inPlay.path}", inPlay.exists())
            assertEquals(inApp.readText(), inPlay.readText())
        }
    }
}
