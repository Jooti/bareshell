package net.jooti.bareshell

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView

/** Dependency-free device checks. Run only against an isolated test installation. */
class FirstUseInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        try {
            persistence()
            failedWrites()
            overlayIsolation()
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "First-use persistence and overlay checks passed") })
        } catch (error: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", Log.getStackTraceString(error)) })
        }
    }

    private fun preferences() = targetContext.getSharedPreferences("launcher_configuration", Context.MODE_PRIVATE)
    private val available = Gesture.entries.toSet()

    private fun persistence() {
        check(preferences().edit().clear().commit())
        val store = ConfigurationStore(targetContext)
        check(store.load(available) == ConfigurationRead.Missing)
        check(store.initialize())
        check(ConfigurationStore(targetContext).load(available) ==
            ConfigurationRead.Ready(defaultConfiguration, introductionPending = true))
        check(store.initialize())
        check(store.finishIntroduction())
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration, defaultHomePending = true))
        check(store.dismissDefaultHomeOffer())
        check(store.finishIntroduction())
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration))

        val custom = LauncherConfiguration(Page.TASKS, mapOf(Page.ALL_APPS to Gesture.UP))
        check(store.save(custom))
        check(store.initialize())
        check(store.load(available) == ConfigurationRead.Ready(custom))
        check(store.reset())
        check(ConfigurationStore(targetContext).load(available) == ConfigurationRead.SetupRequested)
        check(!store.initialize())
        check(store.save(custom))
        check(store.load(available) == ConfigurationRead.Ready(custom))
        check(preferences().edit().putString("home", "INVALID").commit())
        check(store.load(available) == ConfigurationRead.Invalid)
        check(!store.initialize())
        check(preferences().getString("home", "") == "INVALID")
    }

    private fun failedWrites() {
        val failingContext = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val delegate = super.getSharedPreferences(name, mode)
                return object : SharedPreferences by delegate {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = delegate.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun clear(): SharedPreferences.Editor { editor.clear(); return this }
                            override fun commit(): Boolean { editor.apply(); return false }
                        }
                    }
                }
            }
        }
        val failing = ConfigurationStore(failingContext)
        val store = ConfigurationStore(targetContext)
        check(preferences().edit().clear().commit())
        check(!failing.initialize())
        check(store.load(available) == ConfigurationRead.Missing)
        check(store.initialize())
        check(!failing.finishIntroduction())
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration, introductionPending = true))
        check(!failing.reset())
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration, introductionPending = true))
        check(!failing.save(LauncherConfiguration(Page.TASKS, emptyMap())))
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration, introductionPending = true))
        check(store.finishIntroduction())
        check(!failing.dismissDefaultHomeOffer())
        check(store.load(available) == ConfigurationRead.Ready(defaultConfiguration, defaultHomePending = true))
        check(store.dismissDefaultHomeOffer())
        check(store.reset())
        check(!failing.save(defaultConfiguration))
        check(store.load(available) == ConfigurationRead.SetupRequested)
        check(preferences().edit().clear().commit())
    }

    private fun overlayIsolation() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitFor("Welcome overlay did not appear") { activity.findViewById<View>(R.id.welcome_navigation) != null }
            runOnMainSync {
                val host = activity.findViewById<FrameLayout>(R.id.page_content)
                check(host.childCount == 2)
                check(host.getChildAt(0).visibility == View.INVISIBLE)
                check(host.getChildAt(0).importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                check(activity.findViewById<View>(R.id.wizard_prompt) == null)
                check(activity.findViewById<View>(R.id.welcome_sample_notice).visibility == View.VISIBLE)
                check(activity.findViewById<TextView>(R.id.welcome_navigation).text == targetContext.getString(R.string.swipe_left))
                check(activity.findViewById<View>(R.id.welcome_finish).visibility == View.GONE)
                check(activity.findViewById<ListView>(R.id.welcome_preview).adapter.getItem(0) == "Phone")
            }
            swipe(activity, Gesture.RIGHT)
            runOnMainSync {
                check(activity.findViewById<TextView>(R.id.welcome_navigation).text == targetContext.getString(R.string.swipe_left))
            }
            swipe(activity, Gesture.LEFT)
            waitFor("Contact preview did not appear") {
                activity.findViewById<TextView>(R.id.welcome_navigation)?.text == targetContext.getString(R.string.swipe_right)
            }
            runOnMainSync {
                check(activity.findViewById<ListView>(R.id.welcome_preview).adapter.getItem(0) == "Alex")
                activity.findViewById<View>(R.id.welcome_navigation).performClick()
            }
            waitFor("Shortcut map did not appear") { activity.findViewById<View>(R.id.welcome_map) != null }
            runOnMainSync {
                check(activity.findViewById<ListView>(R.id.welcome_preview).adapter.getItem(0) == "Phone")
                check(activity.findViewById<View>(R.id.welcome_finish).visibility == View.VISIBLE)
                val footer = activity.findViewById<View>(R.id.welcome_finish).parent as FrameLayout
                check((footer.background as ColorDrawable).color == activity.appearanceColor(R.color.background))
                verifyMap(activity.findViewById(R.id.welcome_map))
                listOf(R.string.theme_explanation, R.string.font_explanation,
                    R.string.contact_history_explanation, R.string.reset_wizard_explanation,
                    R.string.clear_history_explanation, R.string.default_home_prompt_message).forEach {
                    check(targetContext.getString(it).split(Regex("\\s+")).size <= 16)
                }
                activity.findViewById<View>(R.id.welcome_finish).performClick()
            }
            waitFor("Welcome overlay did not finish") { activity.findViewById<View>(R.id.welcome_finish) == null }
            runOnMainSync {
                val host = activity.findViewById<FrameLayout>(R.id.page_content)
                check(host.childCount == 1)
                check(host.getChildAt(0).visibility == View.VISIBLE)
                check(host.getChildAt(0).importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
            }
            val completed = ConfigurationStore(targetContext).load(available) as ConfigurationRead.Ready
            check(!completed.introductionPending)
        } finally {
            runOnMainSync { activity.finish() }
            waitForIdleSync()
            check(preferences().edit().clear().commit())
        }
    }

    private fun swipe(activity: Activity, gesture: Gesture) {
        val bounds = Rect()
        runOnMainSync { activity.findViewById<View>(R.id.welcome_swipe_area).getGlobalVisibleRect(bounds) }
        val distance = targetContext.resources.displayMetrics.density * 80
        val start = SystemClock.uptimeMillis()
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val endX = x + if (gesture == Gesture.LEFT) -distance else distance
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(start, start + index * 40L, action, if (index == 0) x else endX, y, 0)
            try { sendPointerSync(event) } finally { event.recycle() }
        }
        waitForIdleSync()
    }

    private fun verifyMap(map: ViewGroup) {
        fun boxes(view: View): List<TextView> = if (view.id == R.id.welcome_map_box) {
            listOf(view as TextView)
        } else if (view is ViewGroup) {
            (0 until view.childCount).flatMap { boxes(view.getChildAt(it)) }
        } else emptyList()
        val boxes = boxes(map)
        check(boxes.map { it.text.toString() } == listOf("Search", "Tasks", "Apps", "Contacts"))
        val bounds = boxes.map { Rect().also(it::getGlobalVisibleRect) }
        check(kotlin.math.abs(bounds[0].centerX() - bounds[2].centerX()) <= 2)
        check(bounds[0].bottom <= bounds[2].top)
        check(bounds[1].right < bounds[2].left && bounds[2].right < bounds[3].left)
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        repeat(100) {
            var ready = false
            runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        error(message)
    }
}
