package org.elektronnet.mobile.node

import java.util.ArrayDeque

class OutputBuffer(private val capacity: Int = 1_000) {
    private val lines = ArrayDeque<String>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile
    var lastAddedAt: Long = 0
        private set

    fun add(line: String) {
        lastAddedAt = System.currentTimeMillis()
        synchronized(lines) {
            if (lines.size >= capacity) lines.removeFirst()
            lines.addLast(line)
        }
        listeners.forEach { it(line) }
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun tail(count: Int): List<String> = synchronized(lines) { lines.toList().takeLast(count) }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
