package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsTest {
    @Test
    fun appearanceDefaultsAndEveryStoredChoiceAreReadWithoutChangingCounts() {
        assertEquals(AppearanceTheme.SYSTEM, LauncherSettings().appearanceTheme)
        assertEquals(AppearanceFont.MONOSPACE, LauncherSettings().appearanceFont)
        AppearanceTheme.entries.forEach { theme ->
            AppearanceFont.entries.forEach { font ->
                assertEquals(
                    SettingsRead.Ready(LauncherSettings(20, 5, theme, font)),
                    readSettings(mapOf("top_apps_count" to 20, "close_contacts_count" to 5,
                        "appearance_theme" to theme.name, "appearance_font" to font.name)),
                )
            }
        }
    }

    @Test
    fun invalidAppearanceIsRejectedExplicitly() {
        listOf("UNKNOWN", "", 1, true, null).forEach { value ->
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("appearance_theme" to value)))
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("appearance_font" to value)))
        }
    }
    @Test
    fun defaultsAreIndependentAndAllSixChoicesAreSupported() {
        assertEquals(SettingsRead.Ready(LauncherSettings(10, 10)), readSettings(emptyMap<String, Any>()))
        assertEquals(listOf(5, 10, 15, 20, 30, 50), listCountOptions)
        listCountOptions.forEach { count ->
            assertEquals(SettingsRead.Ready(LauncherSettings(count, 10)), readSettings(mapOf("top_apps_count" to count)))
            assertEquals(SettingsRead.Ready(LauncherSettings(10, count)), readSettings(mapOf("close_contacts_count" to count)))
        }
    }

    @Test
    fun invalidCountsAndTypesAreExplicitlyRejected() {
        listOf(0, -1, 7, 100, "10", 10L, true, null).forEach { invalid ->
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("top_apps_count" to invalid)))
            assertEquals(SettingsRead.Invalid, readSettings(mapOf("close_contacts_count" to invalid)))
        }
    }
}
