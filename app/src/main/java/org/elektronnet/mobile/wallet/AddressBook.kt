package org.elektronnet.mobile.wallet

import java.io.File
import org.elektronnet.mobile.node.Json

data class Contact(val name: String, val address: String)

enum class ContactProblem { EmptyName, NameTooLong, InvalidAddress, Duplicate, Full }

sealed interface ContactResult {
    data object Saved : ContactResult
    data class Refused(val problem: ContactProblem) : ContactResult
}

class AddressBook(private val file: File, private val check: AddressCheck) {
    fun all(): List<Contact> {
        if (!file.isFile) return emptyList()
        val parsed = runCatching { Json.parse(file.readText()) as? List<*> }.getOrNull() ?: return emptyList()
        return parsed.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val address = map["address"] as? String ?: return@mapNotNull null
            Contact(name, address)
        }.sortedBy { it.name.lowercase() }
    }

    fun find(address: String): Contact? {
        val key = check.normalized(address)
        return all().firstOrNull { it.address == key }
    }

    fun save(name: String, address: String, replacing: String? = null): ContactResult {
        val label = name.trim()
        if (label.isEmpty()) return ContactResult.Refused(ContactProblem.EmptyName)
        if (label.length > MAX_NAME || label.any { it.isISOControl() }) return ContactResult.Refused(ContactProblem.NameTooLong)
        val kind = check.classify(address)
        if (kind != AddressKind.Native && kind != AddressKind.LegacyBase58) return ContactResult.Refused(ContactProblem.InvalidAddress)
        val key = check.normalized(address)
        val rows = all().filter { it.address != replacing }
        if (rows.any { it.address == key || it.name.equals(label, ignoreCase = true) }) return ContactResult.Refused(ContactProblem.Duplicate)
        if (rows.size >= MAX_ENTRIES) return ContactResult.Refused(ContactProblem.Full)
        write(rows + Contact(label, key))
        return ContactResult.Saved
    }

    fun remove(address: String) {
        write(all().filter { it.address != address })
    }

    private fun write(rows: List<Contact>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Json.stringify(rows.map { linkedMapOf("name" to it.name, "address" to it.address) }))
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "address book not written" }
    }

    companion object {
        const val MAX_NAME = 60
        const val MAX_ENTRIES = 500
    }
}
