package org.elektronnet.mobile.console

import java.math.BigDecimal
import org.elektronnet.mobile.node.Json

data class ParsedCommand(
    val method: String,
    val positional: List<Any?>,
    val named: Map<String, Any?>,
    val tokens: List<String>,
)

sealed interface ParseResult {
    data class Ok(val command: ParsedCommand) : ParseResult
    data class Error(val message: String) : ParseResult
}

object CommandParser {
    const val MAX_LINE = 16_384
    private val NAME = Regex("[a-z][a-z0-9_]{0,30}")
    private val NUMBER = Regex("-?(0|[1-9][0-9]{0,14})(\\.[0-9]{1,16})?")

    fun parse(line: String): ParseResult {
        if (line.length > MAX_LINE) return ParseResult.Error("The line is too long.")
        val tokens = try {
            tokenize(line)
        } catch (e: IllegalArgumentException) {
            return ParseResult.Error(e.message ?: "Cannot read the command.")
        }
        if (tokens.isEmpty()) return ParseResult.Error("Type a command, for example getblockchaininfo.")
        val method = tokens[0]
        if (!Regex("[a-z][a-z0-9]*").matches(method)) return ParseResult.Error("Command names use lowercase letters and digits only.")
        val positional = ArrayList<Any?>()
        val named = LinkedHashMap<String, Any?>()
        try {
            for (token in tokens.drop(1)) {
                val eq = token.indexOf('=')
                val name = if (eq > 0) token.substring(0, eq) else null
                if (name != null && NAME.matches(name) && eq < token.length - 1) {
                    named[name] = value(token.substring(eq + 1))
                } else {
                    if (named.isNotEmpty()) return ParseResult.Error("Positional arguments must come before name=value arguments.")
                    positional.add(value(token))
                }
            }
        } catch (e: IllegalArgumentException) {
            return ParseResult.Error(e.message ?: "Cannot read an argument.")
        }
        return ParseResult.Ok(ParsedCommand(method, positional, named, tokens))
    }

    private fun value(token: String): Any? {
        if (token.length >= 2 && (token.first() == '"' && token.last() == '"')) return unquote(token)
        if (token.length >= 2 && (token.first() == '\'' && token.last() == '\'')) return token.substring(1, token.length - 1)
        if (token.startsWith("[") || token.startsWith("{")) {
            return try {
                Json.parse(token)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON: $token")
            }
        }
        return when {
            token == "true" -> true
            token == "false" -> false
            token == "null" -> null
            NUMBER.matches(token) -> if (token.contains('.')) BigDecimal(token) else token.toLong()
            else -> token
        }
    }

    private fun unquote(token: String): String =
        try {
            Json.parse(token) as String
        } catch (e: Exception) {
            token.substring(1, token.length - 1)
        }

    private fun tokenize(line: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var depth = 0
        var escaped = false
        var inToken = false
        for (c in line) {
            when {
                quote != null -> {
                    current.append(c)
                    if (escaped) escaped = false
                    else if (c == '\\' && quote == '"') escaped = true
                    else if (c == quote) quote = null
                }
                c == '"' || c == '\'' -> {
                    quote = c
                    inToken = true
                    current.append(c)
                }
                c == '[' || c == '{' -> {
                    depth++
                    inToken = true
                    current.append(c)
                }
                (c == ']' || c == '}') && depth > 0 -> {
                    depth--
                    current.append(c)
                }
                c.isWhitespace() && depth == 0 -> {
                    if (inToken) {
                        tokens.add(current.toString())
                        current.clear()
                        inToken = false
                    }
                }
                else -> {
                    inToken = true
                    current.append(c)
                }
            }
        }
        if (quote != null) throw IllegalArgumentException("A quote is not closed.")
        if (depth != 0) throw IllegalArgumentException("A bracket is not closed.")
        if (inToken) tokens.add(current.toString())
        return tokens
    }
}
