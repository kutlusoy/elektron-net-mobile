package org.elektronnet.mobile.node

import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

object LogCleaner {
    data class Result(val freedBytes: Long, val files: Int)

    private val STAMP = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})")
    private val LATIN1 = Charsets.ISO_8859_1

    // Trims in place: the node keeps its log file open, so the file is rewritten and never replaced.
    // A file above maxBytes is cut to half of it, so it is not trimmed again a minute later.
    fun clean(files: List<File>, maxAgeDays: Int, maxBytes: Long, now: Long): Result {
        var freed = 0L
        var touched = 0
        val cutoff = if (maxAgeDays > 0) now - maxAgeDays * DAY_MS else Long.MIN_VALUE
        for (file in files) {
            val saved = runCatching { trim(file, cutoff, maxBytes) }.getOrDefault(0L)
            if (saved > 0) {
                freed += saved
                touched++
            }
        }
        return Result(freed, touched)
    }

    private fun trim(file: File, cutoff: Long, maxBytes: Long): Long {
        if (!file.isFile) return 0L
        val before = file.length()
        val keep = if (before > maxBytes) maxBytes / 2 else before
        val start = before - keep
        val temp = File(file.path + ".trim")
        try {
            var changed = start > 0
            file.inputStream().use { input ->
                input.skip(start)
                val reader = input.bufferedReader(LATIN1)
                temp.bufferedWriter(LATIN1).use { writer ->
                    var first = start > 0
                    var dropping = false
                    for (line in reader.lineSequence()) {
                        if (first) {
                            first = false
                            continue
                        }
                        val time = stamp(line)
                        if (time != null) dropping = time < cutoff
                        if (dropping) {
                            changed = true
                        } else {
                            writer.write(line)
                            writer.write("\n")
                        }
                    }
                }
            }
            if (!changed) return 0L
            temp.inputStream().use { source -> FileOutputStream(file).use { source.copyTo(it) } }
            return (before - file.length()).coerceAtLeast(0L)
        } finally {
            temp.delete()
        }
    }

    private fun stamp(line: String): Long? {
        val m = STAMP.find(line) ?: return null
        return runCatching { LocalDateTime.parse(m.groupValues[1]).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
