package dev.spatial.android

import dev.spatial.android.hand.PinchDetector
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchDetectorTest {

    @Test
    fun startAndConfirm() {
        val detector = PinchDetector(
            startThreshold = 0.2f,
            releaseThreshold = 0.3f,
            confirmMs = 100L,
            dragDelayMs = 300L,
            dragSlop = 0f
        )

        val start = detector.update(0.1f, 0L, 0f)
        assertTrue(start.contains(PinchDetector.Event.START))

        val mid = detector.update(0.1f, 50L, 0f)
        assertTrue(mid.isEmpty())

        val confirm = detector.update(0.1f, 101L, 0f)
        assertTrue(confirm.contains(PinchDetector.Event.CONFIRM))
    }

    @Test
    fun releaseAfterConfirm() {
        val detector = PinchDetector(
            startThreshold = 0.2f,
            releaseThreshold = 0.3f,
            confirmMs = 100L,
            dragDelayMs = 300L,
            dragSlop = 0f
        )

        detector.update(0.1f, 0L, 0f)
        detector.update(0.1f, 101L, 0f)

        val release = detector.update(0.45f, 200L, 0f)
        assertTrue(release.contains(PinchDetector.Event.RELEASE))
    }

    @Test
    fun dragAfterHoldDelay() {
        val detector = PinchDetector(
            startThreshold = 0.2f,
            releaseThreshold = 0.3f,
            confirmMs = 100L,
            dragDelayMs = 300L,
            dragSlop = 50f
        )

        detector.update(0.1f, 0L, 0f)
        detector.update(0.1f, 101L, 0f)

        val drag = detector.update(0.1f, 500L, 200f)
        assertTrue(drag.contains(PinchDetector.Event.DRAG))
    }
}
