package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectrumMultisigTest {

    private val xprv = listOf(
        "xprv9s21ZrQH143K2uBrmnU7KBC8xnoA2gdLujK5p1u9zkyocD2HCqAGGSQDaAeqJvyFzkGmzdNqN3P8E6wC86BrEyicr1HPSN9srybW6jB6L12",
        "xprv9s21ZrQH143K2yXccG3uJEZCFMuHgepMTVBjdUxpi5QJpcoA4bGf2xXZsehLSFKB49AVPEjDmfMHuoT83HyEmdEDs6sow1y2sjvrM1sHrsf",
        "xprv9s21ZrQH143K43nj42nXiq9oTQPWWrTa57LS8hY5at289KE9xGYG7UL5Uofvuuyw6xtZZYEdvwi3r8teb9CcajbJgvLHd9xNARgA8nznJnN",
    )
    private val zprv = listOf(
        "zprvAWgYBBk7JR8Gk1MVzeuXqU5LTV22KkRihsN1rgDP6Ks8G5fnzHJ3HhkLWvJ7vS4DBGJgQgRLNBJTPn3KVQ2dW19zPvwiJt8iBs6jxWpmULJ",
        "zprvAWgYBBk7JR8GkEjuePvMifGBXSk6ziGUv7x9fYfqSUuWj6FnR1xgZDWbXUHerMyDa2LzJDW8imt8bbzUKNYtw9QV449imGQhKyQqHKwv7Bi",
        "zprvAWgYBBk7JR8GjffVMNcsTytJZu3AcV3cgZ6PLQxDPuvnu7jidPbCPaHUgBGDCudHBMAcbWh4Z1r69mcbEzzE4aCyBTMGMnWAu43fT6ekjDR",
    )

    private fun wallet(keys: List<String>, field: String = "xprv"): String =
        """{"wallet_type":"2of3","seed_version":45,"use_encryption":false,""" +
            keys.mapIndexed { index, key ->
                """"x${index + 1}":{"$field":"$key","derivation":"m/0'","root_fingerprint":"73c5da0a"}"""
            }.joinToString(",") + "}"

    @Test
    fun parsesLegacyMultisig() {
        val content = ElectrumWalletParser.parse(wallet(xprv)) as ElectrumWalletContent.Multisig
        assertEquals(2, content.m)
        assertEquals(3, content.n)
        assertFalse(content.segwit)
        assertFalse(content.watchOnly)
        assertEquals(3, content.keys.size)
        assertEquals("0h", content.keys[0].derivation)
        assertTrue(content.keys[0].extKey.startsWith("xprv"))
    }

    @Test
    fun convertsSlip132ZprvForSegwitMultisig() {
        val content = ElectrumWalletParser.parse(wallet(zprv)) as ElectrumWalletContent.Multisig
        assertTrue(content.segwit)
        assertTrue(content.keys.all { it.extKey.startsWith("xprv") })
    }

    @Test
    fun detectsWatchOnlyWhenAnyKeystoreHasNoXprv() {
        val json = """{"wallet_type":"2of3","use_encryption":false,""" +
            """"x1":{"xprv":"${xprv[0]}","derivation":"m/0'"},""" +
            """"x2":{"xpub":"xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj","derivation":"m/0'"},""" +
            """"x3":{"xprv":"${xprv[2]}","derivation":"m/0'"}""" + "}"
        val content = ElectrumWalletParser.parse(json) as ElectrumWalletContent.Multisig
        assertTrue(content.watchOnly)
    }

    @Test
    fun multisigDescriptorsUseSortedMulti() {
        val content = ElectrumWalletParser.parse(wallet(zprv)) as ElectrumWalletContent.Multisig
        val specs = Descriptors.forElectrumMultisig(content.m, content.segwit, content.keys)
        assertEquals(2, specs.size)
        assertTrue(specs[0].descriptor.startsWith("wsh(sortedmulti(2,"))
        assertTrue(specs[0].descriptor.contains("/0h/0/*)"))
        assertTrue(specs[1].descriptor.contains("/0h/1/*)"))
        assertTrue(specs[1].internal)
        assertFalse(specs[0].legacy)
    }

    @Test
    fun legacyMultisigUsesShSortedMulti() {
        val content = ElectrumWalletParser.parse(wallet(xprv)) as ElectrumWalletContent.Multisig
        val specs = Descriptors.forElectrumMultisig(content.m, content.segwit, content.keys)
        assertTrue(specs[0].descriptor.startsWith("sh(sortedmulti(2,"))
        assertTrue(specs[0].legacy)
    }
}
