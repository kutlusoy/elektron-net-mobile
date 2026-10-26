package org.elektronnet.mobile.wallet

import java.text.Normalizer

/**
 * Text that is written into a block as an OP_RETURN output. The chain treats it as opaque bytes, so everything
 * that could harm a reader of the text (control or direction-override characters, invisible characters, endless
 * stacked marks) is removed before it is signed.
 */
object EmbeddedMessage {
    const val MAX_BYTES = 1000
    private const val MAX_STACKED_MARKS = 3
    private const val MAX_BLANK_LINES = 1

    fun clean(input: String): String {
        val text = Normalizer.normalize(input, Normalizer.Form.NFC)
        val out = StringBuilder()
        var marks = 0
        var newlines = 0
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            val type = Character.getType(cp)
            when {
                cp == '\r'.code -> {
                    if (index < text.length && text[index] == '\n') continue
                    marks = 0
                    newlines = appendNewline(out, newlines)
                }
                cp == '\n'.code -> {
                    marks = 0
                    newlines = appendNewline(out, newlines)
                }
                cp == '\t'.code || type == Character.SPACE_SEPARATOR.toInt() -> {
                    marks = 0
                    if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
                }
                type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() -> {
                    if (out.isNotEmpty() && marks < MAX_STACKED_MARKS && out.last() != '\n' && out.last() != ' ') {
                        out.appendCodePoint(cp)
                        marks++
                    }
                }
                isForbidden(type) -> Unit
                else -> {
                    marks = 0
                    newlines = 0
                    out.appendCodePoint(cp)
                }
            }
        }
        return truncate(out.toString().trim(), MAX_BYTES)
    }

    private fun appendNewline(out: StringBuilder, newlines: Int): Int {
        if (out.isEmpty() || newlines > MAX_BLANK_LINES) return newlines
        out.append('\n')
        return newlines + 1
    }

    private fun isForbidden(type: Int): Boolean = when (type.toByte()) {
        Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> false
    }

    fun bytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    fun byteLength(text: String): Int = bytes(text).size

    private fun truncate(text: String, max: Int): String {
        if (byteLength(text) <= max) return text
        var end = 0
        var size = 0
        while (end < text.length) {
            val cp = text.codePointAt(end)
            val width = bytes(String(Character.toChars(cp))).size
            if (size + width > max) break
            size += width
            end += Character.charCount(cp)
        }
        return text.substring(0, end).trimEnd()
    }

    fun hex(text: String): String = bytes(text).joinToString("") { "%02x".format(it) }

    /** The locking script of the data output: OP_RETURN followed by one minimal data push. */
    fun scriptHex(text: String): String {
        val n = byteLength(text)
        val push = when {
            n <= 75 -> "%02x".format(n)
            n <= 255 -> "4c%02x".format(n)
            else -> "4d%02x%02x".format(n and 0xff, n shr 8)
        }
        return "6a" + push + hex(text)
    }

    /** Size in virtual bytes the data output adds to a payment: value, script length and script. */
    fun outputVBytes(text: String): Int {
        val script = scriptHex(text).length / 2
        return 8 + (if (script < 253) 1 else 3) + script
    }

    fun extraFeeLep(text: String, lepPerVb: java.math.BigDecimal): Long =
        lepPerVb.multiply(java.math.BigDecimal(outputVBytes(text))).setScale(0, java.math.RoundingMode.CEILING).toLong()
}
