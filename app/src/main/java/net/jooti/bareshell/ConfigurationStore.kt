package net.jooti.bareshell

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log

internal class ConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences("launcher_configuration", Context.MODE_PRIVATE)

    fun load(available: Set<Gesture>): ConfigurationRead = synchronized(lock) {
        readConfiguration(preferences.all, available)
    }

    fun initialize(): Boolean = synchronized(lock) {
        if (preferences.all.isNotEmpty()) {
            return@synchronized readConfiguration(preferences.all) is ConfigurationRead.Ready
        }
        write(defaultConfiguration.storedValues() + ("introduction_pending" to true))
    }

    fun finishIntroduction(): Boolean = synchronized(lock) {
        val saved = readConfiguration(preferences.all)
        if (saved !is ConfigurationRead.Ready) {
            Log.e("BareShellSettings", "Cannot finish introduction without a valid configuration")
            return@synchronized false
        }
        if (!saved.introductionPending) return@synchronized true
        write((preferences.all - "introduction_pending") + ("default_home_pending" to true))
    }

    fun dismissDefaultHomeOffer(): Boolean = synchronized(lock) {
        if ("default_home_pending" !in preferences.all) return@synchronized true
        write(preferences.all - "default_home_pending")
    }

    fun reset(): Boolean = synchronized(lock) { write(mapOf("setup_requested" to true)) }

    fun save(configuration: LauncherConfiguration): Boolean = synchronized(lock) {
        write(configuration.storedValues())
    }

    private fun editor(values: Map<String, *>) = preferences.edit().clear().also { editor ->
        values.forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { requireNotNull(it as? String) }.toSet())
                else -> error("Unsupported configuration value")
            }
        }
    }

    @SuppressLint("ApplySharedPref") // Worker writes must report failure and restore the memory cache.
    private fun write(values: Map<String, *>): Boolean {
        val previous = preferences.all
        val saved = try {
            editor(values).commit()
        } catch (error: SecurityException) {
            Log.e("BareShellSettings", "Cannot persist launcher configuration", error)
            false
        }
        if (!saved) {
            Log.e("BareShellSettings", "Launcher configuration was not persisted")
            editor(previous).apply()
        }
        return saved
    }

    private companion object {
        val lock = Any()
    }
}
