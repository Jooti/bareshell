package net.jooti.bareshell

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.AbsListView
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

internal class GestureInput(
    context: Context,
    private val onGesture: (Gesture) -> Unit,
    private val shakeEnabled: () -> Boolean,
    private val onSensorUnavailable: () -> Unit,
    private val canOpenSettings: () -> Boolean = { false },
    private val onOpenSettings: () -> Unit = {},
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val shake = ShakeRecognition()
    private val density = context.resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var trackingSwipe = false
    private val swipe = PointerSwipeRecognition()
    private val supportsTwoFingers = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)
    private var longPressed = false
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var longPress: Runnable? = null

    private fun cancelLongPress() {
        longPress?.let(handler::removeCallbacks)
        longPress = null
    }
    var shakeAvailable = sensor != null
        private set
    val available: Set<Gesture>
        get() = Gesture.entries.filter {
            (it != Gesture.SHAKE || shakeAvailable) && (it !in twoFingerGestures || supportsTwoFingers)
        }.toSet()

    fun startsInBlankSpace(view: View, event: MotionEvent): Boolean =
        isBlankSpace(view, event.rawX, event.rawY)

    private fun isBlankSpace(view: View, x: Float, y: Float): Boolean {
        if (view is EditText) return false
        if (view is ViewGroup && view.isClickable && view !is AbsListView && view !is ScrollView) return false
        if (view is TextView) {
            val layout = view.layout ?: return false
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val localX = x - location[0] - view.totalPaddingLeft + view.scrollX
            val localY = y - location[1] - view.totalPaddingTop + view.scrollY
            for (line in 0 until layout.lineCount) {
                val left = layout.getLineLeft(line)
                val right = layout.getLineRight(line)
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(line)
                val paddingX = maxOf(8f * density, (48f * density - (right - left)) / 2f)
                val paddingY = maxOf(8f * density, (48f * density - (bottom - top)) / 2f)
                if (localX >= left - paddingX && localX <= right + paddingX &&
                    localY >= top - paddingY && localY <= bottom + paddingY
                ) return false
            }
            return true
        }
        if (view is ViewGroup) {
            val bounds = Rect()
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (child.visibility == View.VISIBLE && child.getGlobalVisibleRect(bounds) &&
                    bounds.contains(x.toInt(), y.toInt())
                ) return isBlankSpace(child, x, y)
            }
        }
        return !view.isClickable || view is AbsListView || view is ScrollView
    }

    fun attach(view: View) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                cancelLongPress()
                trackingSwipe = false
                swipe.cancel()
            }
        })
        view.setOnLongClickListener {
            if (canOpenSettings()) { onOpenSettings(); true } else false
        }
        view.setOnTouchListener { surface, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startX = event.x
                startY = event.y
                trackingSwipe = true
                swipe.begin(SwipePoint(event.getPointerId(0), event.x / density, event.y / density))
                longPressed = false
                cancelLongPress()
                if (canOpenSettings()) {
                    longPress = Runnable {
                        if (trackingSwipe && !swipe.multiTouch && canOpenSettings()) {
                            longPressed = true
                            trackingSwipe = false
                            swipe.cancel()
                            surface.performLongClick()
                        }
                    }.also { handler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
                }
            }
            if (event.pointerCount > 1) {
                cancelLongPress()
            }
            fun points(skip: Int = -1): List<SwipePoint> = (0 until event.pointerCount).filter { it != skip }.map {
                SwipePoint(event.getPointerId(it), event.getX(it) / density, event.getY(it) / density)
            }
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                val index = event.actionIndex
                swipe.move(points(index))
                val rawX = event.rawX + event.getX(index) - event.x
                val rawY = event.rawY + event.getY(index) - event.y
                swipe.add(
                    SwipePoint(event.getPointerId(index), event.getX(index) / density, event.getY(index) / density),
                    supportsTwoFingers && isBlankSpace(surface, rawX, rawY),
                )
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                swipe.move(points())
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) swipe.lift()
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE &&
                ((event.x - startX) * (event.x - startX) + (event.y - startY) * (event.y - startY)) > touchSlop * touchSlop
            ) cancelLongPress()
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    cancelLongPress()
                    swipe.move(points())
                    val gesture = if (trackingSwipe) swipe.finish() else null
                    val click = trackingSwipe && !swipe.multiTouch && gesture == null
                    trackingSwipe = false
                    swipe.cancel()
                    if (gesture != null) onGesture(gesture)
                    else if (click && !longPressed) surface.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    trackingSwipe = false
                    swipe.cancel()
                }

            }
            true
        }
    }

    fun resume() {
        shake.reset()
        if (shakeAvailable && !manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
            Log.e("BareShellGestures", "Accelerometer listener could not be registered")
            shakeAvailable = false
            onSensorUnavailable()
        }
    }

    fun pause() {
        cancelLongPress()
        trackingSwipe = false
        swipe.cancel()
        manager.unregisterListener(this)
        shake.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!shakeEnabled()) {
            shake.suspendRecognition()
            return
        }
        if (shake.sample(event.timestamp, event.values[0], event.values[1], event.values[2])) {
            onGesture(Gesture.SHAKE)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

internal val twoFingerGestures = setOf(Gesture.TWO_LEFT, Gesture.TWO_RIGHT, Gesture.TWO_UP, Gesture.TWO_DOWN)
