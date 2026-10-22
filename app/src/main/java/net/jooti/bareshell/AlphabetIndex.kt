package net.jooti.bareshell

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("ViewConstructor") // Created programmatically with its section-selection callback.
internal class AlphabetIndex(
    context: Context,
    private val select: (Int) -> Unit,
) : LinearLayout(context) {
    private var sections = emptyList<ContactSection>()
    private var dragging = false
    private var selected = -1
    private var suppressed = false
    private val hide = Runnable {
        animate().alpha(0f).setDuration(150).withEndAction { visibility = View.INVISIBLE }.start()
    }

    init {
        id = R.id.alphabet_index
        orientation = VERTICAL
        isClickable = true
        visibility = View.INVISIBLE
    }

    fun update(next: List<ContactSection>) {
        removeCallbacks(hide)
        animate().withEndAction(null).cancel()
        visibility = View.INVISIBLE
        sections = next
        dragging = false
        selected = -1
        removeAllViews()
        next.forEach { section ->
            addView(context.textLabel(section.label, R.id.alphabet_index_letter).apply {
                minHeight = 0
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                contentDescription = context.getString(R.string.jump_to_contact_section, section.label)
                isFocusable = true
                setOnClickListener {
                    reveal()
                    select(section.position)
                    hideLater()
                }
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        resizeLabels()
    }

    fun setScrolling(active: Boolean) {
        if (active) reveal()
        hideLater()
    }

    fun suppress(value: Boolean) {
        suppressed = value
        if (value) {
            removeCallbacks(hide)
            animate().withEndAction(null).cancel()
            dragging = false
            visibility = View.INVISIBLE
        }
    }

    fun onScrolled() {
        if (visibility == View.VISIBLE) {
            reveal()
            hideLater()
        }
    }

    private fun reveal() {
        if (suppressed || sections.isEmpty()) return
        removeCallbacks(hide)
        animate().withEndAction(null).cancel()
        alpha = 1f
        visibility = View.VISIBLE
    }

    private fun hideLater() {
        removeCallbacks(hide)
        if (!dragging && visibility == View.VISIBLE) postDelayed(hide, 1500)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (suppressed || sections.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                selected = -1
                reveal()
                parent.requestDisallowInterceptTouchEvent(true)
                selectAt(event.y)
            }
            MotionEvent.ACTION_MOVE -> selectAt(event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                dragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                hideLater()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun selectAt(y: Float) {
        if (height <= 0) return
        val index = (y / height * sections.size).toInt().coerceIn(0, sections.lastIndex)
        if (selected != index) {
            selected = index
            select(sections[index].position)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resizeLabels()
    }

    private fun resizeLabels() {
        if (childCount == 0 || height == 0) return
        val normalSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        val size = minOf(normalSize, height.toFloat() / childCount * 0.7f)
        for (index in 0 until childCount) {
            (getChildAt(index) as TextView).setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        animate().withEndAction(null).cancel()
        super.onDetachedFromWindow()
    }
}
