package net.jooti.bareshell

internal val listCountOptions = listOf(5, 10, 15, 20, 30, 50)

internal enum class AppearanceTheme { SYSTEM, DARK, LIGHT, GREEN, GRAY }
internal enum class AppearanceFont { MONOSPACE, SYSTEM }

internal data class LauncherSettings(
    val topAppsCount: Int = 10,
    val closeContactsCount: Int = 10,
    val appearanceTheme: AppearanceTheme = AppearanceTheme.SYSTEM,
    val appearanceFont: AppearanceFont = AppearanceFont.MONOSPACE,
) {
    init {
        require(topAppsCount in listCountOptions && closeContactsCount in listCountOptions)
    }
}

internal sealed interface SettingsRead {
    data class Ready(val settings: LauncherSettings) : SettingsRead
    data object Invalid : SettingsRead
}

internal fun readSettings(values: Map<String, *>): SettingsRead {
    val apps = if ("top_apps_count" in values) values["top_apps_count"] else 10
    val contacts = if ("close_contacts_count" in values) values["close_contacts_count"] else 10
    if (apps !is Int || contacts !is Int || apps !in listCountOptions || contacts !in listCountOptions) {
        return SettingsRead.Invalid
    }
    val theme = if ("appearance_theme" !in values) AppearanceTheme.SYSTEM else
        AppearanceTheme.entries.firstOrNull { it.name == values["appearance_theme"] } ?: return SettingsRead.Invalid
    val font = if ("appearance_font" !in values) AppearanceFont.MONOSPACE else
        AppearanceFont.entries.firstOrNull { it.name == values["appearance_font"] } ?: return SettingsRead.Invalid
    return SettingsRead.Ready(LauncherSettings(apps, contacts, theme, font))
}
