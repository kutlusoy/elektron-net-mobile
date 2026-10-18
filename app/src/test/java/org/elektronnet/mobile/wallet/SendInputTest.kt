package org.elektronnet.mobile.wallet

import java.math.BigDecimal
import org.elektronnet.mobile.ui.model.AmountState
import org.elektronnet.mobile.ui.model.FeeInput
import org.elektronnet.mobile.ui.model.RecipientState
import org.elektronnet.mobile.ui.model.SendInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendInputTest {
    private val check = AddressCheck(testnet = false)
    private val uris = PaymentUri("elek", check)
    private val address = Bech32.encode("be", 0, ByteArray(20) { (it * 7).toByte() })

    private fun resolve(text: String) = SendInput.recipient(text, uris, check, "elek")

    @Test
    fun recipientStates() {
        assertEquals(RecipientState.Empty, resolve("  ").state)
        assertEquals(RecipientState.Valid, resolve(address).state)
        assertEquals(address, resolve(" " + address.uppercase() + " ").address)
        assertEquals(RecipientState.Legacy, resolve("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2").state)
        assertEquals(RecipientState.Foreign("bc"), resolve("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4").state)
        assertEquals(RecipientState.Invalid, resolve("hello").state)
        assertNull(resolve("hello").address)
        assertFalse(resolve("hello").usable)
        assertTrue(resolve(address).usable)
    }

    @Test
    fun linksFillAddressAndAmount() {
        val r = resolve("elek:$address?amount=2.5&message=Lunch")
        assertEquals(RecipientState.Valid, r.state)
        assertEquals(address, r.address)
        assertEquals(250_000_000L, r.amountLep)
        assertEquals("Lunch", r.note)
        assertEquals(RecipientState.Invalid, resolve("elek:$address?amount=abc").state)
        assertEquals(RecipientState.Foreign("bc"), resolve("elek:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4").state)
        assertEquals(RecipientState.Invalid, resolve("elek:$address?req-x=1").state)
    }

    @Test
    fun amounts() {
        assertEquals(AmountState.Empty, SendInput.amount("", 5))
        assertEquals(AmountState.Invalid, SendInput.amount("abc", 5))
        assertEquals(AmountState.Invalid, SendInput.amount("0", 5))
        assertEquals(AmountState.Ok(5L), SendInput.amount("0.00000005", 5))
        assertEquals(AmountState.Over(5L), SendInput.amount("0.00000006", 5))
        assertEquals(AmountState.Ok(150_000_000L), SendInput.amount("1,5", 200_000_000L))
    }

    @Test
    fun customFee() {
        val floor = BigDecimal("1.0")
        assertEquals(FeeInput.Ok(BigDecimal("2.5")), SendInput.customFee("2,5", floor))
        assertEquals(FeeInput.Invalid, SendInput.customFee("0.5", floor))
        assertEquals(FeeInput.Invalid, SendInput.customFee("10001", floor))
        assertEquals(FeeInput.Invalid, SendInput.customFee("x", floor))
    }
}
