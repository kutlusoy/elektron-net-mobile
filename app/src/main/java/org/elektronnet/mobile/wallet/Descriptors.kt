package org.elektronnet.mobile.wallet

data class DescriptorSpec(val descriptor: String, val active: Boolean, val internal: Boolean, val legacy: Boolean, val signingOnly: Boolean = false)

object Descriptors {
    const val COIN_TYPE_ELEKTRON = 1370
    const val COIN_TYPE_LEGACY = 0
    const val COIN_TYPE_TESTNET = 1

    fun forWallet(masterKey: String, testnet: Boolean, legacy: Boolean = !testnet): List<DescriptorSpec> {
        val current = if (testnet) COIN_TYPE_TESTNET else COIN_TYPE_ELEKTRON
        val specs = mutableListOf(
            DescriptorSpec(path(masterKey, current, 0), active = true, internal = false, legacy = false),
            DescriptorSpec(path(masterKey, current, 1), active = true, internal = true, legacy = false),
        )
        specs += DescriptorSpec("pkh($masterKey/84h/${current}h/0h/0/*)", active = false, internal = false, legacy = false, signingOnly = true)
        if (legacy) {
            specs += DescriptorSpec(path(masterKey, COIN_TYPE_LEGACY, 0), active = false, internal = false, legacy = true)
            specs += DescriptorSpec(path(masterKey, COIN_TYPE_LEGACY, 1), active = false, internal = false, legacy = true)
        }
        return specs
    }

    private fun path(masterKey: String, coinType: Int, chain: Int) = "wpkh($masterKey/84h/${coinType}h/0h/$chain/*)"
}
