package net.jooti.bareshell

import java.text.Collator
import java.util.Locale

internal data class AppEntry(
    val packageName: String,
    val label: String,
    val activityName: String,
)

internal data class ForegroundUsage(val packageName: String, val milliseconds: Long)

internal fun allApps(
    apps: List<AppEntry>,
    ownPackageName: String,
    locale: Locale = Locale.getDefault(),
): List<AppEntry> {
    val collator = Collator.getInstance(locale)
    return apps.asSequence()
        .filter { it.packageName != ownPackageName }
        .sortedBy { it.activityName }
        .distinctBy { it.packageName }
        .sortedWith { first, second ->
            collator.compare(first.label, second.label).takeIf { it != 0 }
                ?: first.packageName.compareTo(second.packageName)
        }
        .toList()
}

internal fun rankApps(
    apps: List<AppEntry>,
    usage: List<ForegroundUsage>,
    ownPackageName: String,
    locale: Locale = Locale.getDefault(),
    limit: Int = 10,
    opens: Map<String, Long> = emptyMap(),
    adjustments: Map<String, RankingAdjustment> = emptyMap(),
): List<AppEntry> {
    require(limit in listCountOptions)
    val totals = usage.groupingBy { it.packageName }
        .fold(0L) { total, record -> addUsage(total, record.milliseconds) }
    val eligible = allApps(apps, ownPackageName, locale)
    val scores = eligible.associate { app ->
        val automatic = addUsage(opens[app.packageName]?.coerceAtLeast(0) ?: 0,
            (totals[app.packageName] ?: 0) / 600_000)
        val manual = adjustments[app.packageName]?.points?.toLong() ?: 0
        app.packageName to if (manual >= 0) addUsage(automatic, manual) else automatic + manual
    }
    val collator = Collator.getInstance(locale)
    val order = Comparator<AppEntry> { first, second ->
        compareValues(scores.getValue(second.packageName), scores.getValue(first.packageName))
            .takeIf { it != 0 }
            ?: collator.compare(first.label, second.label).takeIf { it != 0 }
            ?: first.packageName.compareTo(second.packageName)
    }

    return eligible.asSequence()
        .sortedWith(order)
        .take(limit)
        .toList()
}
