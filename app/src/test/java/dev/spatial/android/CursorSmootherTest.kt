package dev.spatial.android

import dev.spatial.android.hand.CursorSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorSmootherTest {

    @Test
    fun firstValueReturnsTarget() {
        val smoother = CursorSmoother(alpha = 0.9f)
        val result = smoother.next(100f, 200f)
        assertEquals(100f, result.first, 0.001f)
        assertEquals(200f, result.second, 0.001f)
    }

    @Test
    fun zeroSmoothingFollowsTarget() {
        val smoother = CursorSmoother(alpha = 0f)
        smoother.next(0f, 0f)
        val result = smoother.next(100f, 100f)
        assertEquals(100f, result.first, 0.001f)
        assertEquals(100f, result.second, 0.001f)
    }

    @Test
    fun highSmoothingMovesGradually() {
        val smoother = CursorSmoother(alpha = 0.9f)
        smoother.next(0f, 0f)
        val result = smoother.next(100f, 100f)
        assertTrue(result.first < 20f)
        assertTrue(result.second < 20f)
    }
}
