package net.jooti.bareshell

internal data class AppearancePalette(val background: Int, val foreground: Int, val muted: Int, val light: Boolean)

internal fun AppearanceTheme.palette(night: Boolean): AppearancePalette = when (this) {
    AppearanceTheme.SYSTEM -> (if (night) AppearanceTheme.DARK else AppearanceTheme.LIGHT).palette(night)
    AppearanceTheme.DARK -> AppearancePalette(0xFF121212.toInt(), 0xFFFFFFFF.toInt(), 0xFFAAAAAA.toInt(), false)
    AppearanceTheme.LIGHT -> AppearancePalette(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF666666.toInt(), true)
    AppearanceTheme.GREEN -> AppearancePalette(0xFF081008.toInt(), 0xFF66FF88.toInt(), 0xFF83B88D.toInt(), false)
    AppearanceTheme.GRAY -> AppearancePalette(0xFF767676.toInt(), 0xFF000000.toInt(), 0xFF030303.toInt(), true)
}

internal fun favoriteTopGap(usableHeight: Int, gestureHeight: Int, minimumViewport: Int): Int =
    (usableHeight / 3 - gestureHeight).coerceAtLeast(0)
        .coerceAtMost((usableHeight - gestureHeight - minimumViewport).coerceAtLeast(0))
