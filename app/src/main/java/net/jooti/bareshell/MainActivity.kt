package net.jooti.bareshell

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.database.sqlite.SQLiteException
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.ContactsContract
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.time.ZonedDateTime

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var task: Future<*>? = null
    private var filterTask: Future<*>? = null
    private var detailsTask: Future<*>? = null
    private var detailsGeneration = 0
    private var menu = ContactMenu()
    private var generation = 0
    private var searchGeneration = 0
    private var resumed = false
    private var configuration: LauncherConfiguration? = null
    private var progress = WizardProgress()
    private var currentPage = Page.FAV_APPS
    private var searchQuery = ""
    private var searchKeyboardPending = false
    private var homePromptPending = false
    private var homePrompt: AlertDialog? = null
    private var content: PageContent? = null
    private var saving = false
    private var saveFailed = false
    private var invalidSettings = false
    private lateinit var store: ConfigurationStore
    private lateinit var apps: AppRepository
    private lateinit var contacts: ContactRepository
    private lateinit var history: ContactHistoryStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var pageCache: PageCacheStore
    private var displayedCacheKey: String? = null
    private var listSettings = LauncherSettings()
    private var invalidListSettingsReported = false
    private lateinit var appliedSettings: LauncherSettings
    private var refreshEventsRegistered = false
    private val midnight = object : Runnable {
        override fun run() {
            if (resumed) { refreshPage(); scheduleMidnight() }
        }
    }
    private val refreshEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (resumed) { reloadPreferences(); scheduleMidnight(); refreshPage() }
        }
    }
    private lateinit var gestures: GestureInput
    private lateinit var root: LinearLayout
    private lateinit var host: FrameLayout
    private var wizard: SetupWizard? = null
    private var pages: LauncherPages? = null
    private var tasksPage: TasksPage? = null
    private var taskUiState: Bundle? = null
    private val searchWork = Runnable { filterSearch() }
    private val permissionState by lazy { getSharedPreferences("permission_state", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedSettings = (SettingsStore(applicationContext).load() as? SettingsRead.Ready)?.settings ?: LauncherSettings()
        applyAppearance(appliedSettings)
        super.onCreate(savedInstanceState)
        homePromptPending = savedInstanceState?.getBoolean("home_prompt_pending") == true
        store = ConfigurationStore(applicationContext)
        apps = AppRepository(applicationContext)
        history = ContactHistoryStore(applicationContext)
        contacts = ContactRepository(applicationContext, history)
        settingsStore = SettingsStore(applicationContext)
        pageCache = PageCacheStore(applicationContext)
        loadListSettings()
        gestures = GestureInput(this, ::handleGesture, ::shakeEnabled, ::sensorUnavailable,
            { configuration != null && currentPage == configuration?.home }, ::openLauncherSettings)
        root = object : LinearLayout(this) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    gestures.startsInBlankSpace(this, event)
                } else {
                    super.onInterceptTouchEvent(event)
                }
        }.apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setBackgroundColor(appearanceColor(R.color.background))
        }
        val strip = View(this).apply {
            id = R.id.gesture_strip
            contentDescription = getString(R.string.gesture_strip)
        }
        gestures.attach(root)
        root.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))
        host = FrameLayout(this).apply { id = R.id.page_content }
        root.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.setOnApplyWindowInsetsListener { view, insets ->
            val imeVisible: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                imeVisible = insets.isVisible(WindowInsets.Type.ime())
                view.setPadding(bars.left + dp(24), bars.top, bars.right + dp(24), maxOf(bars.bottom, ime.bottom) + dp(24))
            } else {
                @Suppress("DEPRECATION")
                imeVisible = insets.systemWindowInsetBottom > dp(120)
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft + dp(24), insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight + dp(24), insets.systemWindowInsetBottom + dp(24),
                )
            }
            val stripHeight = if (currentPage == Page.SEARCH && imeVisible) dp(24) else dp(96)
            if (strip.layoutParams.height != stripHeight) {
                strip.layoutParams = strip.layoutParams.apply { height = stripHeight }
            }
            insets
        }
        val resizeMode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        window.setSoftInputMode(resizeMode or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(root)
        root.requestApplyInsets()
        searchQuery = savedInstanceState?.getString("query").orEmpty()
        taskUiState = savedInstanceState?.getBundle("tasks_state")
        when (val saved = store.load(gestures.available)) {
            is ConfigurationRead.Ready -> {
                configuration = saved.configuration
                val restored = Page.entries.firstOrNull {
                    it.name == savedInstanceState?.getString("page") && saved.configuration.enables(it)
                }
                showPage(restored ?: saved.configuration.home)
                val expandedId = savedInstanceState?.getLong("expanded_contact", -1)?.takeIf { it >= 0 }
                menu = ContactMenu(ContactExpansion(
                    expandedId, expandedId != null && savedInstanceState?.getBoolean("other_numbers") == true,
                ))
                pages?.setMenu(menu)
                pages?.restoreState(savedInstanceState)
            }
            ConfigurationRead.Missing, ConfigurationRead.Invalid -> {
                invalidSettings = saved == ConfigurationRead.Invalid
                if (invalidSettings) Log.w(TAG, "Stored launcher configuration is invalid or unsupported")
                restoreWizard(savedInstanceState)
                showWizard()
                if (progress.complete) saveConfiguration()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        val refreshes = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(ACTION_PERSONALIZATION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshEvents, refreshes, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(refreshEvents, refreshes)
        }
        refreshEventsRegistered = true
        reloadPreferences()
        scheduleMidnight()
        wizard?.setForeground(true)
        if (configuration == null || configuration?.bindings?.containsValue(Gesture.SHAKE) == true) {
            gestures.resume()
        }
        if (configuration != null) refreshPage()
        offerDefaultHome()
        showSearchKeyboard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            offerDefaultHome()
            showSearchKeyboard()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        ) {
            configuration?.let {
                hideKeyboard()
                showPage(it.home)
            }
        }
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(midnight)
        if (refreshEventsRegistered) { unregisterReceiver(refreshEvents); refreshEventsRegistered = false }
        wizard?.setForeground(false)
        gestures.pause()
        cancelLoads()
        super.onPause()
    }

    override fun onDestroy() {
        homePrompt?.dismiss()
        wizard?.dispose()
        pages?.dispose()
        tasksPage?.dispose()
        executor.submit { history.close() }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("home_prompt_pending", homePromptPending)
        outState.putString("page", currentPage.name)
        outState.putString("query", searchQuery)
        outState.putLong("expanded_contact", menu.expansion.contactId ?: -1)
        outState.putBoolean("other_numbers", menu.expansion.otherNumbers)
        pages?.saveState(outState)
        tasksPage?.let { page -> taskUiState = Bundle().also(page::saveState) }
        outState.putBundle("tasks_state", taskUiState)
        progress.home?.let { outState.putString("wizard_home", it.name) }
        outState.putStringArrayList("wizard_destinations", ArrayList(progress.destinations.map { it.name }))
        outState.putBoolean("wizard_assigning", progress.assigning)
        progress.bindings.forEach { (page, gesture) -> outState.putString("wizard_${page.name}", gesture.name) }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Used only before Android 13")
    @SuppressLint("GestureBackNavigation") // Android 13+ uses the platform callback registered above.
    override fun onBackPressed() {
        handleBack()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACTS_REQUEST) {
            if (grantResults.isNotEmpty()) permissionState.edit().putBoolean("contacts_requested", true).apply()
            if (configuration != null && resumed) refreshPage()
        }
    }

        private fun reloadPreferences() {
            loadListSettings()
            if (listSettings.appearanceTheme != appliedSettings.appearanceTheme ||
                listSettings.appearanceFont != appliedSettings.appearanceFont) {
                recreate()
                return
            }
            if (configuration != null && store.load(gestures.available) !is ConfigurationRead.Ready) beginWizard()
        }

        private fun loadListSettings() {
            when (val result = settingsStore.load()) {
                is SettingsRead.Ready -> { listSettings = result.settings; invalidListSettingsReported = false }
                SettingsRead.Invalid -> {
                    listSettings = LauncherSettings()
                    if (!invalidListSettingsReported) {
                        Toast.makeText(this, R.string.settings_invalid, Toast.LENGTH_LONG).show()
                        invalidListSettingsReported = true
                    }
                }
            }
        }

        private fun scheduleMidnight() {
            handler.removeCallbacks(midnight)
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            handler.postDelayed(midnight, (next - System.currentTimeMillis()).coerceAtLeast(1))
        }

        private fun beginWizard() {
            cancelLoads()
            pages?.dispose()
            pages = null
            rememberTasks()
            wizard?.dispose()
            wizard = null
            configuration = null
            progress = WizardProgress()
            menu = ContactMenu()
            content = null
            saving = false
            saveFailed = false
            invalidSettings = false
            hideKeyboard()
            loadListSettings()
            showWizard()
            if (resumed) gestures.resume()
        }

        @Suppress("DEPRECATION")
        private fun openLauncherSettings() {
            if (configuration == null || currentPage != configuration?.home) return
            val page = tasksPage
            if (page != null) page.requestLeave { openLauncherSettingsNow() } else openLauncherSettingsNow()
        }

        @Suppress("DEPRECATION")
        private fun openLauncherSettingsNow() {
            hideKeyboard()
            closeContact()
            try {
                startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST)
            } catch (error: ActivityNotFoundException) {
                Log.e(TAG, "Launcher settings are unavailable", error)
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
            } catch (error: SecurityException) {
                Log.e(TAG, "Launcher settings access was denied", error)
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
            }
        }

        @Deprecated("Native result API is used without an additional framework dependency")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == SETTINGS_REQUEST && resultCode == SettingsActivity.RESULT_RESET) beginWizard()
        }

    private fun restoreWizard(state: Bundle?) {
        if (state == null) return
        val home = Page.entries.firstOrNull { it.name == state?.getString("wizard_home") } ?: return
        val names = state.getStringArrayList("wizard_destinations").orEmpty()
        val destinations = names.mapNotNull { name -> home.remaining().firstOrNull { it.name == name } }
        if (destinations.map { it.name } != names || destinations != home.remaining().filter { it in destinations } ||
            destinations.size > gestures.available.size) {
            Log.w(TAG, "Discarding invalid or unavailable wizard selections")
            progress = WizardProgress(home)
            return
        }
        var restored = WizardProgress(home, destinations, state.getBoolean("wizard_assigning"))
        for (page in if (restored.assigning) destinations else emptyList()) {
            val name = state.getString("wizard_${page.name}") ?: break
            val gesture = gestures.available.firstOrNull { it.name == name }
            if (gesture == null || gesture in restored.bindings.values) {
                Log.w(TAG, "Discarding invalid in-progress wizard state")
                progress = WizardProgress(home, destinations)
                return
            }
            restored = restored.assign(gesture)
        }
        progress = restored
    }

    private fun showWizard() {
        root.isLongClickable = false
        root.findViewById<View>(R.id.gesture_strip).visibility = View.GONE
        if (wizard == null) {
            wizard = SetupWizard(this, { home ->
                progress = WizardProgress.choosing(home, gestures.available)
                showWizard()
            }, ::saveConfiguration, ::handleBack, { page ->
                progress = progress.toggle(page, gestures.available.size)
                showWizard()
            }, {
                progress = progress.start(gestures.available)
                if (progress.complete) saveConfiguration() else showWizard()
            }, listSettings)
            host.removeAllViews()
            host.addView(requireNotNull(wizard).view)
        }
        wizard?.show(progress, gestures.available, invalidSettings, saving, saveFailed)
        wizard?.setForeground(resumed)
    }

    private fun saveConfiguration() {
        if (saving || !progress.complete) return
        saving = true
        saveFailed = false
        showWizard()
        val selected = progress.configuration()
        executor.submit {
            val saved = store.save(selected)
            if (!saved) Log.e(TAG, "Launcher configuration could not be committed")
            runOnUiThread {
                if (!isDestroyed) {
                    saving = false
                    if (saved) {
                        configuration = selected
                        if (!selected.bindings.containsValue(Gesture.SHAKE)) gestures.pause()
                        wizard?.dispose()
                        wizard = null
                        homePromptPending = true
                        showPage(selected.home)
                        offerDefaultHome()
                    } else {
                        saveFailed = true
                        showWizard()
                    }
                }
            }
        }
    }

    private fun offerDefaultHome() {
        if (!homePromptPending || !resumed || !hasWindowFocus() || configuration == null ||
            isFinishing || isDestroyed || homePrompt != null) return
        if (isDefaultHome()) {
            homePromptPending = false
            return
        }
        homePrompt = AlertDialog.Builder(this)
            .setTitle(R.string.default_home_prompt_title)
            .setMessage(R.string.default_home_prompt_message)
            .setPositiveButton(R.string.default_home_set) { _, _ ->
                homePromptPending = false
                requestDefaultHome(DEFAULT_HOME_REQUEST)
            }
            .setNegativeButton(R.string.default_home_not_now) { _, _ -> homePromptPending = false }
            .setOnCancelListener { homePromptPending = false }
            .show().apply {
                setOnDismissListener {
                    homePrompt = null
                    showSearchKeyboard()
                }
            }
    }

    private fun showPage(page: Page) {
        val settings = configuration ?: return
        if (!settings.enables(page)) {
            Log.w(TAG, "Attempt to open disabled page: $page")
            return
        }
        val tasks = tasksPage
        if (tasks != null) tasks.requestLeave { showPageNow(page) } else showPageNow(page)
    }

    private fun rememberTasks() {
        tasksPage?.let { page ->
            taskUiState = Bundle().also(page::saveState)
            page.dispose()
        }
        tasksPage = null
    }

    private fun showPageNow(page: Page) {
        root.isLongClickable = page == configuration?.home
        root.findViewById<View>(R.id.gesture_strip).visibility = View.VISIBLE
        cancelLoads()
        pages?.dispose()
        pages = null
        rememberTasks()
        menu = ContactMenu()
        if (currentPage == Page.SEARCH || currentPage == Page.TASKS) hideKeyboard()
        currentPage = page
        searchKeyboardPending = page == Page.SEARCH
        content = null
        displayedCacheKey = null
        if (page == Page.TASKS) {
            val tasks = TasksPage(this, taskUiState)
            tasksPage = tasks
            host.removeAllViews()
            host.addView(tasks.view)
            root.requestFocus()
            return
        }
        pages = LauncherPages(
            this, page, searchQuery, ::launchApp, ::openUsageSettings, ::allowContacts,
            ::contactSettingsNeeded, ::refreshPage,
            ::selectContact, ::toggleOtherNumbers, ::loadContactDetails, ::launchContactAction,
        ) { query ->
            searchQuery = query
            closeContact()
            searchGeneration++
            filterTask?.cancel(true)
            handler.removeCallbacks(searchWork)
            handler.postDelayed(searchWork, 150)
        }
        showCachedPage()
        host.removeAllViews()
        host.addView(requireNotNull(pages).view)
        if (page == Page.SEARCH) {
            host.findViewById<EditText>(R.id.search_input).requestFocus()
            showSearchKeyboard()
        } else root.requestFocus()
        if (resumed) refreshPage()
    }

    private fun refreshPage() {
        if (configuration == null) return
        if (currentPage == Page.TASKS) {
            tasksPage?.refresh()
            return
        }
        showCachedPage()
        cancelLoads()
        if (currentPage == Page.SEARCH && content != null) filterSearch()
        if (menu.expansion.contactId != null) {
            updateMenu(menu.copy(refreshing = menu.result is ContactDetailsResult.Loaded))
        }
        val request = generation
        val page = currentPage
        val counts = listSettings
        val cacheKey = cacheKey(page)
        task = executor.submit {
            val result = PageContent(
                apps = if (page == Page.FAV_APPS || page == Page.ALL_APPS || page == Page.SEARCH) apps.load(page == Page.FAV_APPS, counts.topAppsCount) else null,
                contacts = if (page == Page.FAV_CONTACTS || page == Page.ALL_CONTACTS || page == Page.SEARCH) {
                    contacts.load(favoritesOnly = page == Page.FAV_CONTACTS, starredFirst = page == Page.ALL_CONTACTS, limit = counts.closeContactsCount)
                } else null,
            )
            if (!Thread.currentThread().isInterrupted) pageCache.put(page, cacheKey, result)
            runOnUiThread {
                if (resumed && !isDestroyed && generation == request && currentPage == page) {
                    if (cacheKey != cacheKey(page)) {
                        refreshPage()
                        return@runOnUiThread
                    }
                    content = result
                    displayedCacheKey = cacheKey
                    val available = (result.contacts as? ContactsResult.Loaded)?.contacts.orEmpty()
                    if (menu.expansion.contactId != null && available.none { it.id == menu.expansion.contactId }) {
                        closeContact()
                    }
                    if (page == Page.SEARCH) {
                        filterSearch()
                    } else {
                        pages?.show(result)
                        if (menu.expansion.contactId != null) loadContactDetails()
                    }
                }
            }
        }
    }

    private fun cacheKey(page: Page): String = pageCacheKey(
        page, listSettings, resources.configuration.locales.toLanguageTags(),
        page == Page.FAV_APPS && apps.hasUsageAccess(),
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
    ) + "|${pageCache.version(page)}"

    private fun showCachedPage() {
        val key = cacheKey(currentPage)
        val cached = pageCache.get(currentPage, key)
        if (content != null && displayedCacheKey == key && cached != null) return
        displayedCacheKey = key
        content = cached
        if (menu.expansion.contactId != null) closeContact()
        if (cached == null) {
            pages?.showLoading()
        } else {
            val search = cachedSearch?.takeIf { it.first === cached && it.second == searchQuery }?.third
            pages?.show(cached, search, searchQuery)
        }
    }

    private fun filterSearch() {
        val data = content ?: return
        if (!resumed || currentPage != Page.SEARCH) return
        val request = ++searchGeneration
        val pageRequest = generation
        val query = searchQuery
        cachedSearch?.takeIf { it.first === data && it.second == query }?.let {
            pages?.show(data, it.third, query)
            if (menu.expansion.contactId != null) loadContactDetails()
            return
        }
        filterTask?.cancel(true)
        filterTask = executor.submit {
            val matches = searchNames(
                query,
                (data.apps as? AppListResult.Loaded)?.apps.orEmpty(),
                (data.contacts as? ContactsResult.Loaded)?.contacts.orEmpty(),
            )
            runOnUiThread {
                if (resumed && !isDestroyed && currentPage == Page.SEARCH &&
                    request == searchGeneration && pageRequest == generation
                ) {
                    if (menu.expansion.contactId != null && matches.contacts.none { it.id == menu.expansion.contactId }) {
                        closeContact()
                    }
                    pages?.show(data, matches, query)
                    cachedSearch = Triple(data, query, matches)
                    if (menu.expansion.contactId != null) loadContactDetails()
                }
            }
        }
    }

    private fun cancelLoads() {
        generation++
        searchGeneration++
        task?.cancel(true)
        filterTask?.cancel(true)
        detailsGeneration++
        detailsTask?.cancel(true)
        handler.removeCallbacks(searchWork)
    }

    private fun handleGesture(gesture: Gesture) {
        val settings = configuration
        if (settings == null) {
            if (saving || wizard?.inputReady != true || !progress.assigning || progress.complete) return
            if (gesture in progress.bindings.values) {
                val owner = progress.bindings.entries.first { it.value == gesture }.key
                wizard?.showGestureUsed(gesture, owner)
                return
            }
            progress = progress.assign(gesture)
            if (progress.complete) saveConfiguration() else showWizard()
        } else {
            val destination = settings.navigate(currentPage, gesture)
            if (destination != currentPage) showPage(destination)
        }
    }

    private fun shakeEnabled(): Boolean = if (configuration == null) {
        wizard?.inputReady == true && progress.assigning && !progress.complete && !saving
    } else {
        configuration?.bindings?.containsValue(Gesture.SHAKE) == true
    }

    private fun sensorUnavailable() {
        if (configuration?.bindings?.containsValue(Gesture.SHAKE) == true) {
            Log.w(TAG, "Saved shake binding is unavailable; setup is required")
            configuration = null
            progress = WizardProgress()
            pages?.dispose()
            pages = null
            rememberTasks()
            invalidSettings = true
            hideKeyboard()
        }
        if (configuration == null &&
            (progress.destinations.size > gestures.available.size || Gesture.SHAKE in progress.bindings.values)) {
            progress = progress.home?.let { WizardProgress.choosing(it, gestures.available) } ?: WizardProgress()
            Toast.makeText(this, R.string.wizard_capacity_changed, Toast.LENGTH_LONG).show()
        }
        if (configuration == null) showWizard()
    }

    private fun handleBack() {
        if (saving) return
        if (configuration == null) {
            if (wizard?.inputReady == true) {
                progress = progress.back()
                saveFailed = false
                showWizard()
            }
        } else if (keyboardVisible()) {
            hideKeyboard()
        } else if (tasksPage?.back() == true) {
            return
        } else if (menu.expansion.contactId != null) {
            if (menu.expansion.otherNumbers) {
                updateMenu(menu.copy(expansion = menu.expansion.back()), true)
            } else closeContact(true)
        } else {
            configuration?.let { if (currentPage != it.home) showPage(it.home) }
        }
    }

        private fun updateMenu(next: ContactMenu, animate: Boolean = false) {
            if (next == menu) return
            menu = next
            pages?.setMenu(next, animate)
        }

        private fun closeContact(animate: Boolean = false) {
            detailsGeneration++
            detailsTask?.cancel(true)
            updateMenu(ContactMenu(), animate)
        }

        private fun selectContact(contact: ContactEntry) {
            val expansion = menu.expansion.toggle(contact.id)
            if (expansion.contactId == null) {
                closeContact(true)
            } else {
                recordInteraction(contact)
                updateMenu(ContactMenu(expansion), true)
                loadContactDetails()
            }
        }

    private fun recordInteraction(contact: ContactEntry) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Contacts access was revoked before scoring an interaction")
            Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_LONG).show()
            return
        }
        if (contact.lookupKey.isBlank()) {
            Log.w(TAG, "Cannot score a contact without lookup identity")
            Toast.makeText(this, R.string.contact_history_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        val occurredAt = ZonedDateTime.now()
        val now = occurredAt.toInstant().toEpochMilli()
        val day = occurredAt.toLocalDate().toEpochDay()
        val version = history.version
        executor.submit {
            try {
                history.record(contact.lookupKey, now, day, version)
            } catch (error: SQLiteException) {
                Log.e(TAG, "Could not persist a contact interaction", error)
                runOnUiThread { if (!isDestroyed) Toast.makeText(this, R.string.contact_history_save_failed, Toast.LENGTH_LONG).show() }
            } catch (error: SecurityException) {
                Log.e(TAG, "Contact history access was denied", error)
                runOnUiThread { if (!isDestroyed) Toast.makeText(this, R.string.contact_history_save_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

        private fun toggleOtherNumbers() {
            if (menu.expansion.contactId != null) {
                updateMenu(menu.copy(expansion = menu.expansion.copy(otherNumbers = !menu.expansion.otherNumbers)), true)
            }
        }

        private fun loadContactDetails() {
            val contactId = menu.expansion.contactId ?: return
            detailsTask?.cancel(true)
            val request = ++detailsGeneration
            val page = currentPage
            val query = searchQuery
            val loaded = menu.result is ContactDetailsResult.Loaded
            updateMenu(menu.copy(
                result = if (loaded) menu.result else ContactDetailsResult.Loading,
                refreshing = loaded,
            ))
            detailsTask = executor.submit {
                val result = contacts.loadDetails(contactId)
                runOnUiThread {
                    if (resumed && !isDestroyed && request == detailsGeneration && page == currentPage &&
                        query == searchQuery && menu.expansion.contactId == contactId
                    ) {
                        if (result == ContactDetailsResult.Denied) {
                            closeContact()
                            refreshPage()
                        } else {
                            val hasOthers = result is ContactDetailsResult.Loaded && result.details.phones.size > 1
                            updateMenu(menu.copy(
                                expansion = menu.expansion.copy(otherNumbers = menu.expansion.otherNumbers && hasOthers),
                                result = result, refreshing = false,
                            ), true)
                            if (result == ContactDetailsResult.Missing) refreshPage()
                        }
                    }
                }
            }
        }

        private fun launchContactAction(action: ContactAction) {
            val details = menu.result as? ContactDetailsResult.Loaded
            if (menu.refreshing || menu.expansion.contactId == null || details?.details?.contains(action) != true) {
                Log.w(TAG, "A stale contact action was rejected")
                Toast.makeText(this, R.string.contact_action_unavailable, Toast.LENGTH_LONG).show()
                return
            }
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Contacts access was revoked before opening an action")
                closeContact()
                refreshPage()
                Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_LONG).show()
                return
            }
            val intent = when (action) {
                is ContactAction.View -> Intent(Intent.ACTION_VIEW).setDataAndType(
                    contactViewUri(action.contact), ContactsContract.Contacts.CONTENT_ITEM_TYPE,
                )
                is ContactAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.phone.number, null))
                is ContactAction.Sms -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.phone.number, null))
                is ContactAction.Messenger -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.target.dataId),
                        action.target.kind.mimeType,
                    )
                    component = ComponentName(action.target.kind.packageName, action.target.activityName)
                }
            }
            try {
                val activity = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                if (activity == null || !activity.enabled || !activity.applicationInfo.enabled || !activity.exported ||
                    (activity.permission != null && checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED)
                ) {
                    throw ActivityNotFoundException("No available contact action handler")
                }
                startActivity(intent)
                details.details.contact?.let(::recordInteraction)
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "Contact action handler is unavailable")
                Toast.makeText(this, R.string.contact_action_unavailable, Toast.LENGTH_LONG).show()
                loadContactDetails()
            } catch (error: SecurityException) {
                Log.w(TAG, "Contact action handler denied access")
                Toast.makeText(this, R.string.contact_action_unavailable, Toast.LENGTH_LONG).show()
                loadContactDetails()
            }
        }

    private fun keyboardVisible(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        root.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    } else {
        val visible = Rect()
        root.getWindowVisibleDisplayFrame(visible)
        root.rootView.height - visible.bottom > dp(120)
    }

    private fun hideKeyboard() {
        searchKeyboardPending = false
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
        root.requestFocus()
    }

    private fun showSearchKeyboard() {
        if (!searchKeyboardPending || !resumed || !hasWindowFocus() || homePromptPending || homePrompt != null) return
        val input = host.findViewById<EditText>(R.id.search_input) ?: return
        input.requestFocus()
        input.post {
            if (searchKeyboardPending && resumed && currentPage == Page.SEARCH &&
                input.isAttachedToWindow && input.hasWindowFocus() && input.hasFocus()
            ) {
                input.selectAll()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    input.windowInsetsController?.show(WindowInsets.Type.ime())
                } else {
                    getSystemService(InputMethodManager::class.java)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
                searchKeyboardPending = false
            }
        }
    }

    private fun contactSettingsNeeded(): Boolean =
        permissionState.getBoolean("contacts_requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)

    private fun allowContacts() {
        if (contactSettingsNeeded()) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_REQUEST)
        }
    }

    private fun openUsageSettings() = openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Requested system settings are unavailable", error)
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e(TAG, "Opening system settings was blocked", error)
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun launchApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "Application is no longer available: ${app.packageName}", error)
            Toast.makeText(this, getString(R.string.app_unavailable, app.label), Toast.LENGTH_LONG).show()
            refreshPage()
        } catch (error: SecurityException) {
            Log.w(TAG, "Application launch was blocked: ${app.packageName}", error)
            Toast.makeText(this, getString(R.string.app_unavailable, app.label), Toast.LENGTH_LONG).show()
            refreshPage()
        }
    }

    private companion object {
        var cachedSearch: Triple<PageContent, String, SearchMatches>? = null
        const val TAG = "BareShell"
        const val CONTACTS_REQUEST = 1
        const val SETTINGS_REQUEST = 2
        const val DEFAULT_HOME_REQUEST = 3
    }
}
