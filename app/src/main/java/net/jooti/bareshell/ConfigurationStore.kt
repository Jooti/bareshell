package net.jooti.bareshell

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log

internal class ConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences("launcher_configuration", Context.MODE_PRIVATE)

    fun load(available: Set<Gesture>): ConfigurationRead =
        readConfiguration(preferences.all, available)

    @SuppressLint("ApplySharedPref") // Reset must be durable before the UI enters setup.
    fun reset(): Boolean {
        val previous = preferences.all
        val cleared = try {
            preferences.edit().clear().commit()
        } catch (error: SecurityException) {
            Log.e("BareShellSettings", "Cannot reset launcher setup", error)
            false
        }
        if (!cleared) {
            Log.e("BareShellSettings", "Launcher setup reset was not persisted")
            val rollback = preferences.edit().clear()
            previous.forEach { (key, value) ->
                when (value) {
                    is Int -> rollback.putInt(key, value)
                    is String -> rollback.putString(key, value)
                }
            }
            rollback.apply()
        }
        return cleared
    }

    @SuppressLint("ApplySharedPref") // Called on the worker; setup must observe the disk-write result.
    fun save(configuration: LauncherConfiguration): Boolean {
        val editor = preferences.edit().clear()
        configuration.storedValues().forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported configuration value")
            }
        }
        val saved = try {
            editor.commit()
        } catch (error: SecurityException) {
            Log.e("BareShellSettings", "Cannot persist launcher configuration", error)
            false
        }
        if (!saved) {
            // A failed commit still changes SharedPreferences' memory cache.
            preferences.edit().clear().apply()
        }
        return saved
    }
}
