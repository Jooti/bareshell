package net.jooti.bareshell

import java.text.Collator
import java.util.Locale

internal data class ContactEntry(val id: Long, val label: String, val starred: Boolean = false, val lookupKey: String = "")

internal data class ContactSection(val label: String, val position: Int)

internal fun favoriteContacts(
    contacts: List<ContactEntry>,
    locale: Locale = Locale.getDefault(),
    limit: Int = 10,
    scores: Map<String, ContactScore> = emptyMap(),
): List<ContactEntry> {
    require(limit in listCountOptions)
    val ordered = orderContacts(contacts, locale = locale)
    val stars = ordered.filter { it.starred }
    val recent = ordered.filter { !it.starred && (scores[it.lookupKey]?.points ?: 0) > 0 }
        .sortedWith(compareByDescending<ContactEntry> { scores.getValue(it.lookupKey).points }
            .thenByDescending { scores.getValue(it.lookupKey).lastInteraction })
    return (stars + recent).take(limit)
}

internal fun alphabetSections(labels: List<String>, bucketLabel: (String) -> String): List<ContactSection> {
    val seen = mutableSetOf<String>()
    return labels.mapIndexedNotNull { position, label ->
        val bucket = bucketLabel(label)
        if (seen.add(bucket)) ContactSection(bucket, position) else null
    }
}

internal fun orderContacts(
    contacts: List<ContactEntry>,
    starredFirst: Boolean = false,
    locale: Locale = Locale.getDefault(),
): List<ContactEntry> {
    val collator = Collator.getInstance(locale)
    return contacts.distinctBy { it.id }.sortedWith { first, second ->
        val favoriteOrder = if (starredFirst) second.starred.compareTo(first.starred) else 0
        favoriteOrder.takeIf { it != 0 }
            ?: collator.compare(first.label, second.label).takeIf { it != 0 }
            ?: first.id.compareTo(second.id)
    }
}

internal fun contactSections(
    contacts: List<ContactEntry>,
    favoritesLabel: String,
    bucketLabel: (String) -> String,
): List<ContactSection> {
    val sections = mutableListOf<ContactSection>()
    val favorite = contacts.indexOfFirst { it.starred }
    if (favorite >= 0) sections += ContactSection(favoritesLabel, favorite)
    val seen = mutableSetOf<String>()
    contacts.forEachIndexed { position, contact ->
        if (!contact.starred) {
            val label = bucketLabel(contact.label)
            if (seen.add(label)) sections += ContactSection(label, position)
        }
    }
    return sections
}
