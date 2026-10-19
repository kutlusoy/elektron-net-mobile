package org.elektronnet.mobile.console

import java.math.BigDecimal

data class Formatted(val text: String, val truncated: Boolean)

object Pretty {
    const val MAX_CHARS = 200_000

    fun format(value: Any?, limit: Int = MAX_CHARS): Formatted {
        if (value is String) {
            return if (value.length > limit) Formatted(value.take(limit), true) else Formatted(value, false)
        }
        val out = StringBuilder()
        var truncated = false
        fun room(): Boolean {
            if (out.length >= limit) {
                truncated = true
                return false
            }
            return true
        }
        fun write(v: Any?, indent: Int) {
            if (!room()) return
            when (v) {
                null -> out.append("null")
                is Boolean, is Int, is Long -> out.append(v)
                is BigDecimal -> out.append(v.toPlainString())
                is Double -> out.append(BigDecimal(v.toString()).toPlainString())
                is String -> out.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"')
                is Map<*, *> -> {
                    if (v.isEmpty()) {
                        out.append("{}")
                        return
                    }
                    out.append("{\n")
                    var first = true
                    for ((k, item) in v) {
                        if (!room()) return
                        if (!first) out.append(",\n")
                        first = false
                        out.append("  ".repeat(indent + 1)).append('"').append(k).append("\": ")
                        write(item, indent + 1)
                    }
                    out.append('\n').append("  ".repeat(indent)).append('}')
                }
                is List<*> -> {
                    if (v.isEmpty()) {
                        out.append("[]")
                        return
                    }
                    out.append("[\n")
                    var first = true
                    for (item in v) {
                        if (!room()) return
                        if (!first) out.append(",\n")
                        first = false
                        out.append("  ".repeat(indent + 1))
                        write(item, indent + 1)
                    }
                    out.append('\n').append("  ".repeat(indent)).append(']')
                }
                else -> out.append(v.toString())
            }
        }
        write(value, 0)
        val text = if (out.length > limit) out.substring(0, limit) else out.toString()
        return Formatted(text, truncated || out.length > limit)
    }
}
