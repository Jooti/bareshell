package net.jooti.bareshell

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

internal class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("list_settings", Context.MODE_PRIVATE)

    fun load(): SettingsRead = readSettings(preferences.all).also {
        if (it == SettingsRead.Invalid) Log.e("BareShellSettings", "Invalid launcher settings")
    }

    @SuppressLint("ApplySharedPref") // Worker-thread writes must report persistence failure.
    fun save(settings: LauncherSettings): Boolean {
        val previous = preferences.all
        val editor = preferences.edit().clear()
            .putInt("top_apps_count", settings.topAppsCount)
            .putInt("close_contacts_count", settings.closeContactsCount)
            .putString("appearance_theme", settings.appearanceTheme.name)
            .putString("appearance_font", settings.appearanceFont.name)
        val saved = try {
            editor.commit()
        } catch (error: SecurityException) {
            Log.e("BareShellSettings", "Cannot save list settings", error)
            false
        }
        if (!saved) {
            Log.e("BareShellSettings", "List settings were not persisted")
            val rollback = preferences.edit().clear()
            previous.forEach { (key, value) ->
                when (value) {
                    is Int -> rollback.putInt(key, value)
                    is String -> rollback.putString(key, value)
                    is Boolean -> rollback.putBoolean(key, value)
                    is Long -> rollback.putLong(key, value)
                    is Float -> rollback.putFloat(key, value)
                }
            }
            rollback.apply()
        }
        return saved
    }
}
