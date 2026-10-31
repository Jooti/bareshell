package net.jooti.bareshell

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

internal enum class WelcomeStep {
    APPS, CONTACTS, MAP;

    val page: Page get() = if (this == CONTACTS) Page.FAV_CONTACTS else Page.FAV_APPS
    val gesture: Gesture? get() = when (this) {
        APPS -> defaultConfiguration.bindings.getValue(Page.FAV_CONTACTS)
        CONTACTS -> defaultConfiguration.bindings.getValue(Page.FAV_CONTACTS).reverse()
        MAP -> null
    }

    fun navigate(input: Gesture): WelcomeStep = if (input == gesture) {
        if (this == APPS) CONTACTS else MAP
    } else this

    fun back(): WelcomeStep = when (this) {
        APPS -> APPS
        CONTACTS -> APPS
        MAP -> CONTACTS
    }
}

internal class WelcomeOverlay(
    private val context: Context,
    initialStep: WelcomeStep,
    private val onFinish: () -> Unit,
) {
    val view = FrameLayout(context).apply {
        setBackgroundColor(context.appearanceColor(R.color.background))
        setOnTouchListener { _, _ -> true }
    }
    var step = initialStep
        private set
    private var samples = emptyList<String>()
    private val list = ListView(context).apply {
        id = R.id.welcome_preview
        divider = null
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = samples.size
            override fun getItem(position: Int): String = samples[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun areAllItemsEnabled(): Boolean = false
            override fun isEnabled(position: Int): Boolean = false
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: context.textLabel("", R.id.welcome_row, primary = true)).apply {
                    text = samples[position]
                }
        }
    }
    private val preview = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val minimum = (context.dp(112) * resources.configuration.fontScale).roundToInt()
            val gap = favoriteTopGap(height, 0, minimum)
            if (paddingTop != gap) setPadding(0, gap, 0, 0)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        background = border()
    }
    private val status = context.textLabel("", R.id.welcome_status).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = View.GONE
        setBackgroundColor(context.appearanceColor(R.color.background))
    }
    private val finish = context.textLabel(context.getString(R.string.welcome_finish), R.id.welcome_finish).apply {
        onTextClick(onFinish)
    }
    private val finishBar = FrameLayout(context).apply {
        setBackgroundColor(context.appearanceColor(R.color.background))
        addView(finish, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private var busy = false

    init {
        view.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(context).apply {
                id = R.id.welcome_swipe_area
                addView(card)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(status)
            addView(finishBar)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        render()
    }

    fun navigate(gesture: Gesture) {
        if (busy) return
        val next = step.navigate(gesture)
        if (next != step) {
            step = next
            render()
        }
    }

    fun back() {
        if (!busy && step != WelcomeStep.APPS) {
            step = step.back()
            render()
        }
    }

    fun saving(value: Boolean, failed: Boolean = false) {
        busy = value
        finish.isEnabled = !value
        status.visibility = if (value || failed) View.VISIBLE else View.GONE
        status.text = context.getString(if (failed) R.string.welcome_save_failed else R.string.settings_saving)
    }

    private fun border() = GradientDrawable().apply {
        setColor(context.appearanceColor(R.color.background))
        setStroke(context.dp(1), context.appearanceColor(R.color.foreground_muted))
        cornerRadius = context.dp(8).toFloat()
    }

    private fun render() {
        samples = context.resources.getStringArray(
            if (step.page == Page.FAV_CONTACTS) R.array.welcome_contacts_sample else R.array.welcome_apps_sample,
        ).toList()
        (list.adapter as BaseAdapter).notifyDataSetChanged()
        list.setSelection(0)
        card.removeAllViews()
        card.addView(context.textLabel(context.getString(R.string.welcome_sample_notice), R.id.welcome_sample_notice).apply {
            minHeight = 0
            setPadding(context.dp(8), 0, context.dp(8), 0)
        })
        val gesture = step.gesture
        if (gesture != null) {
            card.addView(context.textLabel(context.getString(gesture.labelRes), R.id.welcome_navigation, primary = true).apply {
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                contentDescription = context.getString(R.string.welcome_swipe_action, context.getString(gesture.labelRes))
                onTextClick { navigate(gesture) }
            })
        } else {
            card.addView(context.textLabel(context.getString(R.string.welcome_title), R.id.welcome_title, primary = true).apply {
                minHeight = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
            })
            card.addView(shortcutMap())
            card.addView(context.textLabel(context.getString(R.string.welcome_settings), R.id.welcome_text).apply { minHeight = 0 })
        }
        finish.visibility = if (step == WelcomeStep.MAP) View.VISIBLE else View.GONE
        finishBar.visibility = finish.visibility
    }

    private fun shortcutMap(): View = LinearLayout(context).apply {
        id = R.id.welcome_map
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.welcome_map_description)
        fun box(label: Int, gesture: Gesture? = null): View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(context.textLabel(context.getString(label), R.id.welcome_map_box).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                minHeight = context.dp(48)
                setPadding(context.dp(2), 0, context.dp(2), 0)
                background = border()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (gesture != null) addView(context.textLabel(context.getString(gesture.labelRes), R.id.welcome_map_gesture).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                minHeight = 0
                setPadding(0, context.dp(4), 0, 0)
            })
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(box(R.string.search, defaultConfiguration.bindings.getValue(Page.SEARCH)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.TOP
            addView(box(R.string.tasks, defaultConfiguration.bindings.getValue(Page.TASKS)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(context.textLabel("\u2190", R.id.welcome_map_arrow).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, 0, 0, 0)
            })
            addView(box(R.string.welcome_map_apps),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(context.textLabel("\u2192", R.id.welcome_map_arrow).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, 0, 0, 0)
            })
            addView(box(R.string.welcome_map_contacts, defaultConfiguration.bindings.getValue(Page.FAV_CONTACTS)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
    }
}
