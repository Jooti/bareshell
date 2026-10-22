package net.jooti.bareshell

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class PageCacheStore(context: Context) {
    private val preferences = context.getSharedPreferences("page_cache", Context.MODE_PRIVATE)

    fun version(page: Page): Long = preferences.getLong("epoch.${page.name}", 0)

    fun get(page: Page, key: String): PageContent? {
        synchronized(memory) { memory[page]?.takeIf { it.first == key }?.let { return it.second } }
        val raw = try {
            preferences.getString(page.name, null)
        } catch (error: ClassCastException) {
            Log.w(TAG, "Invalid cached page type: $page", error)
            discard(page)
            return null
        } ?: return null
        try {
            val json = JSONObject(raw)
            if (json.getString("key") != key) {
                discard(page)
                return null
            }
            val apps = json.optJSONObject("apps")?.let { value ->
                val entries = value.getJSONArray("entries")
                AppListResult.Loaded((0 until entries.length()).map { index ->
                    val app = entries.getJSONObject(index)
                    AppEntry(app.getString("package"), app.getString("label"), app.getString("activity"))
                }, value.optString("usage").takeIf { it.isNotEmpty() }?.let(UsageAccess::valueOf))
            }
            val contacts = when {
                json.optBoolean("denied") -> ContactsResult.Denied
                json.has("contacts") -> json.getJSONArray("contacts").let { entries ->
                    ContactsResult.Loaded((0 until entries.length()).map { index ->
                        val contact = entries.getJSONObject(index)
                        ContactEntry(contact.getLong("id"), contact.getString("label"),
                            contact.getBoolean("starred"), contact.getString("lookup"))
                    })
                }
                else -> null
            }
            val result = PageContent(apps, contacts)
            if (!result.cacheable(page)) throw JSONException("Incomplete cached page")
            synchronized(memory) { memory[page] = key to result }
            return result
        } catch (error: JSONException) {
            Log.w(TAG, "Invalid cached page: $page", error)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Invalid cached page values: $page", error)
        }
        discard(page)
        return null
    }

    fun put(page: Page, key: String, content: PageContent) {
        if (!content.cacheable(page)) return
        synchronized(memory) { memory[page] = key to content }
        val json = JSONObject().put("key", key)
        (content.apps as? AppListResult.Loaded)?.let { apps ->
            json.put("apps", JSONObject()
                .put("usage", apps.usageAccess?.name.orEmpty())
                .put("entries", JSONArray().apply {
                    apps.apps.forEach { app ->
                        put(JSONObject().put("package", app.packageName).put("label", app.label).put("activity", app.activityName))
                    }
                }))
        }
        (content.contacts as? ContactsResult.Loaded)?.let { contacts ->
            json.put("contacts", JSONArray().apply {
                contacts.contacts.forEach { contact ->
                    put(JSONObject().put("id", contact.id).put("label", contact.label)
                        .put("starred", contact.starred).put("lookup", contact.lookupKey))
                }
            })
        }
        if (content.contacts == ContactsResult.Denied) json.put("denied", true)
        preferences.edit().putString(page.name, json.toString()).apply()
    }

    private fun discard(page: Page) {
        synchronized(memory) { memory.remove(page) }
        preferences.edit().remove(page.name).apply()
    }

    fun clear(page: Page) = synchronized(memory) {
        memory.remove(page)
        preferences.edit().remove(page.name)
            .putLong("epoch.${page.name}", version(page) + 1).apply()
    }

    fun clearContacts() {
        listOf(Page.FAV_CONTACTS, Page.ALL_CONTACTS, Page.SEARCH).forEach(::clear)
    }

    companion object {
        private const val TAG = "BareShellCache"
        private val memory = mutableMapOf<Page, Pair<String, PageContent>>()
    }
}
