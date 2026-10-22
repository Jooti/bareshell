package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCacheTest {
    private val settings = LauncherSettings()
    private fun key(
        page: Page, counts: LauncherSettings = settings, locale: String = "en-US",
        usage: Boolean = true, contacts: Boolean = true,
    ) = pageCacheKey(page, counts, locale, usage, contacts)

    @Test fun topAppsCacheChangesWithCountUsagePermissionAndLocale() {
        val old = key(Page.FAV_APPS)
        assertNotEquals(old, key(Page.FAV_APPS, counts = LauncherSettings(topAppsCount = 5)))
        assertNotEquals(old, key(Page.FAV_APPS, usage = false))
        assertNotEquals(old, key(Page.FAV_APPS, locale = "fa"))
        assertEquals(old, key(Page.FAV_APPS, contacts = false))
    }

    @Test fun allContactSurfacesInvalidateWhenPermissionChanges() {
        listOf(Page.FAV_CONTACTS, Page.ALL_CONTACTS, Page.SEARCH).forEach { page ->
            assertNotEquals(key(page), key(page, contacts = false))
        }
        assertNotEquals(key(Page.FAV_CONTACTS), key(Page.FAV_CONTACTS, counts = LauncherSettings(closeContactsCount = 5)))
    }

    @Test fun fullCatalogCachesIgnoreUnrelatedCountAndUsageChanges() {
        listOf(Page.ALL_APPS, Page.ALL_CONTACTS, Page.SEARCH).forEach { page ->
            assertEquals(key(page), key(page, counts = LauncherSettings(5, 5), usage = false))
        }
    }

    @Test fun successfulEmptyListsAreValidCacheEntries() {
        assertTrue(PageContent(apps = AppListResult.Loaded(emptyList())).cacheable(Page.FAV_APPS))
        assertTrue(PageContent(contacts = ContactsResult.Loaded(emptyList())).cacheable(Page.ALL_CONTACTS))
        assertTrue(PageContent(AppListResult.Loaded(emptyList()), ContactsResult.Denied).cacheable(Page.SEARCH))
    }

    @Test fun failuresNeverReplaceSuccessfulSnapshots() {
        assertFalse(PageContent(apps = AppListResult.Unavailable).cacheable(Page.FAV_APPS))
        assertFalse(PageContent(apps = AppListResult.Loaded(emptyList(), UsageAccess.UNAVAILABLE)).cacheable(Page.FAV_APPS))
        assertFalse(PageContent(contacts = ContactsResult.Unavailable).cacheable(Page.ALL_CONTACTS))
        assertFalse(PageContent(contacts = ContactsResult.Loaded(emptyList(), true)).cacheable(Page.FAV_CONTACTS))
        assertFalse(PageContent(AppListResult.Loaded(emptyList()), ContactsResult.Unavailable).cacheable(Page.SEARCH))
        assertFalse(PageContent().cacheable(Page.TASKS))
    }
}
