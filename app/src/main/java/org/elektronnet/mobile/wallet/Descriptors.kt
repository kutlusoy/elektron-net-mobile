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

    fun forElectrumSeed(masterKey: String, type: ElectrumSeedType): List<DescriptorSpec> {
        val specs = mutableListOf<DescriptorSpec>()
        when (type) {
            ElectrumSeedType.Segwit, ElectrumSeedType.TwoFactorSegwit -> {
                // Electrum native SegWit derives from m/0'/0/* (receive) and m/0'/1/* (change)
                specs += DescriptorSpec("wpkh($masterKey/0h/0/*)", active = true, internal = false, legacy = false)
                specs += DescriptorSpec("wpkh($masterKey/0h/1/*)", active = true, internal = true, legacy = false)
                // Scan legacy path as inactive fallback
                specs += DescriptorSpec("pkh($masterKey/0h/0/*)", active = false, internal = false, legacy = true, signingOnly = true)
            }
            ElectrumSeedType.Standard, ElectrumSeedType.TwoFactor -> {
                // Electrum standard legacy derives from m/0/* and m/1/*
                specs += DescriptorSpec("pkh($masterKey/0/*)", active = true, internal = false, legacy = true)
                specs += DescriptorSpec("pkh($masterKey/1/*)", active = true, internal = true, legacy = true)
                // Scan segwit path as inactive fallback
                specs += DescriptorSpec("wpkh($masterKey/0/*)", active = false, internal = false, legacy = false, signingOnly = true)
            }
        }
        return specs
    }

    private fun path(masterKey: String, coinType: Int, chain: Int) = "wpkh($masterKey/84h/${coinType}h/0h/$chain/*)"
}
