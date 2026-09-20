fun evaluateCalculadoraExpression(str: String): Double? {
    try {
        return object : Any() {
            var pos = -1
            var ch = 0
            val expression = str.replace("×", "*").replace("÷", "/").replace(" ", "")

            fun nextChar() {
                ch = if (++pos < expression.length) expression[pos].toInt() else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.toInt()) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expression.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.toInt())) x += parseTerm() // addition
                    else if (eat('-'.toInt())) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.toInt())) x *= parseFactor() // multiplication
                    else if (eat('/'.toInt())) { // division
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Division by zero")
                        x /= denom
                    }
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.toInt())) return parseFactor() // unary plus
                if (eat('-'.toInt())) return -parseFactor() // unary minus

                var x: Double = 0.0
                val startPos = pos
                if (eat('('.toInt())) { // parentheses
                    x = parseExpression()
                    eat(')'.toInt())
                } else if (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) { // numbers
                    while (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) nextChar()
                    x = expression.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                
                if (eat('%'.toInt())) {
                    x /= 100.0
                }

                return x
            }
        }.parse()
    } catch (e: Exception) {
        return null
    }
}

fun main() {
    println(evaluateCalculadoraExpression("2 + 5 * 10")) // 52
    println(evaluateCalculadoraExpression("10 + 20 / 5")) // 14
    println(evaluateCalculadoraExpression("2 * 5 + 10")) // 20
    println(evaluateCalculadoraExpression("(2 + 5) * 10")) // 70
    println(evaluateCalculadoraExpression("10 + 20%")) // wait, 10 + 0.2 = 10.2
}
main()
