package org.elektronnet.mobile.node

import java.math.BigDecimal

class JsonException(message: String) : Exception(message)

object Json {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipSpace()
        if (!parser.atEnd()) throw JsonException("trailing data at ${parser.position}")
        return value
    }

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is BigDecimal -> out.append(value.toPlainString())
            is Double -> {
                if (value.isNaN() || value.isInfinite()) throw JsonException("non-finite number")
                out.append(BigDecimal(value.toString()).toPlainString())
            }
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> throw JsonException("unsupported type ${value.javaClass.name}")
        }
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var position = 0

        fun atEnd() = position >= text.length

        fun skipSpace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun value(): Any? {
            skipSpace()
            if (atEnd()) throw JsonException("unexpected end")
            return when (val c = text[position]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw JsonException("unexpected '$c' at $position")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, position)) throw JsonException("bad literal at $position")
            position += word.length
            return result
        }

        private fun number(): Any {
            val start = position
            if (text[position] == '-') position++
            while (position < text.length && (text[position].isDigit() || text[position] in ".eE+-")) position++
            val token = text.substring(start, position)
            return try {
                if (token.any { it in ".eE" }) BigDecimal(token) else token.toLong()
            } catch (e: NumberFormatException) {
                BigDecimal(token)
            }
        }

        private fun string(): String {
            position++
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("unterminated string")
                val c = text[position++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("unterminated escape")
                        when (val e = text[position++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                if (position + 4 > text.length) throw JsonException("bad unicode escape")
                                out.append(text.substring(position, position + 4).toInt(16).toChar())
                                position += 4
                            }
                            else -> throw JsonException("bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun array(): List<Any?> {
            position++
            val items = ArrayList<Any?>()
            skipSpace()
            if (!atEnd() && text[position] == ']') {
                position++
                return items
            }
            while (true) {
                items.add(value())
                skipSpace()
                if (atEnd()) throw JsonException("unterminated array")
                when (text[position++]) {
                    ',' -> continue
                    ']' -> return items
                    else -> throw JsonException("expected , or ] at ${position - 1}")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            position++
            val items = LinkedHashMap<String, Any?>()
            skipSpace()
            if (!atEnd() && text[position] == '}') {
                position++
                return items
            }
            while (true) {
                skipSpace()
                if (atEnd() || text[position] != '"') throw JsonException("expected key at $position")
                val key = string()
                skipSpace()
                if (atEnd() || text[position++] != ':') throw JsonException("expected : at ${position - 1}")
                items[key] = value()
                skipSpace()
                if (atEnd()) throw JsonException("unterminated object")
                when (text[position++]) {
                    ',' -> continue
                    '}' -> return items
                    else -> throw JsonException("expected , or } at ${position - 1}")
                }
            }
        }
    }
}
