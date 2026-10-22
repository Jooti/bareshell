package net.jooti.bareshell

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal class SetupWizard(
    private val context: Context,
    private val onHome: (Page) -> Unit,
    private val onRetry: () -> Unit,
    private val onBack: () -> Unit,
    private val onToggle: (Page) -> Unit,
    private val onContinue: () -> Unit,
    private val settings: LauncherSettings = LauncherSettings(),
) {
    val view = ScrollView(context).apply { isFillViewport = true }
    var inputReady = false
        private set
    private var disposed = false
    private var foreground = false
    private var illustration: WizardGestureIllustration? = null
    private var feedback: TextView? = null
    private var selectionHome: Page? = null

    fun setForeground(value: Boolean) {
        foreground = value
        illustration?.setForeground(value && inputReady)
    }

    fun showGestureUsed(gesture: Gesture, owner: Page) {
        feedback?.apply {
            text = context.getString(R.string.wizard_gesture_used, context.getString(gesture.labelRes), context.getString(owner.labelRes))
            visibility = View.VISIBLE
        }
    }

    fun show(
        progress: WizardProgress,
        available: Set<Gesture>,
        invalidSettings: Boolean,
        saving: Boolean,
        saveFailed: Boolean,
    ) {
        val scroll = if (progress.home != null && !progress.assigning && selectionHome == progress.home) view.scrollY else 0
        selectionHome = if (!progress.assigning) progress.home else null
        inputReady = false
        illustration?.setForeground(false)
        view.animate().withEndAction(null).cancel()
        val update = {
            if (!disposed) {
                illustration = null
                feedback = null
                view.removeAllViews()
                val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                fun label(resource: Int, id: Int = R.id.wizard_tip, primary: Boolean = false, vararg args: Any): TextView =
                    context.textLabel(context.getString(resource, *args), id, primary).also { body.addView(it) }
                val stepBar = LinearLayout(context)
                stepBar.addView(context.textLabel(
                    context.getString(R.string.wizard_step, progress.stepNumber, progress.stepCount), R.id.wizard_progress,
                ).apply { setTextColor(context.appearanceColor(R.color.foreground_muted)) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (progress.home != null && !saving) stepBar.addView(
                    context.textLabel(context.getString(R.string.wizard_back), R.id.wizard_back).apply {
                        onTextClick { if (inputReady) onBack() }
                    },
                )
                body.addView(stepBar)
                val prompt = when {
                    saveFailed -> context.getString(R.string.save_failed)
                    saving || progress.complete -> context.getString(R.string.saving_setup)
                    progress.home == null -> context.getString(R.string.choose_home)
                    !progress.assigning -> context.getString(R.string.choose_destinations)
                    else -> context.getString(R.string.choose_gesture, context.getString(requireNotNull(progress.next).labelRes))
                }
                body.addView(context.textLabel(prompt, R.id.wizard_prompt, primary = true).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
                })
                if (progress.home == null) {
                    label(R.string.wizard_home_explanation, R.id.wizard_instructions)
                    if (invalidSettings) label(R.string.invalid_setup, R.id.status_message)
                    Page.entries.forEach { page ->
                        body.addView(context.textLabel("", R.id.wizard_option, primary = true).apply {
                            text = android.text.SpannableString(
                                "${context.getString(page.labelRes)}\n${context.pageDescription(page, settings)}",
                            ).apply {
                                val start = context.getString(page.labelRes).length + 1
                                setSpan(android.text.style.RelativeSizeSpan(0.67f), start, length, 0)
                                setSpan(android.text.style.ForegroundColorSpan(context.appearanceColor(R.color.foreground_muted)), start, length, 0)
                            }
                            onTextClick { if (inputReady) onHome(page) }
                        })
                    }
                    label(R.string.wizard_access_later)
                } else if (!progress.assigning) {
                    label(R.string.destination_capacity, R.id.wizard_instructions, false, progress.destinations.size, available.size)
                    label(R.string.destination_groups_help)
                    listOf(true, false).forEach { included ->
                        val group = progress.home.remaining().filter { (it in progress.destinations) == included }
                        label(
                            if (included) R.string.included_pages else R.string.excluded_pages,
                            R.id.section_heading, true, group.size,
                        ).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
                        }
                        if (group.isEmpty()) label(if (included) R.string.no_included_pages else R.string.no_excluded_pages)
                        group.forEach { page ->
                            body.addView(context.textLabel(
                                context.getString(
                                    if (included) R.string.destination_remove else R.string.destination_add,
                                    context.getString(page.labelRes),
                                ), R.id.wizard_option, primary = true,
                            ).apply {
                                isSelected = included
                                if (included || progress.destinations.size < available.size) {
                                    onTextClick { if (inputReady) onToggle(page) }
                                } else {
                                    setTextColor(context.appearanceColor(R.color.foreground_muted))
                                }
                            })
                        }
                    }
                    label(R.string.wizard_continue, R.id.wizard_continue).onTextClick { if (inputReady) onContinue() }
                } else {
                    if (!progress.complete) {
                        feedback = label(R.string.wizard_assign_explanation, R.id.wizard_feedback).apply {
                            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                            visibility = View.GONE
                        }
                        val caption = context.textLabel("", R.id.wizard_example)
                        body.addView(caption)
                        val unused = progress.unusedGestures(available)
                        if (unused.isNotEmpty()) {
                            illustration = WizardGestureIllustration(context, unused) { gesture ->
                                caption.text = if (gesture == Gesture.SHAKE) {
                                    context.getString(R.string.wizard_shake_example)
                                } else context.getString(R.string.wizard_example, context.getString(gesture.labelRes))
                            }.also { body.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(128))) }
                        }
                        label(R.string.available_gestures, R.id.status_message, false,
                            unused.joinToString(", ") { context.getString(it.labelRes) })
                        progress.bindings.entries.lastOrNull()?.let { (page, gesture) ->
                            label(R.string.wizard_assigned, R.id.wizard_summary, false,
                                context.getString(page.labelRes), context.getString(gesture.labelRes))
                        }
                        body.addView(context.textLabel(
                            context.pageDescription(requireNotNull(progress.next), settings), R.id.wizard_description,
                        ))
                        label(R.string.wizard_assign_explanation, R.id.wizard_instructions)
                        label(R.string.wizard_example_tip)
                        label(R.string.wizard_swipe_tip)
                        if (available.any { it in twoFingerGestures }) label(R.string.wizard_two_fingers)
                        if (Gesture.SHAKE in unused) label(R.string.wizard_shake_tip)
                        else if (Gesture.SHAKE !in available) label(R.string.wizard_no_shake)
                    }
                    label(R.string.wizard_setup_heading).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
                    }
                    label(R.string.wizard_home_summary, R.id.wizard_summary, false, context.getString(progress.home.labelRes))
                    progress.bindings.forEach { (page, gesture) ->
                        label(R.string.wizard_binding_summary, R.id.wizard_summary, false,
                            context.getString(page.labelRes), context.getString(gesture.labelRes))
                    }
                    if (!progress.complete) label(
                        if (Gesture.SHAKE in available) R.string.wizard_return_tip else R.string.wizard_return_swipes,
                    )
                }
                if (saveFailed) {
                    label(R.string.retry_save, R.id.retry_save).onTextClick { if (inputReady) onRetry() }
                }
                view.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                view.post { if (!disposed) view.scrollTo(0, scroll) }
                fun ready() {
                    inputReady = !disposed && !saving
                    illustration?.setForeground(foreground && inputReady)
                }
                if (ValueAnimator.areAnimatorsEnabled()) {
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(200).withEndAction { ready() }.start()
                } else {
                    view.alpha = 1f
                    ready()
                }
            }
        }
        if (view.childCount == 0 || !ValueAnimator.areAnimatorsEnabled()) {
            update()
        } else {
            view.animate().alpha(0f).setDuration(150).withEndAction { update() }.start()
        }
    }

    fun dispose() {
        disposed = true
        inputReady = false
        illustration?.setForeground(false)
        view.animate().withEndAction(null).cancel()
    }
}
