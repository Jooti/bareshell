package net.jooti.bareshell

internal fun pageCacheKey(
    page: Page,
    settings: LauncherSettings,
    locale: String,
    usageAllowed: Boolean,
    contactsAllowed: Boolean,
): String = listOf(
    "2", locale,
    when (page) {
        Page.FAV_APPS -> "${settings.topAppsCount}:$usageAllowed"
        Page.FAV_CONTACTS -> "${settings.closeContactsCount}:$contactsAllowed"
        Page.ALL_CONTACTS, Page.SEARCH -> contactsAllowed.toString()
        Page.ALL_APPS, Page.TASKS -> ""
    },
).joinToString("|")

internal fun PageContent.cacheable(page: Page): Boolean = when (page) {
    Page.FAV_APPS, Page.ALL_APPS -> apps is AppListResult.Loaded && apps.usageAccess != UsageAccess.UNAVAILABLE
    Page.FAV_CONTACTS, Page.ALL_CONTACTS -> contacts is ContactsResult.Loaded && !contacts.historyUnavailable ||
        contacts == ContactsResult.Denied
    Page.SEARCH -> apps is AppListResult.Loaded &&
        (contacts is ContactsResult.Loaded && !contacts.historyUnavailable || contacts == ContactsResult.Denied)
    Page.TASKS -> false
}
