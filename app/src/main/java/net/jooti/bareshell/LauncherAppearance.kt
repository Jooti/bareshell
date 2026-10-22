package net.jooti.bareshell

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue

internal fun Context.isNightAppearance(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal fun Activity.applyAppearance(settings: LauncherSettings) {
    val chosen = if (settings.appearanceTheme == AppearanceTheme.SYSTEM) {
        if (isNightAppearance()) AppearanceTheme.DARK else AppearanceTheme.LIGHT
    } else settings.appearanceTheme
    setTheme(when (chosen) {
        AppearanceTheme.DARK -> R.style.Appearance_Dark
        AppearanceTheme.GREEN -> R.style.Appearance_Green
        AppearanceTheme.GRAY -> R.style.Appearance_Gray
        AppearanceTheme.LIGHT, AppearanceTheme.SYSTEM -> R.style.Appearance_Light
    })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        theme.applyStyle(if (chosen.palette(isNightAppearance()).light) R.style.Navigation_Light else R.style.Navigation_Dark, true)
    }
    theme.applyStyle(
        if (settings.appearanceFont == AppearanceFont.MONOSPACE) R.style.Font_Monospace else R.style.Font_System,
        true,
    )
}

internal fun Context.appearanceColor(resource: Int): Int {
    val attribute = when (resource) {
        R.color.background -> R.attr.launcherBackground
        R.color.foreground -> R.attr.launcherForeground
        R.color.foreground_muted -> R.attr.launcherMuted
        else -> error("Unsupported appearance color")
    }
    val value = TypedValue()
    check(theme.resolveAttribute(attribute, value, true)) { "Missing launcher appearance attribute" }
    return value.data
}

internal val AppearanceTheme.labelRes: Int
    get() = when (this) {
        AppearanceTheme.SYSTEM -> R.string.theme_system
        AppearanceTheme.DARK -> R.string.theme_dark
        AppearanceTheme.LIGHT -> R.string.theme_light
        AppearanceTheme.GREEN -> R.string.theme_green
        AppearanceTheme.GRAY -> R.string.theme_gray
    }

internal val AppearanceFont.labelRes: Int
    get() = if (this == AppearanceFont.MONOSPACE) R.string.font_monospace else R.string.font_system

internal val AppearanceFont.typeface: Typeface
    get() = if (this == AppearanceFont.MONOSPACE) Typeface.MONOSPACE else Typeface.SANS_SERIF
