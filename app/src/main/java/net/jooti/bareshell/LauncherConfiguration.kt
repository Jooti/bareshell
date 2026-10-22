package net.jooti.bareshell

internal enum class Page {
    FAV_APPS, FAV_CONTACTS, ALL_APPS, ALL_CONTACTS, SEARCH, TASKS;

    fun remaining(): List<Page> = entries.filter { it != this }
}

internal enum class Gesture {
    LEFT, RIGHT, UP, DOWN, SHAKE, TWO_LEFT, TWO_RIGHT, TWO_UP, TWO_DOWN;

    fun reverse(): Gesture = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        UP -> DOWN
        DOWN -> UP
        SHAKE -> SHAKE
        TWO_LEFT -> TWO_RIGHT
        TWO_RIGHT -> TWO_LEFT
        TWO_UP -> TWO_DOWN
        TWO_DOWN -> TWO_UP
    }
}

internal data class LauncherConfiguration(val home: Page, val bindings: Map<Page, Gesture>) {
    init {
        require(home !in bindings) { "Home cannot be a destination" }
        require(bindings.values.toSet().size == bindings.size) { "Gestures must be unique" }
    }

    fun enables(page: Page): Boolean = page == home || page in bindings

    fun navigate(current: Page, gesture: Gesture): Page = if (current == home) {
        bindings.entries.firstOrNull { it.value == gesture }?.key ?: home
    } else {
        if (bindings[current]?.reverse() == gesture) home else current
    }

    fun storedValues(): Map<String, Any> = buildMap {
        put("version", 2)
        put("home", home.name)
        put("destinations", home.remaining().filter { it in bindings }.joinToString(",") { it.name })
        bindings.forEach { (page, gesture) -> put("gesture.${page.name}", gesture.name) }
    }
}

internal sealed interface ConfigurationRead {
    data object Missing : ConfigurationRead
    data object Invalid : ConfigurationRead
    data class Ready(val configuration: LauncherConfiguration) : ConfigurationRead
}

internal fun readConfiguration(
    values: Map<String, *>,
    available: Set<Gesture> = Gesture.entries.toSet(),
): ConfigurationRead {
    if (values.isEmpty()) return ConfigurationRead.Missing
    val version = values["version"]
    if (version != 1 && version != 2) return ConfigurationRead.Invalid
    val home = Page.entries.firstOrNull { it.name == values["home"] }
        ?: return ConfigurationRead.Invalid
    val destinations = if (version == 1) {
        if (home == Page.TASKS) return ConfigurationRead.Invalid
        home.remaining().filter { it != Page.TASKS }
    } else {
        val names = values["destinations"] as? String ?: return ConfigurationRead.Invalid
        val parsed = if (names.isEmpty()) emptyList() else names.split(",").map { name ->
            Page.entries.firstOrNull { it.name == name } ?: return ConfigurationRead.Invalid
        }
        if (parsed != home.remaining().filter { it in parsed }) return ConfigurationRead.Invalid
        parsed
    }
    val keys = setOf("version", "home") + (if (version == 2) setOf("destinations") else emptySet()) +
        destinations.map { "gesture.${it.name}" }
    if (values.keys != keys) {
        return ConfigurationRead.Invalid
    }
    val bindings = destinations.associateWith { page ->
        Gesture.entries.firstOrNull { it.name == values["gesture.${page.name}"] }
    }
    if (bindings.values.any { it == null || it !in available } ||
        bindings.values.toSet().size != bindings.size
    ) {
        return ConfigurationRead.Invalid
    }
    return ConfigurationRead.Ready(
        LauncherConfiguration(home, bindings.mapValues { requireNotNull(it.value) }),
    )
}

internal data class WizardProgress(
    val home: Page? = null,
    val destinations: List<Page> = emptyList(),
    val assigning: Boolean = false,
    val bindings: Map<Page, Gesture> = emptyMap(),
) {
    init {
        require(home != null || (destinations.isEmpty() && !assigning))
        require(destinations == home?.remaining()?.filter { it in destinations }.orEmpty())
        require(assigning || bindings.isEmpty())
        require(bindings.keys.toList() == destinations.take(bindings.size))
        require(bindings.size <= destinations.size)
        require(bindings.values.toSet().size == bindings.size)
    }

    val next: Page? get() = if (assigning) destinations.getOrNull(bindings.size) else null
    val complete: Boolean get() = assigning && bindings.size == destinations.size
    val stepCount: Int get() = 2 + destinations.size
    val stepNumber: Int get() = when {
        home == null -> 1
        !assigning -> 2
        else -> minOf(bindings.size + 3, stepCount)
    }

    fun toggle(page: Page, capacity: Int): WizardProgress {
        require(home != null && page != home && !assigning)
        require(page in destinations || destinations.size < capacity)
        val selected = if (page in destinations) destinations - page else destinations + page
        return copy(destinations = home.remaining().filter { it in selected })
    }

    fun start(available: Set<Gesture>): WizardProgress {
        require(home != null && destinations.size <= available.size)
        return copy(assigning = true)
    }

    fun unusedGestures(available: Set<Gesture>): List<Gesture> =
        Gesture.entries.filter { it in available && it !in bindings.values }

    fun assign(gesture: Gesture): WizardProgress {
        require(gesture !in bindings.values) { "Gesture already assigned" }
        return copy(bindings = bindings + (requireNotNull(next) to gesture))
    }

    fun back(): WizardProgress = when {
        bindings.isNotEmpty() -> copy(bindings = bindings - bindings.keys.last())
        assigning -> copy(assigning = false)
        else -> WizardProgress()
    }

    fun configuration(): LauncherConfiguration {
        check(complete)
        return LauncherConfiguration(requireNotNull(home), bindings.toMap())
    }

    companion object {
        fun choosing(home: Page, available: Set<Gesture>) =
            WizardProgress(home, home.remaining()
                .filter { it != Page.ALL_APPS && it != Page.ALL_CONTACTS }
                .take(available.size))
    }
}
