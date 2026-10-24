package net.jooti.bareshell

internal data class SearchMatches(
    val apps: List<AppEntry>,
    val contacts: List<ContactEntry>,
)

internal fun searchNames(
    query: String,
    apps: List<AppEntry>,
    contacts: List<ContactEntry>,
): SearchMatches {
    val term = query.trim()
    if (term.isEmpty()) return SearchMatches(emptyList(), emptyList())
    return SearchMatches(
        apps.filter { it.label.contains(term, ignoreCase = true) }
            .sortedBy { !it.label.equals(term, ignoreCase = true) },
        contacts.filter { it.label.contains(term, ignoreCase = true) },
    )
}
