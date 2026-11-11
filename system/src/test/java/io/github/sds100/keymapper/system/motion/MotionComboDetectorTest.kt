package io.github.sds100.keymapper.system.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MotionComboDetectorTest {

    private lateinit var detector: MotionComboDetector

    @Before
    fun setUp() {
        detector = MotionComboDetector(
            threshold = 3.5f,
            debounceMs = 250L,
            shakeWindowMs = 150L,
        )
    }

    @Test
    fun `detects individual directional gestures above threshold`() {
        assertEquals(MotionDirection.UP, detector.processSample(0f, 4.0f, 0f, 1000L))

        assertEquals(MotionDirection.DOWN, detector.processSample(0f, -4.5f, 0f, 1300L))

        assertEquals(MotionDirection.RIGHT, detector.processSample(5.0f, 0f, 0f, 1600L))

        assertEquals(MotionDirection.LEFT, detector.processSample(-4.2f, 0f, 0f, 1900L))

        assertEquals(MotionDirection.FORWARD, detector.processSample(0f, 0f, 6.0f, 2200L))

        assertEquals(MotionDirection.BACKWARD, detector.processSample(0f, 0f, -5.5f, 2500L))
    }

    @Test
    fun `ignores acceleration samples below threshold`() {
        assertNull(detector.processSample(1.0f, 2.0f, 3.0f, 1000L))
        assertNull(detector.processSample(-3.49f, 0f, 0f, 1050L))
        assertNull(detector.processSample(0f, 0f, 0f, 1100L))
    }

    @Test
    fun `detects shake on rapid sign change within shake window`() {
        // Initial move RIGHT on X-axis
        assertEquals(MotionDirection.RIGHT, detector.processSample(4.5f, 0f, 0f, 1000L))

        // Rapid reverse to LEFT on X-axis within 100ms (< 150ms shake window)
        val shake = detector.processSample(-4.5f, 0f, 0f, 1100L)
        assertEquals(MotionDirection.SHAKE, shake)
    }

    @Test
    fun `enforces debounce period between regular gestures`() {
        // First gesture UP
        assertEquals(MotionDirection.UP, detector.processSample(0f, 4.0f, 0f, 1000L))

        // Second sample with same sign or different axis within 200ms (< 250ms debounce)
        assertNull(detector.processSample(0f, 4.2f, 0f, 1150L))
        assertNull(detector.processSample(5.0f, 0f, 0f, 1200L))

        // Sample after debounce window (> 250ms) is accepted
        assertEquals(MotionDirection.RIGHT, detector.processSample(5.0f, 0f, 0f, 1300L))
    }

    @Test
    fun `triggers sequential combo when sequence matches within window`() {
        val combo = MotionCombo(
            id = "combo_up_up_backward",
            sequence = listOf(MotionDirection.UP, MotionDirection.UP, MotionDirection.BACKWARD),
            windowTimeoutMs = 2000L,
        )
        val combos = listOf(combo)

        assertNull(detector.feedGesture(MotionGesture(MotionDirection.UP, 1000L), combos))
        assertNull(detector.feedGesture(MotionGesture(MotionDirection.UP, 1300L), combos))

        val triggered = detector.feedGesture(MotionGesture(MotionDirection.BACKWARD, 1600L), combos)
        assertNotNull(triggered)
        assertEquals("combo_up_up_backward", triggered?.id)
    }

    @Test
    fun `does not trigger combo if gestures exceed window timeout`() {
        val combo = MotionCombo(
            id = "combo_fast",
            sequence = listOf(MotionDirection.UP, MotionDirection.DOWN),
            windowTimeoutMs = 1000L,
        )
        val combos = listOf(combo)

        assertNull(detector.feedGesture(MotionGesture(MotionDirection.UP, 1000L), combos))

        // 1600ms later (diff = 1600ms > 1000ms window timeout)
        val triggered = detector.feedGesture(MotionGesture(MotionDirection.DOWN, 2600L), combos)
        assertNull(triggered)
    }

    @Test
    fun `discards intermediate wrong gestures breaking the combo`() {
        val combo = MotionCombo(
            id = "combo_target",
            sequence = listOf(MotionDirection.UP, MotionDirection.UP, MotionDirection.BACKWARD),
            windowTimeoutMs = 3000L,
        )
        val combos = listOf(combo)

        assertNull(detector.feedGesture(MotionGesture(MotionDirection.UP, 1000L), combos))
        assertNull(detector.feedGesture(MotionGesture(MotionDirection.UP, 1300L), combos))

        // Wrong gesture injected
        assertNull(detector.feedGesture(MotionGesture(MotionDirection.LEFT, 1500L), combos))

        // Next gesture doesn't complete UP -> UP -> BACKWARD because of the LEFT
        val triggered = detector.feedGesture(MotionGesture(MotionDirection.BACKWARD, 1800L), combos)
        assertNull(triggered)
    }

    @Test
    fun `consumed combo does not trigger again immediately on extra gesture`() {
        val combo = MotionCombo(
            id = "combo_pair",
            sequence = listOf(MotionDirection.LEFT, MotionDirection.RIGHT),
            windowTimeoutMs = 2000L,
        )
        val combos = listOf(combo)

        assertNull(detector.feedGesture(MotionGesture(MotionDirection.LEFT, 1000L), combos))
        val firstTrigger = detector.feedGesture(MotionGesture(MotionDirection.RIGHT, 1300L), combos)
        assertNotNull(firstTrigger)

        // Extra gesture should not trigger without another full sequence
        val extra = detector.feedGesture(MotionGesture(MotionDirection.RIGHT, 1600L), combos)
        assertNull(extra)
    }

    @Test
    fun `reset clears all gesture history and peak timing state`() {
        val combo = MotionCombo(
            id = "combo_reset",
            sequence = listOf(MotionDirection.UP, MotionDirection.DOWN),
            windowTimeoutMs = 2000L,
        )
        val combos = listOf(combo)

        detector.feedGesture(MotionGesture(MotionDirection.UP, 1000L), combos)
        detector.reset()

        // DOWN alone should not trigger after reset
        val triggered = detector.feedGesture(MotionGesture(MotionDirection.DOWN, 1200L), combos)
        assertNull(triggered)
    }
}
