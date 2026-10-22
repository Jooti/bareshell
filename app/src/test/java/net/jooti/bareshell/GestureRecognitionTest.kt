package net.jooti.bareshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognitionTest {
    @Test
    fun recognizesAllFourDominantDirections() {
        assertEquals(Gesture.LEFT, recognizeSwipe(-60f, 5f))
        assertEquals(Gesture.RIGHT, recognizeSwipe(60f, 5f))
        assertEquals(Gesture.UP, recognizeSwipe(5f, -60f))
        assertEquals(Gesture.DOWN, recognizeSwipe(5f, 60f))
    }

    @Test
    fun rejectsTapsShortMovementsAndAmbiguousDiagonals() {
        assertNull(recognizeSwipe(0f, 0f))
        assertNull(recognizeSwipe(5f, 5f))
        assertNull(recognizeSwipe(39.9f, 0f))
        assertNull(recognizeSwipe(0f, -39.9f))
        assertNull(recognizeSwipe(60f, 60f))
    }

    @Test
    fun acceptsMinimumDistanceInEveryDirectionWithoutRequiringReleaseVelocity() {
        assertEquals(Gesture.LEFT, recognizeSwipe(-40f, 0f))
        assertEquals(Gesture.RIGHT, recognizeSwipe(40f, 0f))
        assertEquals(Gesture.UP, recognizeSwipe(0f, -40f))
        assertEquals(Gesture.DOWN, recognizeSwipe(0f, 40f))
    }

    @Test
    fun acceptsOnlyClearlyDominantAxes() {
        assertEquals(Gesture.LEFT, recognizeSwipe(-60f, 40f))
        assertEquals(Gesture.DOWN, recognizeSwipe(40f, 60f))
        assertNull(recognizeSwipe(-60f, 40.1f))
        assertNull(recognizeSwipe(40.1f, 60f))
    }

    @Test
    fun aShakeNeedsTwoSeparatedPeaksAndReturnsOnlyOneEvent() {
        val shake = ShakeRecognition()
        assertFalse(shake.sample(0, 30f, 0f, 0f))
        assertFalse(shake.sample(100_000_000, 0f, 9.8f, 0f))
        assertTrue(shake.sample(200_000_000, -30f, 0f, 0f))
        assertFalse(shake.sample(250_000_000, 30f, 0f, 0f))
        assertFalse(shake.sample(300_000_000, 0f, 9.8f, 0f))
        assertFalse(shake.sample(400_000_000, -30f, 0f, 0f))
    }

    @Test
    fun stationaryAndNormalSmallMovementsNeverShake() {
        val shake = ShakeRecognition()
        repeat(100) { index ->
            assertFalse(shake.sample(index * 100_000_000L, 1f, 10f, 0.5f))
        }
    }

    @Test
    fun temporaryPromptTransitionsDoNotEraseShakeCooldown() {
        val shake = ShakeRecognition()
        shake.sample(0, 30f, 0f, 0f)
        shake.sample(100_000_000, 9.8f, 0f, 0f)
        assertTrue(shake.sample(200_000_000, 30f, 0f, 0f))
        shake.suspendRecognition()
        shake.sample(400_000_000, 9.8f, 0f, 0f)
        assertFalse(shake.sample(500_000_000, 30f, 0f, 0f))
        shake.sample(600_000_000, 9.8f, 0f, 0f)
        assertFalse(shake.sample(700_000_000, 30f, 0f, 0f))
    }

    @Test
    fun sustainedHighAccelerationAndWidelySpacedPeaksAreNotShakes() {
        val shake = ShakeRecognition()
        assertFalse(shake.sample(0, 30f, 0f, 0f))
        assertFalse(shake.sample(200_000_000, 30f, 0f, 0f))
        assertFalse(shake.sample(1_000_000_000, 9.8f, 0f, 0f))
        assertFalse(shake.sample(1_200_000_000, 30f, 0f, 0f))
    }

    @Test
    fun aNewShakeWorksAfterCooldownAndResetDiscardsOldPeaks() {
        val shake = ShakeRecognition()
        shake.sample(0, 30f, 0f, 0f)
        shake.sample(100_000_000, 9.8f, 0f, 0f)
        assertTrue(shake.sample(200_000_000, 30f, 0f, 0f))
        shake.sample(1_500_000_000, 9.8f, 0f, 0f)
        assertFalse(shake.sample(1_600_000_000, 30f, 0f, 0f))
        shake.reset()
        shake.sample(1_700_000_000, 9.8f, 0f, 0f)
        assertFalse(shake.sample(1_800_000_000, 30f, 0f, 0f))
        shake.sample(1_900_000_000, 9.8f, 0f, 0f)
        assertTrue(shake.sample(2_000_000_000, 30f, 0f, 0f))
    }
}
