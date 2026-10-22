package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointerSwipeRecognitionTest {
    private fun pair() = PointerSwipeRecognition().apply {
        begin(SwipePoint(7, 0f, 0f))
        add(SwipePoint(2, 0f, 20f), true)
    }
    private fun PointerSwipeRecognition.movePair(dx: Float, dy: Float) =
        move(listOf(SwipePoint(2, dx, 20f + dy), SwipePoint(7, dx, dy)))

    @Test fun recognizesBothFingersWithStableIdsAndMinimumDistances() {
        listOf(
            Triple(-40f, 0f, Gesture.TWO_LEFT), Triple(40f, 0f, Gesture.TWO_RIGHT),
            Triple(0f, -40f, Gesture.TWO_UP), Triple(0f, 40f, Gesture.TWO_DOWN),
        ).forEach { (dx, dy, expected) ->
            val tracker = pair()
            tracker.movePair(dx, dy)
            tracker.lift()
            tracker.move(listOf(SwipePoint(2, dx + 100, dy + 200)))
            assertEquals(expected, tracker.finish())
            assertNull(tracker.finish())
        }
    }

    @Test fun oneFingerRemainsUnchanged() {
        val tracker = PointerSwipeRecognition()
        tracker.begin(SwipePoint(4, 0f, 0f))
        tracker.move(listOf(SwipePoint(4, 40f, 0f)))
        assertEquals(Gesture.RIGHT, tracker.finish())
    }

    @Test fun rejectsPinchesOppositeDirectionsShortSecondFingerAndDiagonals() {
        listOf(
            listOf(SwipePoint(7, -60f, 0f), SwipePoint(2, 60f, 20f)),
            listOf(SwipePoint(7, 60f, 0f), SwipePoint(2, 39f, 20f)),
            listOf(SwipePoint(7, 60f, 60f), SwipePoint(2, 60f, 80f)),
        ).forEach { points ->
            val tracker = pair()
            tracker.move(points)
            assertNull(tracker.finish())
        }
    }

    @Test fun rejectsThirdFingerCanceledAndSecondFingerOverText() {
        pair().apply { add(SwipePoint(3, 0f, 40f), true); movePair(60f, 0f); assertNull(finish()) }
        pair().apply { movePair(60f, 0f); cancel(); assertNull(finish()) }
        PointerSwipeRecognition().apply {
            begin(SwipePoint(7, 0f, 0f))
            add(SwipePoint(2, 0f, 20f), false)
            movePair(60f, 0f)
            assertNull(finish())
        }
    }

    @Test fun rejectsLateSecondFingerAndUnknownPointerId() {
        PointerSwipeRecognition().apply {
            begin(SwipePoint(7, 0f, 0f))
            move(listOf(SwipePoint(7, 40f, 0f)))
            move(listOf(SwipePoint(7, 0f, 0f)))
            add(SwipePoint(2, 0f, 20f), true)
            movePair(60f, 0f)
            assertNull(finish())
        }
        pair().apply {
            move(listOf(SwipePoint(3, 60f, 20f), SwipePoint(7, 60f, 0f)))
            assertNull(finish())
        }
    }

    @Test fun resetStartsFreshAndLiftDoesNotPermitANewFinger() {
        val tracker = pair()
        tracker.movePair(60f, 0f)
        tracker.lift()
        tracker.add(SwipePoint(9, 60f, 20f), true)
        assertNull(tracker.finish())
        tracker.begin(SwipePoint(9, 0f, 0f))
        tracker.move(listOf(SwipePoint(9, 0f, -40f)))
        assertEquals(Gesture.UP, tracker.finish())
    }
}
