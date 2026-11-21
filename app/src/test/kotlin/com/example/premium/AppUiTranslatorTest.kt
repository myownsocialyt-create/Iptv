package com.example.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppUiTranslatorTest {

    private val nonEnglishLangs = listOf("hi", "es", "fr", "de", "pt", "ar", "ru")

    @Test
    fun englishReturnsOriginalStrings() {
        assertEquals("Stream Offline", AppUiTranslator.translateInternal("Stream Offline", "en"))
        assertEquals("Channels (120)", AppUiTranslator.translateInternal("Channels (120)", "en"))
        assertEquals("Playing: BBC News", AppUiTranslator.translateInternal("Playing: BBC News", "en"))
    }

    @Test
    fun coreUiStringsTranslateAcrossAllSupportedLanguages() {
        val sampleKeys = listOf(
            "Stream Offline",
            "Connect",
            "Add Account",
            "Retry",
            "Remind Later",
            "Update Now",
            "Upgrade Premium",
            "System Cast / Screen Mirroring",
            "Live Channel",
            "Playback error"
        )
        for (lang in nonEnglishLangs) {
            for (key in sampleKeys) {
                val translated = AppUiTranslator.translateInternal(key, lang)
                assertNotEquals("Missing translation for '$key' in '$lang'", key, translated)
            }
        }
    }

    @Test
    fun dynamicPatternsPreserveDynamicValuesAndTranslateLabels() {
        val playingHi = AppUiTranslator.translateInternal("Playing: BBC World", "hi")
        assertEquals("चल रहा है: BBC World", playingHi)

        val channelsHi = AppUiTranslator.translateInternal("Channels (42)", "hi")
        assertEquals("चैनल (42)", channelsHi)

        val skipEs = AppUiTranslator.translateInternal("Skip in 5s", "es")
        assertNotEquals("Skip in 5s", skipEs)
        assertEquals(true, skipEs.contains("5"))

        val durationFr = AppUiTranslator.translateInternal("1 hr 25 min", "fr")
        assertNotEquals("1 hr 25 min", durationFr)
    }

    @Test
    fun dynamicChannelAndMovieNamesStayUntouched() {
        val dynamicNames = listOf(
            "Sony SAB HD",
            "Play Misty for Me",
            "Spider-Man: No Way Home (2021)",
            "My Custom Family Playlist",
            "http://135.125.132.93:80/live"
        )
        for (lang in nonEnglishLangs) {
            for (title in dynamicNames) {
                assertEquals(title, AppUiTranslator.translateInternal(title, lang))
            }
        }
    }
}
