package org.elektronnet.mobile.console

import java.io.File
import org.elektronnet.mobile.node.Json

interface Sealer {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray?
}

data class HistoryEntry(val id: Long, val atMs: Long, val text: String)

class ConsoleHistory(
    private val file: File,
    private val sealer: Sealer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var entries: MutableList<HistoryEntry>? = null

    @Synchronized
    fun all(): List<HistoryEntry> = load().sortedByDescending { it.atMs }

    @Synchronized
    fun add(maskedText: String) {
        val list = load()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        list.add(HistoryEntry(id, clock(), maskedText.take(MAX_TEXT)))
        while (list.size > MAX_ENTRIES) list.removeAt(0)
        save(list)
    }

    @Synchronized
    fun remove(id: Long) {
        val list = load()
        list.removeAll { it.id == id }
        save(list)
    }

    @Synchronized
    fun clear() {
        entries = mutableListOf()
        file.delete()
    }

    @Synchronized
    fun prune(retentionDays: Int) {
        val list = load()
        if (retentionDays <= 0) {
            clear()
            return
        }
        val cutoff = clock() - retentionDays * DAY_MS
        if (list.removeAll { it.atMs < cutoff }) save(list)
    }

    private fun load(): MutableList<HistoryEntry> {
        entries?.let { return it }
        val list = mutableListOf<HistoryEntry>()
        if (file.isFile) {
            val plain = sealer.open(file.readBytes())
            if (plain != null) {
                runCatching {
                    (Json.parse(plain.toString(Charsets.UTF_8)) as List<*>).forEach {
                        val m = it as Map<*, *>
                        list.add(HistoryEntry((m["id"] as Number).toLong(), (m["at"] as Number).toLong(), m["text"].toString()))
                    }
                }
            }
        }
        entries = list
        return list
    }

    private fun save(list: List<HistoryEntry>) {
        val json = Json.stringify(list.map { linkedMapOf("id" to it.id, "at" to it.atMs, "text" to it.text) })
        file.parentFile?.mkdirs()
        file.writeBytes(sealer.seal(json.toByteArray(Charsets.UTF_8)))
    }

    companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_TEXT = 4_000
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

object ExportsHousekeeping {
    const val DUMP_DAYS = 7

    fun cleanup(exports: File, nowMs: Long, days: Int = DUMP_DAYS): List<String> {
        val dumps = File(exports, "dumps")
        val cutoff = nowMs - days * ConsoleHistory.DAY_MS
        val removed = mutableListOf<String>()
        dumps.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff && f.delete()) removed.add(f.name)
        }
        return removed
    }
}
