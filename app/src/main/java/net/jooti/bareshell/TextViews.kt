package net.jooti.bareshell

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.textLabel(text: String, viewId: Int, primary: Boolean = false): TextView =
    TextView(this).apply {
        id = viewId
        this.text = text
        setTextColor(appearanceColor(R.color.foreground))
        textSize = if (primary) 24f else 16f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = dp(56)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

internal fun TextView.onTextClick(action: () -> Unit) {
    val background = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
    setBackgroundResource(background.resourceId)
    isFocusable = true
    setOnClickListener { action() }
}

internal val Page.labelRes: Int
    get() = when (this) {
        Page.FAV_APPS -> R.string.fav_apps
        Page.FAV_CONTACTS -> R.string.fav_contacts
        Page.ALL_APPS -> R.string.all_apps
        Page.ALL_CONTACTS -> R.string.all_contacts
        Page.SEARCH -> R.string.search
        Page.TASKS -> R.string.tasks
    }

internal val Gesture.labelRes: Int
    get() = when (this) {
        Gesture.LEFT -> R.string.swipe_left
        Gesture.RIGHT -> R.string.swipe_right
        Gesture.UP -> R.string.swipe_up
        Gesture.DOWN -> R.string.swipe_down
        Gesture.SHAKE -> R.string.shake
        Gesture.TWO_LEFT -> R.string.two_swipe_left
        Gesture.TWO_RIGHT -> R.string.two_swipe_right
        Gesture.TWO_UP -> R.string.two_swipe_up
        Gesture.TWO_DOWN -> R.string.two_swipe_down
    }

internal fun Context.pageDescription(page: Page, settings: LauncherSettings): String = when (page) {
    Page.FAV_APPS -> resources.getQuantityString(R.plurals.describe_fav_apps, settings.topAppsCount, settings.topAppsCount)
    Page.FAV_CONTACTS -> resources.getQuantityString(R.plurals.describe_fav_contacts, settings.closeContactsCount, settings.closeContactsCount)
    Page.ALL_APPS -> getString(R.string.describe_all_apps)
    Page.ALL_CONTACTS -> getString(R.string.describe_all_contacts)
    Page.SEARCH -> getString(R.string.describe_search)
    Page.TASKS -> getString(R.string.describe_tasks)
}
