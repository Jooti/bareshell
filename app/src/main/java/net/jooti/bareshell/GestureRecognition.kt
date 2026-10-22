package net.jooti.bareshell

import kotlin.math.abs
import kotlin.math.sqrt

internal fun recognizeSwipe(dx: Float, dy: Float): Gesture? = when {
    abs(dx) >= 40f && abs(dx) >= abs(dy) * 1.5f ->
        if (dx < 0) Gesture.LEFT else Gesture.RIGHT
    abs(dy) >= 40f && abs(dy) >= abs(dx) * 1.5f ->
        if (dy < 0) Gesture.UP else Gesture.DOWN
    else -> null
}

internal data class SwipePoint(val id: Int, val x: Float, val y: Float)

internal class PointerSwipeRecognition {
    private val starts = mutableMapOf<Int, SwipePoint>()
    private val ends = mutableMapOf<Int, SwipePoint>()
    private var valid = false
    private var lifted = false
    var multiTouch = false
        private set
    private var established = false

    fun begin(point: SwipePoint) {
        starts.clear()
        ends.clear()
        starts[point.id] = point
        ends[point.id] = point
        valid = true
        lifted = false
        multiTouch = false
        established = false
    }

    fun add(point: SwipePoint, blank: Boolean) {
        multiTouch = true
        if (starts.size != 1 || lifted || established || !blank || point.id in starts) {
            cancel()
            return
        }
        starts[point.id] = point
        ends[point.id] = point
    }

    fun move(points: List<SwipePoint>) {
        if (!valid || lifted) return
        if (points.size != starts.size || points.map { it.id }.toSet() != starts.keys) {
            cancel()
            return
        }
        points.forEach { point ->
            ends[point.id] = point
            val start = starts.getValue(point.id)
            if (recognizeSwipe(point.x - start.x, point.y - start.y) != null) established = true
        }
    }

    fun lift() { lifted = true }
    fun cancel() { valid = false }

    fun finish(): Gesture? {
        if (!valid) return null
        valid = false
        val directions = starts.map { (id, start) ->
            val end = ends.getValue(id)
            recognizeSwipe(end.x - start.x, end.y - start.y)
        }
        val direction = directions.firstOrNull() ?: return null
        if (directions.any { it != direction }) return null
        return if (!multiTouch) direction else when (direction) {
            Gesture.LEFT -> Gesture.TWO_LEFT
            Gesture.RIGHT -> Gesture.TWO_RIGHT
            Gesture.UP -> Gesture.TWO_UP
            Gesture.DOWN -> Gesture.TWO_DOWN
            else -> null
        }
    }
}

internal class ShakeRecognition {
    private var armed = true
    private var lastPeak: Long? = null
    private var cooldownUntil = 0L

    fun reset() {
        armed = true
        lastPeak = null
        cooldownUntil = 0
    }

    fun suspendRecognition() {
        armed = false
        lastPeak = null
    }

    fun sample(timeNanos: Long, x: Float, y: Float, z: Float): Boolean {
        val force = sqrt(x * x + y * y + z * z) / 9.80665f
        if (force < 1.5f) armed = true
        if (timeNanos < cooldownUntil || force < 2.2f || !armed) return false
        armed = false
        val previous = lastPeak
        lastPeak = timeNanos
        if (previous != null && timeNanos - previous in 80_000_000L..700_000_000L) {
            lastPeak = null
            cooldownUntil = timeNanos + 1_200_000_000L
            return true
        }
        return false
    }
}
