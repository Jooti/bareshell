package net.jooti.bareshell

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors

internal const val ACTION_PERSONALIZATION_CHANGED = "net.jooti.bareshell.PERSONALIZATION_CHANGED"

internal class SettingsWork(context: Context) {
    private val app = context.applicationContext
    private val store = SettingsStore(context)
    private val configuration = ConfigurationStore(context)
    private val history = ContactHistoryStore(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    var settings = LauncherSettings()
        private set
    var status = 0
        private set
    var busy = false
        private set
    var resetCompleted = false
        private set
    var listener: (() -> Unit)? = null

    init {
        when (val result = store.load()) {
            is SettingsRead.Ready -> settings = result.settings
            SettingsRead.Invalid -> status = R.string.settings_invalid
        }
    }

    private fun perform(message: Int, operation: () -> Boolean, success: () -> Unit = {}) {
        if (busy) return
        busy = true
        status = R.string.settings_saving
        listener?.invoke()
        executor.submit {
            val saved = try {
                operation()
            } catch (error: SQLiteException) {
                Log.e("BareShellSettings", "Contact history operation failed", error)
                false
            } catch (error: SecurityException) {
                Log.e("BareShellSettings", "Settings operation was denied", error)
                false
            }
            handler.post {
                busy = false
                status = if (saved) message else R.string.settings_save_failed
                if (saved) {
                    success()
                    app.sendBroadcast(Intent(ACTION_PERSONALIZATION_CHANGED).setPackage(app.packageName))
                }
                listener?.invoke()
            }
        }
    }

    fun save(next: LauncherSettings) = perform(R.string.settings_saved, { store.save(next) }) { settings = next }
    fun reset() = perform(R.string.settings_saved, { configuration.reset() }) { resetCompleted = true }
    fun clearHistory() = perform(R.string.history_cleared, {
        history.clearHistory()
        PageCacheStore(app).clearContacts()
        true
    })

    fun close() {
        listener = null
        executor.submit { history.close() }
        executor.shutdown()
    }
}

class SettingsActivity : Activity() {
    private lateinit var work: SettingsWork
    private lateinit var body: LinearLayout
    private lateinit var appliedSettings: LauncherSettings
    private lateinit var scroll: ScrollView
    private var homeRequestInFlight = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        appliedSettings = (SettingsStore(applicationContext).load() as? SettingsRead.Ready)?.settings ?: LauncherSettings()
        applyAppearance(appliedSettings)
        super.onCreate(savedInstanceState)
        homeRequestInFlight = savedInstanceState?.getBoolean("home_request_in_flight") == true
        work = lastNonConfigurationInstance as? SettingsWork ?: SettingsWork(applicationContext)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appearanceColor(R.color.background))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
            setBackgroundColor(appearanceColor(R.color.background))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left + dp(24), bars.top, bars.right + dp(24), bars.bottom + dp(24))
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft + dp(24), insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight + dp(24), insets.systemWindowInsetBottom + dp(24))
                }
                insets
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        scroll.post { scroll.scrollTo(0, savedInstanceState?.getInt("settings_scroll") ?: 0) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("home_request_in_flight", homeRequestInFlight)
        outState.putInt("settings_scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        work.listener = ::render
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Uses the native Activity result API without additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DEFAULT_HOME_REQUEST) {
            homeRequestInFlight = false
            render()
        }
    }

    override fun onStop() {
        work.listener = null
        super.onStop()
    }

    @Deprecated("Retains only application-context work, never an Activity or its views")
    override fun onRetainNonConfigurationInstance(): Any = work

    override fun onDestroy() {
        if (!isChangingConfigurations) work.close()
        super.onDestroy()
    }

    private fun render() {
        if (work.settings.appearanceTheme != appliedSettings.appearanceTheme ||
            work.settings.appearanceFont != appliedSettings.appearanceFont) {
            recreate()
            return
        }
        if (work.resetCompleted) {
            setResult(RESULT_RESET)
            finish()
            return
        }
        body.removeAllViews()
        body.addView(textLabel(getString(R.string.settings_title), R.id.settings_title, true))
        body.addView(textLabel(getString(R.string.wizard_back), R.id.settings_back).apply {
            onTextClick { finish() }
        })
        fun action(text: String, id: Int, click: () -> Unit) {
            body.addView(textLabel(text, id, true).apply {
                isEnabled = !work.busy
                onTextClick { if (!work.busy) click() }
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        action(getString(R.string.settings_top_count, work.settings.topAppsCount), R.id.settings_top_count) { count(true) }
        action(getString(R.string.settings_close_count, work.settings.closeContactsCount), R.id.settings_close_count) { count(false) }
        val defaultHome = isDefaultHome()
        action(getString(if (defaultHome) R.string.default_home_current else R.string.default_home_set), R.id.settings_default_home) {
            if (!homeRequestInFlight && !defaultHome) {
                homeRequestInFlight = requestDefaultHome(DEFAULT_HOME_REQUEST)
                render()
            }
        }
        body.findViewById<android.view.View>(R.id.settings_default_home).isEnabled =
            !work.busy && !homeRequestInFlight && !defaultHome
        body.addView(textLabel(getString(R.string.appearance_title), R.id.appearance_heading, true).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
        })
        fun appearanceGroup(title: Int, headingId: Int, description: Int, descriptionId: Int) {
            body.addView(textLabel(getString(title), headingId).apply {
                textSize = 20f
                setPadding(paddingLeft, dp(32), paddingRight, paddingBottom)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
            })
            body.addView(textLabel(getString(description), descriptionId))
        }
        appearanceGroup(R.string.theme_title, R.id.theme_heading, R.string.theme_explanation, R.id.theme_explanation)
        AppearanceTheme.entries.forEach { option ->
            val palette = option.palette(isNightAppearance())
            body.addView(textLabel(
                getString(R.string.theme_preview,
                    if (option == AppearanceTheme.SYSTEM) getString(
                        R.string.theme_system_resolved, getString(if (isNightAppearance()) R.string.theme_dark else R.string.theme_light),
                    ) else getString(option.labelRes),
                    getString(if (option == work.settings.appearanceTheme) R.string.option_selected else R.string.option_choose)),
                R.id.theme_option,
            ).apply {
                setTextColor(palette.foreground)
                isSelected = option == work.settings.appearanceTheme
                isEnabled = !work.busy
                onTextClick { if (!work.busy && option != work.settings.appearanceTheme) work.save(work.settings.copy(appearanceTheme = option)) }
                foreground = background
                setBackgroundColor(palette.background)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }
        appearanceGroup(R.string.font_title, R.id.font_heading, R.string.font_explanation, R.id.font_explanation)
        AppearanceFont.entries.forEach { option ->
            body.addView(textLabel(
                getString(R.string.font_preview, getString(option.labelRes),
                    getString(if (option == work.settings.appearanceFont) R.string.option_selected else R.string.option_choose)),
                R.id.font_option,
            ).apply {
                typeface = option.typeface
                isSelected = option == work.settings.appearanceFont
                isEnabled = !work.busy
                onTextClick { if (!work.busy && option != work.settings.appearanceFont) work.save(work.settings.copy(appearanceFont = option)) }
            })
        }
        action(getString(R.string.reset_wizard), R.id.settings_reset) {
            confirm(R.string.reset_wizard, R.string.reset_wizard_explanation, work::reset)
        }
        action(getString(R.string.clear_contact_history), R.id.settings_clear_history) {
            confirm(R.string.clear_contact_history, R.string.clear_history_explanation, work::clearHistory)
        }
        body.addView(textLabel(getString(R.string.contact_history_explanation), R.id.settings_explanation))
        if (work.status != 0) body.addView(textLabel(getString(work.status), R.id.settings_status).apply {
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
    }

    private fun count(apps: Boolean) {
        val current = if (apps) work.settings.topAppsCount else work.settings.closeContactsCount
        AlertDialog.Builder(this)
            .setTitle(if (apps) R.string.top_apps_count else R.string.close_contacts_count)
            .setSingleChoiceItems(listCountOptions.map(Int::toString).toTypedArray(), listCountOptions.indexOf(current)) { dialog, which ->
                if (!work.busy) {
                    val value = listCountOptions[which]
                    work.save(if (apps) work.settings.copy(topAppsCount = value) else work.settings.copy(closeContactsCount = value))
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirm(title: Int, message: Int, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> if (!work.busy) action() }
            .show()
    }

    companion object {
        const val RESULT_RESET = RESULT_FIRST_USER
        private const val DEFAULT_HOME_REQUEST = 1
    }
}
