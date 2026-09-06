package dev.spatial.android

import dev.spatial.android.hand.DragCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class DragCalculatorTest {

    @Test
    fun dragUsesDeltaWithoutJump() {
        val result = DragCalculator.apply(
            initialObjectX = 500f,
            initialObjectY = 300f,
            initialPointerX = 100f,
            initialPointerY = 100f,
            currentPointerX = 150f,
            currentPointerY = 180f
        )

        assertEquals(550f, result.first, 0.001f)
        assertEquals(380f, result.second, 0.001f)
    }
}
