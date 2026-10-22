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
): List<AppEntry> {
    require(limit in listCountOptions)
    val totals = usage.groupingBy { it.packageName }
        .fold(0L) { total, record -> total + record.milliseconds }
    val eligible = allApps(apps, ownPackageName, locale)
    fun percentiles(values: Map<String, Long>): Map<String, Double> {
        val positive = eligible.mapNotNull { values[it.packageName]?.takeIf { value -> value > 0 } }.sorted()
        return eligible.associate { app ->
            val value = values[app.packageName] ?: 0
            app.packageName to if (value <= 0 || positive.isEmpty()) 0.0 else {
                positive.count { it <= value }.toDouble() / positive.size
            }
        }
    }
    val frequency = percentiles(opens)
    val time = percentiles(totals)
    val scores = eligible.associate { it.packageName to (frequency.getValue(it.packageName) + time.getValue(it.packageName)) / 2 }
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
