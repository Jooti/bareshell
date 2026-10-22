package net.jooti.bareshell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

@SuppressLint("ViewConstructor") // Programmatic illustration with the unused gestures and caption callback.
internal class WizardGestureIllustration(
    context: Context,
    private val gestures: List<Gesture>,
    private val onExample: (Gesture) -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var example = 0
    private var fraction = 0.55f
    private var foreground = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            fraction = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationRepeat(animation: Animator) {
                example = (example + 1) % gestures.size
                onExample(gestures[example])
            }
        })
    }

    init {
        require(gestures.isNotEmpty())
        id = R.id.wizard_swipe_area
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.wizard_swipe_tip)
        onExample(gestures.first())
    }

    fun setForeground(value: Boolean) {
        foreground = value
        updateMotion()
    }

    private fun updateMotion() {
        if (foreground && isAttachedToWindow && windowVisibility == VISIBLE && isShown &&
            ValueAnimator.areAnimatorsEnabled()
        ) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            fraction = 0.55f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMotion()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateMotion()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = resources.displayMetrics.density
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(unit, unit)
        val gesture = gestures[example]
        val shake = gesture == Gesture.SHAKE
        val offset = if (shake) sin(fraction * Math.PI * 6).toFloat() * 7f else 0f
        paint.color = context.appearanceColor(R.color.foreground_muted)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(-34f + offset, -54f, 34f + offset, 54f, 8f, 8f, paint)
        canvas.drawLine(-8f + offset, 44f, 8f + offset, 44f, paint)
        paint.color = context.appearanceColor(R.color.foreground)
        if (shake) {
            canvas.drawLine(-52f, -18f, -58f, -5f, paint)
            canvas.drawLine(-58f, -5f, -52f, 8f, paint)
            canvas.drawLine(52f, -18f, 58f, -5f, paint)
            canvas.drawLine(58f, -5f, 52f, 8f, paint)
        } else {
            val dx = when (gesture) { Gesture.LEFT, Gesture.TWO_LEFT -> -1f; Gesture.RIGHT, Gesture.TWO_RIGHT -> 1f; else -> 0f }
            val dy = when (gesture) { Gesture.UP, Gesture.TWO_UP -> -1f; Gesture.DOWN, Gesture.TWO_DOWN -> 1f; else -> 0f }
            val startX = -dx * 22f
            val startY = -dy * 28f
            val endX = dx * 22f
            val endY = dy * 28f
            canvas.drawLine(startX, startY, endX, endY, paint)
            canvas.drawLine(endX, endY, endX - dx * 8f - dy * 6f, endY - dy * 8f + dx * 6f, paint)
            canvas.drawLine(endX, endY, endX - dx * 8f + dy * 6f, endY - dy * 8f - dx * 6f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                startX + (endX - startX) * fraction,
                startY + (endY - startY) * fraction, 6f, paint,
            )
            if (gesture in twoFingerGestures) {
                canvas.drawCircle(
                    startX + (endX - startX) * fraction + if (dx == 0f) 16f else 0f,
                    startY + (endY - startY) * fraction + if (dy == 0f) 16f else 0f, 6f, paint,
                )
            }
        }
        canvas.restore()
    }
}
