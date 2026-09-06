package dev.spatial.android

import java.util.Locale

object ExpressionEvaluator {
    private val OPS = setOf("+", "-", "*", "/")
    private val PRECEDENCE = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)

    fun eval(expr: String): String {
        return try {
            val tokens = tokenize(expr)
            val rpn = toRpn(tokens)
            val result = evalRpn(rpn)
            if (result.isNaN() || result.isInfinite()) "Error" else format(result)
        } catch (_: Exception) {
            "Error"
        }
    }

    private fun format(d: Double): String {
        return if (Math.abs(d - Math.round(d)) < 1e-9) {
            Math.round(d).toString()
        } else {
            String.format(Locale.US, "%.8f", d).trimEnd('0').trimEnd('.')
        }
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val s = input.trim()

        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    tokens.add(s.substring(start, i))
                }

                c in "+-*/()" -> {
                    val prev = tokens.lastOrNull()
                    val isUnary = (c == '-' || c == '+') &&
                        (prev == null || prev == "(" || OPS.contains(prev))

                    if (isUnary) {
                        val sign = c
                        i++
                        val start = i
                        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                        if (start == i) throw IllegalArgumentException("Bad unary")
                        tokens.add(sign + s.substring(start, i))
                    } else {
                        tokens.add(c.toString())
                        i++
                    }
                }

                else -> throw IllegalArgumentException("Invalid character: $c")
            }
        }

        return tokens
    }

    private fun isNumber(token: String): Boolean = token.toDoubleOrNull() != null

    private fun toRpn(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val ops = ArrayDeque<String>()

        for (token in tokens) {
            when {
                isNumber(token) -> output.add(token)

                token == "(" -> ops.addLast(token)

                token == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") {
                        output.add(ops.removeLast())
                    }
                    if (ops.isNotEmpty()) ops.removeLast()
                }

                OPS.contains(token) -> {
                    while (ops.isNotEmpty() && ops.last() != "(" &&
                        (PRECEDENCE[ops.last()] ?: 0) >= (PRECEDENCE[token] ?: 0)
                    ) {
                        output.add(ops.removeLast())
                    }
                    ops.addLast(token)
                }
            }
        }

        while (ops.isNotEmpty()) {
            output.add(ops.removeLast())
        }

        return output
    }

    private fun evalRpn(rpn: List<String>): Double {
        val stack = ArrayDeque<Double>()

        for (token in rpn) {
            if (OPS.contains(token)) {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> a / b
                    else -> throw IllegalStateException()
                }
                stack.addLast(result)
            } else {
                stack.addLast(token.toDouble())
            }
        }

        return stack.last()
    }
}
