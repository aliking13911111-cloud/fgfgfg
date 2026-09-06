package dev.spatial.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionEvaluatorTest {

    @Test
    fun simpleMath() {
        assertEquals("14", ExpressionEvaluator.eval("2+3*4"))
    }

    @Test
    fun parentheses() {
        assertEquals("20", ExpressionEvaluator.eval("(2+3)*4"))
    }

    @Test
    fun divisionError() {
        assertEquals("Error", ExpressionEvaluator.eval("1/0"))
    }

    @Test
    fun decimal() {
        assertEquals("2.5", ExpressionEvaluator.eval("1.25+1.25"))
    }
}
