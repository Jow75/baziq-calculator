package com.omnicalc.platform.core.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.PI
import kotlin.math.E

enum class AngleMode {
    DEGREES,
    RADIANS
}

sealed class EvalResult {
    data class Success(val value: BigDecimal, val formatted: String) : EvalResult()
    data class Error(val message: String) : EvalResult()
}

class ExpressionEvaluator(
    var angleMode: AngleMode = AngleMode.DEGREES,
    var mathContext: MathContext = BigDecimalMath.DEFAULT_MATH_CONTEXT
) {
    companion object {
        val PHI = BigDecimal("1.6180339887498948482045868343656381")
        val PI_BD = BigDecimal("3.1415926535897932384626433832795029")
        val E_BD = BigDecimal("2.7182818284590452353602874713526625")
    }

    private sealed class Token {
        data class Number(val value: BigDecimal) : Token()
        data class Operator(val symbol: String, val precedence: Int, val isRightAssociative: Boolean) : Token()
        data class Function(val name: String) : Token()
        object LeftParen : Token()
        object RightParen : Token()
        object Comma : Token()
    }

    fun evaluate(expression: String): EvalResult {
        if (expression.isBlank()) {
            return EvalResult.Success(BigDecimal.ZERO, "0")
        }

        try {
            val tokens = tokenize(expression)
            val rpn = toRpn(tokens)
            val result = evaluateRpn(rpn)
            val formatted = formatResult(result)
            return EvalResult.Success(result, formatted)
        } catch (e: ArithmeticException) {
            return EvalResult.Error(e.message ?: "Calculation error")
        } catch (e: IllegalArgumentException) {
            return EvalResult.Error(e.message ?: "Syntax error")
        } catch (e: Exception) {
            return EvalResult.Error("Invalid expression: ${e.localizedMessage}")
        }
    }

    private fun tokenize(input: String): List<Token> {
        val sanitized = CalculatorDisplayFormatter.sanitizeForEvaluation(input)
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("φ", "phi")
            .trim()

        val tokens = mutableListOf<Token>()
        var i = 0
        var prevToken: Token? = null

        while (i < sanitized.length) {
            val c = sanitized[i]

            if (c.isWhitespace()) {
                i++
                continue
            }

            // Check for numbers (including decimals and scientific notation)
            if (c.isDigit() || c == '.') {
                val start = i
                var hasDot = c == '.'
                i++
                while (i < sanitized.length) {
                    val curr = sanitized[i]
                    if (curr.isDigit()) {
                        i++
                    } else if (curr == '.' && !hasDot) {
                        hasDot = true
                        i++
                    } else if ((curr == 'e' || curr == 'E') && i + 1 < sanitized.length) {
                        val next = sanitized[i + 1]
                        if (next.isDigit() || next == '+' || next == '-') {
                            i += 2
                            while (i < sanitized.length && sanitized[i].isDigit()) {
                                i++
                            }
                            break
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
                val numStr = sanitized.substring(start, i)
                val numberToken = Token.Number(BigDecimal(numStr))

                // Implicit multiplication: e.g. 5(2) or 5pi
                if (prevToken is Token.Number || prevToken is Token.RightParen) {
                    tokens.add(Token.Operator("*", 2, false))
                }
                tokens.add(numberToken)
                prevToken = numberToken
                continue
            }

            // Check for identifiers (functions or constants)
            if (c.isLetter()) {
                val start = i
                while (i < sanitized.length && sanitized[i].isLetter()) {
                    i++
                }
                val name = sanitized.substring(start, i).lowercase(Locale.ROOT)

                // Check constants
                when (name) {
                    "pi" -> {
                        if (prevToken is Token.Number || prevToken is Token.RightParen) {
                            tokens.add(Token.Operator("*", 2, false))
                        }
                        val token = Token.Number(PI_BD)
                        tokens.add(token)
                        prevToken = token
                    }
                    "e" -> {
                        if (prevToken is Token.Number || prevToken is Token.RightParen) {
                            tokens.add(Token.Operator("*", 2, false))
                        }
                        val token = Token.Number(E_BD)
                        tokens.add(token)
                        prevToken = token
                    }
                    "phi" -> {
                        if (prevToken is Token.Number || prevToken is Token.RightParen) {
                            tokens.add(Token.Operator("*", 2, false))
                        }
                        val token = Token.Number(PHI)
                        tokens.add(token)
                        prevToken = token
                    }
                    "mod" -> {
                        val token = Token.Operator("mod", 2, false)
                        tokens.add(token)
                        prevToken = token
                    }
                    "of" -> {
                        // "20% of 45000" acts as multiplication
                        val token = Token.Operator("*", 2, false)
                        tokens.add(token)
                        prevToken = token
                    }
                    else -> {
                        // Function name
                        if (prevToken is Token.Number || prevToken is Token.RightParen) {
                            tokens.add(Token.Operator("*", 2, false))
                        }
                        val token = Token.Function(name)
                        tokens.add(token)
                        prevToken = token
                    }
                }
                continue
            }

            // Check operators & parentheses
            when (c) {
                '+' -> {
                    val token = Token.Operator("+", 1, false)
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '-' -> {
                    // Check if unary minus
                    val isUnary = prevToken == null || prevToken is Token.Operator || prevToken is Token.LeftParen || prevToken is Token.Function
                    if (isUnary) {
                        val token = Token.Operator("neg", 3, true)
                        tokens.add(token)
                        prevToken = token
                    } else {
                        val token = Token.Operator("-", 1, false)
                        tokens.add(token)
                        prevToken = token
                    }
                    i++
                }
                '*' -> {
                    val token = Token.Operator("*", 2, false)
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '/' -> {
                    val token = Token.Operator("/", 2, false)
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '%' -> {
                    // Postfix percentage: e.g. 50% = 0.5, 200 * 15% = 30
                    val token = Token.Operator("%", 5, false)
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '^' -> {
                    val token = Token.Operator("^", 4, true)
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '!' -> {
                    val token = Token.Operator("!", 5, false) // Postfix factorial
                    tokens.add(token)
                    prevToken = token
                    i++
                }
                '(' -> {
                    if (prevToken is Token.Number || prevToken is Token.RightParen) {
                        tokens.add(Token.Operator("*", 2, false))
                    }
                    tokens.add(Token.LeftParen)
                    prevToken = Token.LeftParen
                    i++
                }
                ')' -> {
                    tokens.add(Token.RightParen)
                    prevToken = Token.RightParen
                    i++
                }
                ',' -> {
                    tokens.add(Token.Comma)
                    prevToken = Token.Comma
                    i++
                }
                else -> {
                    throw IllegalArgumentException("Unsupported character: '$c'")
                }
            }
        }

        return tokens
    }

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val opStack = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> output.add(token)
                is Token.Function -> opStack.push(token)
                is Token.Operator -> {
                    while (opStack.isNotEmpty()) {
                        val top = opStack.peek()
                        if (top is Token.Operator) {
                            val cond = if (token.isRightAssociative) {
                                token.precedence < top.precedence
                            } else {
                                token.precedence <= top.precedence
                            }
                            if (cond) {
                                output.add(opStack.pop())
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }
                    opStack.push(token)
                }
                is Token.LeftParen -> opStack.push(token)
                is Token.RightParen -> {
                    var foundLeft = false
                    while (opStack.isNotEmpty()) {
                        val top = opStack.pop()
                        if (top is Token.LeftParen) {
                            foundLeft = true
                            break
                        } else {
                            output.add(top)
                        }
                    }
                    if (!foundLeft) {
                        throw IllegalArgumentException("Mismatched parentheses")
                    }
                    if (opStack.isNotEmpty() && opStack.peek() is Token.Function) {
                        output.add(opStack.pop())
                    }
                }
                is Token.Comma -> {
                    while (opStack.isNotEmpty() && opStack.peek() !is Token.LeftParen) {
                        output.add(opStack.pop())
                    }
                }
            }
        }

        while (opStack.isNotEmpty()) {
            val top = opStack.pop()
            if (top is Token.LeftParen || top is Token.RightParen) {
                throw IllegalArgumentException("Mismatched parentheses")
            }
            output.add(top)
        }

        return output
    }

    private data class StackEntry(val value: BigDecimal, val isPercent: Boolean = false)

    private fun evaluateRpn(rpn: List<Token>): BigDecimal {
        val stack = ArrayDeque<StackEntry>()

        for (token in rpn) {
            when (token) {
                is Token.Number -> stack.push(StackEntry(token.value, isPercent = false))
                is Token.Operator -> {
                    if (token.symbol == "neg") {
                        if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for negation")
                        val a = stack.pop()
                        stack.push(StackEntry(a.value.negate(mathContext), a.isPercent))
                    } else if (token.symbol == "!") {
                        if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for factorial")
                        val a = stack.pop()
                        try {
                            val n = a.value.intValueExact()
                            stack.push(StackEntry(BigDecimalMath.factorial(n), isPercent = false))
                        } catch (e: Exception) {
                            throw ArithmeticException("Factorial requires a non-negative integer")
                        }
                    } else if (token.symbol == "%") {
                        if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for percentage")
                        val a = stack.pop()
                        stack.push(StackEntry(a.value.divide(BigDecimal(100), mathContext), isPercent = true))
                    } else {
                        if (stack.size < 2) throw IllegalArgumentException("Missing operand for ${token.symbol}")
                        val b = stack.pop()
                        val a = stack.pop()
                        val res = when (token.symbol) {
                            "+" -> {
                                if (b.isPercent && !a.isPercent) {
                                    val delta = a.value.multiply(b.value, mathContext)
                                    a.value.add(delta, mathContext)
                                } else {
                                    a.value.add(b.value, mathContext)
                                }
                            }
                            "-" -> {
                                if (b.isPercent && !a.isPercent) {
                                    val delta = a.value.multiply(b.value, mathContext)
                                    a.value.subtract(delta, mathContext)
                                } else {
                                    a.value.subtract(b.value, mathContext)
                                }
                            }
                            "*" -> a.value.multiply(b.value, mathContext)
                            "/" -> BigDecimalMath.divide(a.value, b.value, mathContext)
                            "mod" -> a.value.remainder(b.value, mathContext)
                            "^" -> BigDecimalMath.power(a.value, b.value, mathContext)
                            else -> throw IllegalArgumentException("Unknown operator: ${token.symbol}")
                        }
                        stack.push(StackEntry(res, isPercent = false))
                    }
                }
                is Token.Function -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing argument for function ${token.name}")
                    val arg = stack.pop()
                    val res = evaluateFunction(token.name, arg.value)
                    stack.push(StackEntry(res, isPercent = false))
                }
                else -> throw IllegalArgumentException("Unexpected token in evaluation: $token")
            }
        }

        if (stack.size != 1) {
            throw IllegalArgumentException("Malformed expression")
        }

        return stack.pop().value
    }

    private fun evaluateFunction(name: String, arg: BigDecimal): BigDecimal {
        val d = arg.toDouble()
        val resultD = when (name.lowercase(Locale.ROOT)) {
            "sin" -> {
                val rad = if (angleMode == AngleMode.DEGREES) Math.toRadians(d) else d
                Math.sin(rad)
            }
            "cos" -> {
                val rad = if (angleMode == AngleMode.DEGREES) Math.toRadians(d) else d
                Math.cos(rad)
            }
            "tan" -> {
                val rad = if (angleMode == AngleMode.DEGREES) Math.toRadians(d) else d
                val cosVal = Math.cos(rad)
                if (Math.abs(cosVal) < 1e-15) throw ArithmeticException("Tangent is undefined for this angle")
                Math.tan(rad)
            }
            "asin" -> {
                if (d < -1.0 || d > 1.0) throw ArithmeticException("asin argument must be between -1 and 1")
                val rad = Math.asin(d)
                if (angleMode == AngleMode.DEGREES) Math.toDegrees(rad) else rad
            }
            "acos" -> {
                if (d < -1.0 || d > 1.0) throw ArithmeticException("acos argument must be between -1 and 1")
                val rad = Math.acos(d)
                if (angleMode == AngleMode.DEGREES) Math.toDegrees(rad) else rad
            }
            "atan" -> {
                val rad = Math.atan(d)
                if (angleMode == AngleMode.DEGREES) Math.toDegrees(rad) else rad
            }
            "sinh" -> Math.sinh(d)
            "cosh" -> Math.cosh(d)
            "tanh" -> Math.tanh(d)
            "ln" -> {
                if (d <= 0.0) throw ArithmeticException("ln argument must be positive")
                Math.log(d)
            }
            "log", "log10" -> {
                if (d <= 0.0) throw ArithmeticException("log argument must be positive")
                Math.log10(d)
            }
            "sqrt" -> {
                if (d < 0.0) throw ArithmeticException("sqrt argument must be non-negative")
                return BigDecimalMath.sqrt(arg, mathContext)
            }
            "cbrt" -> Math.cbrt(d)
            "abs" -> return arg.abs(mathContext)
            "fact" -> {
                val n = arg.intValueExact()
                return BigDecimalMath.factorial(n)
            }
            else -> throw IllegalArgumentException("Unknown function: $name")
        }

        if (resultD.isNaN() || resultD.isInfinite()) {
            throw ArithmeticException("Function $name yielded an undefined or infinite result")
        }

        // Clean small floating point errors near zero (e.g. sin(180 deg))
        val cleaned = if (Math.abs(resultD) < 1e-14) 0.0 else resultD
        return BigDecimal(cleaned, mathContext)
    }

    private fun formatResult(value: BigDecimal): String {
        val rounded = value.round(BigDecimalMath.DISPLAY_MATH_CONTEXT)
        if (rounded.compareTo(BigDecimal.ZERO) == 0) return "0"
        val stripped = rounded.stripTrailingZeros()
        val plain = stripped.toPlainString()

        // If the number is huge or tiny, use scientific notation
        if (plain.length > 15 || (stripped.abs() > BigDecimal.ZERO && stripped.abs() < BigDecimal("0.000000001"))) {
            return stripped.toEngineeringString()
        }
        return plain
    }
}
