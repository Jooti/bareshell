package net.jooti.bareshell

internal data class ForegroundEvent(val timestamp: Long, val packageName: String?)

internal fun countForegroundEntries(
    events: List<ForegroundEvent>,
    start: Long,
    end: Long,
): Map<String, Long> {
    var previous: String? = null
    val counts = mutableMapOf<String, Long>()
    events.filter { it.timestamp in start until end }.sortedBy { it.timestamp }.forEach { event ->
        val name = event.packageName?.takeIf { it.isNotBlank() }
        if (name != null && name != previous) counts[name] = (counts[name] ?: 0L) + 1
        previous = name
    }
    return counts
}
