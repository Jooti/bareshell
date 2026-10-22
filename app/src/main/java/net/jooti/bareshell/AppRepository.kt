package net.jooti.bareshell

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Process
import android.util.Log
import java.util.concurrent.TimeUnit

internal enum class UsageAccess {
    GRANTED,
    REQUIRED,
    UNAVAILABLE,
}

internal sealed interface AppListResult {
    data class Loaded(val apps: List<AppEntry>, val usageAccess: UsageAccess? = null) : AppListResult
    data object Unavailable : AppListResult
}

internal class AppRepository(private val context: Context) {
    fun load(favorites: Boolean = true, limit: Int = 10): AppListResult {
        val apps = try {
            discoverApps()
        } catch (error: SecurityException) {
            Log.e(TAG, "Cannot discover launchable apps", error)
            return AppListResult.Unavailable
        } catch (error: Resources.NotFoundException) {
            Log.e(TAG, "Cannot load application labels", error)
            return AppListResult.Unavailable
        }

        if (!favorites) {
            return AppListResult.Loaded(allApps(apps, context.packageName))
        }

        try {
            if (!hasUsageAccess()) {
                return AppListResult.Loaded(
                    rankApps(apps, emptyList(), context.packageName, limit = limit),
                    UsageAccess.REQUIRED,
                )
            }

            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(7)
            val manager = context.getSystemService(UsageStatsManager::class.java)
            val records = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: throw IllegalStateException("Android returned unavailable usage statistics")
            val usage = records.map { ForegroundUsage(it.packageName, it.totalTimeInForeground) }
            val opens = readOpens(manager, start, end)
            return AppListResult.Loaded(
                rankApps(apps, usage, context.packageName, limit = limit, opens = opens),
                UsageAccess.GRANTED,
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Usage access could not be read or was revoked during the query", error)
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Usage statistics are unavailable", error)
        }

        return AppListResult.Loaded(
            rankApps(apps, emptyList(), context.packageName, limit = limit),
            UsageAccess.UNAVAILABLE,
        )
    }

    @Suppress("DEPRECATION")
    private fun readOpens(manager: UsageStatsManager, start: Long, end: Long): Map<String, Long> {
        val events = manager.queryEvents(start, end)
            ?: throw IllegalStateException("Android returned unavailable foreground events")
        val entries = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                entries += ForegroundEvent(event.timeStamp, event.packageName)
            } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.KEYGUARD_SHOWN)) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.DEVICE_SHUTDOWN)
            ) entries += ForegroundEvent(event.timeStamp, null)
        }
        return countForegroundEntries(entries, start, end)
    }

    @Suppress("DEPRECATION")
    private fun discoverApps(): List<AppEntry> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(intent, 0).mapNotNull { result ->
            val info = result.activityInfo
            if (!info.enabled || !info.applicationInfo.enabled || !info.exported) {
                null
            } else {
                AppEntry(
                    info.packageName,
                    info.applicationInfo.loadLabel(manager).toString(),
                    info.name,
                )
            }
        }
    }

    fun hasUsageAccess(): Boolean = try {
        val manager = context.getSystemService(AppOpsManager::class.java)
        val mode = manager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (error: SecurityException) {
        Log.w(TAG, "Cannot check usage access", error)
        false
    }

    private companion object {
        const val TAG = "BareShellApps"
    }
}
