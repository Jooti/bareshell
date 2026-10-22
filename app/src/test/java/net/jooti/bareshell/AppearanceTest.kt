package net.jooti.bareshell

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AppearanceTest {
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 255) / 255.0
            return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }

    @Test fun allPalettesHaveReadablePrimaryAndMutedText() {
        AppearanceTheme.entries.forEach { theme ->
            listOf(false, true).forEach { night ->
                val palette = theme.palette(night)
                listOf(palette.foreground, palette.muted).forEach { text ->
                    val foreground = luminance(text)
                    val background = luminance(palette.background)
                    val contrast = (max(foreground, background) + .05) / (min(foreground, background) + .05)
                    assertTrue("$theme text contrast $contrast", contrast >= 4.5)
                }
            }
        }
        assertEquals(.18, luminance(AppearanceTheme.GRAY.palette(false).background), .002)
    }

    @Test fun onlySystemThemeChangesWithDeviceNightMode() {
        assertEquals(AppearanceTheme.DARK.palette(false), AppearanceTheme.SYSTEM.palette(true))
        assertEquals(AppearanceTheme.LIGHT.palette(true), AppearanceTheme.SYSTEM.palette(false))
        (AppearanceTheme.entries - AppearanceTheme.SYSTEM).forEach {
            assertEquals(it.palette(false), it.palette(true))
        }
    }

    @Test fun previewPalettesMatchNativeThemeResources() {
        val file = Path.of("src", "main", "res", "values", "appearance.xml").toFile()
        val styles = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("style")
        (AppearanceTheme.entries - AppearanceTheme.SYSTEM).forEach { theme ->
            val name = "Appearance.${theme.name.lowercase().replaceFirstChar { it.uppercaseChar() }}"
            val style = (0 until styles.length).map { styles.item(it) as Element }
                .single { it.getAttribute("name") == name }
            val items = style.getElementsByTagName("item")
            val values = (0 until items.length).map { items.item(it) as Element }
                .associate { it.getAttribute("name") to it.textContent.trim() }
            val palette = theme.palette(false)
            mapOf(
                "launcherBackground" to palette.background,
                "launcherForeground" to palette.foreground,
                "launcherMuted" to palette.muted,
            ).forEach { (attribute, color) ->
                assertEquals("$name $attribute", "#%06X".format(color and 0xFFFFFF), values[attribute])
            }
        }
    }

    @Test fun favoriteGapCountsExistingGestureAreaAndProtectsViewport() {
        assertEquals(204, favoriteTopGap(900, 96, 112))
        assertEquals(0, favoriteTopGap(240, 96, 112))
        assertEquals(52, favoriteTopGap(600, 96, 452))
        listOf(160, 240, 600, 900).forEach { height ->
            listOf(112, 224, 400).forEach { minimum ->
                val gap = favoriteTopGap(height, 96, minimum)
                assertTrue(gap >= 0)
                if (height - 96 >= minimum) assertTrue(height - 96 - gap >= minimum)
            }
        }
    }
}
