package org.elektronnet.mobile.node

enum class NodeFailure { DiskFull, CorruptedDatabase, AlreadyRunning, StartFailed, Unknown }

object FailureClassifier {
    private val patterns = listOf(
        NodeFailure.DiskFull to listOf("Disk space is too low", "No space left on device"),
        NodeFailure.CorruptedDatabase to listOf("Corrupted block database", "Error opening block database"),
        NodeFailure.AlreadyRunning to listOf("Cannot obtain a lock on"),
    )

    fun classify(lines: List<String>): NodeFailure {
        for ((failure, needles) in patterns) {
            if (lines.any { line -> needles.any { line.contains(it) } }) return failure
        }
        return NodeFailure.Unknown
    }
}
