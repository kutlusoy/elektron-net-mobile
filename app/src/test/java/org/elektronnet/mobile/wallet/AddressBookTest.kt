package org.elektronnet.mobile.wallet

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBookTest {
    private val check = AddressCheck(testnet = true, hrp = "bcrt")
    private val a = "bcrt1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080"
    private val b = "bcrt1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qzf4jry"

    private fun book() = AddressBook(Files.createTempDirectory("book").toFile().resolve("sub/book.json"), check)

    @Test
    fun savesAndSortsContacts() {
        val book = book()
        assertEquals(ContactResult.Saved, book.save("Zoe", a))
        assertEquals(ContactResult.Saved, book.save("alice", b))
        assertEquals(listOf("alice", "Zoe"), book.all().map { it.name })
        assertEquals("Zoe", book.find(a.uppercase())?.name)
    }

    @Test
    fun refusesBadInput() {
        val book = book()
        assertEquals(ContactResult.Refused(ContactProblem.EmptyName), book.save("  ", a))
        assertEquals(ContactResult.Refused(ContactProblem.InvalidAddress), book.save("x", "not-an-address"))
        assertEquals(ContactResult.Refused(ContactProblem.InvalidAddress), book.save("x", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertEquals(ContactResult.Refused(ContactProblem.NameTooLong), book.save("a".repeat(61), a))
        book.save("Zoe", a)
        assertEquals(ContactResult.Refused(ContactProblem.Duplicate), book.save("zoe", b))
        assertEquals(ContactResult.Refused(ContactProblem.Duplicate), book.save("Other", a))
    }

    @Test
    fun editReplacesAndRemoveDeletes() {
        val book = book()
        book.save("Zoe", a)
        assertEquals(ContactResult.Saved, book.save("Zoe B", b, replacing = a))
        assertEquals(listOf("Zoe B"), book.all().map { it.name })
        assertNull(book.find(a))
        book.remove(b)
        assertTrue(book.all().isEmpty())
    }

    @Test
    fun corruptFileReadsAsEmpty() {
        val file = Files.createTempDirectory("book").toFile().resolve("book.json")
        file.writeText("{not json")
        assertTrue(AddressBook(file, check).all().isEmpty())
        assertEquals(ContactResult.Saved, AddressBook(file, check).save("Zoe", a))
        assertTrue(AddressBook(file, check).find(a) != null)
    }
}
