package org.elektronnet.mobile.kdf

import java.io.File
import java.security.MessageDigest
import org.elektronnet.mobile.ChainConfig
import org.elektronnet.mobile.wallet.SystemEntropy

object KdfConfig {
    const val DEFAULT_RPC_HOST = "127.0.0.1"
    const val DEFAULT_RPC_PORT = 7821
    const val NETID_TESTNET = 8888
    const val NETID_MAINNET = 2
    const val DEFAULT_SEEDNODE = "wss://elektron-market.onrender.com"

    private const val PASSPHRASE_TAG = "elektron-net/kdf-passphrase/v1"
    private const val PASSPHRASE_PREFIX = "elkkdf1"

    val SEPOLIA_RPC_URLS = listOf(
        "https://ethereum-sepolia-rpc.publicnode.com",
        "https://sepolia.drpc.org",
    )
    const val SEPOLIA_SWAP_CONTRACT = "0xeA6D65434A15377081495a9E7C5893543E7c32cB"

    val MAINNET_ETH_RPC_URLS = listOf(
        "https://ethereum-rpc.publicnode.com",
        "https://eth.drpc.org",
    )
    const val MAINNET_ETH_SWAP_CONTRACT = "0x8500AFc0bc5214728082163326C2FF0C73f4a871"

    val TELEK_ELECTRUM_SERVERS = listOf(
        "127.0.0.1:50005",
    )

    val TBTC_ELECTRUM_SERVERS = listOf(
        "127.0.0.1:50005",
    )

    val ELEK_ELECTRUM_SERVERS = listOf(
        "electrs.elektron-net.org:50002",
        "electrs2.elektron-net.org:50002",
    )

    val BTC_ELECTRUM_SERVERS = listOf(
        "btc.electrum1.cipig.net:10000",
        "btc.electrum2.cipig.net:10000",
    )

    /**
     * Deterministic KDF Passphrase derived from the wallet seed phrase (Task #98).
     * Returns "elkkdf1" + sha256("elektron-net/kdf-passphrase/v1/" + seedText).
     */
    fun deriveKdfPassphrase(seedText: String?): String {
        val text = seedText?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val bytes = "$PASSPHRASE_TAG/$text".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return PASSPHRASE_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates an RPC password against the strict KDF mm2 password policy:
     * - Length >= 8
     * - At least one digit
     * - At least one lowercase letter
     * - At least one uppercase letter
     * - At least one special / non-alphanumeric character
     * - No 3 identical characters in a row
     * - "password" substring banned (case-insensitive)
     */
    fun isPasswordPolicyOk(pw: String): Boolean {
        if (pw.length < 8) return false
        if (pw.contains("password", ignoreCase = true)) return false
        if (!pw.any { it.isDigit() }) return false
        if (!pw.any { it.isLowerCase() }) return false
        if (!pw.any { it.isUpperCase() }) return false
        if (pw.all { it.isLetterOrDigit() }) return false
        for (i in 0 until pw.length - 2) {
            if (pw[i] == pw[i + 1] && pw[i] == pw[i + 2]) return false
        }
        return true
    }

    /**
     * Generates a policy-compliant RPC password with prefix "K_" followed by 16 random hex bytes.
     */
    fun generateRpcPassword(entropy: SystemEntropy = SystemEntropy()): String {
        while (true) {
            val bytes = entropy.draw(16)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            val candidate = "K_$hex"
            if (isPasswordPolicyOk(candidate)) return candidate
        }
    }

    /**
     * Builds the complete MM2.json configuration.
     */
    fun buildConfig(
        kdfDir: File,
        isTestnet: Boolean,
        passphrase: String,
        rpcPassword: String,
        rpcPort: Int = DEFAULT_RPC_PORT,
        seednode: String = DEFAULT_SEEDNODE,
    ): Map<String, Any?> {
        val dbDir = File(kdfDir, "db").apply { mkdirs() }
        val netid = if (isTestnet) NETID_TESTNET else NETID_MAINNET

        val coins = if (isTestnet) {
            listOf(
                mapOf(
                    "coin" to "tELEK",
                    "name" to "elektron-testnet",
                    "fname" to "Elektron (testnet)",
                    "pubtype" to 111,
                    "p2shtype" to 196,
                    "wiftype" to 239,
                    "segwit" to true,
                    "bech32_hrp" to "tb",
                    "address_format" to mapOf("format" to "segwit"),
                    "decimals" to 8,
                    "txfee" to 1000,
                    "estimate_fee_mode" to "ECONOMICAL",
                    "estimate_fee_blocks" to 4,
                    "avg_blocktime" to 1,
                    "required_confirmations" to 2,
                    "mature_confirmations" to 1,
                    "derivation_path" to "m/84'/1370'",
                    "orderbook_ticker" to "tELEK",
                    "rpcport" to ChainConfig.rpcPort,
                    "mm2" to 1,
                    "protocol" to mapOf("type" to "UTXO"),
                    "electrum" to TELEK_ELECTRUM_SERVERS.map { mapOf("url" to it, "protocol" to "TCP") },
                ),
                mapOf(
                    "coin" to "tBTC",
                    "name" to "bitcoin",
                    "fname" to "tBTC (Elektron fork-testnet electrs-tn)",
                    "pubtype" to 111,
                    "p2shtype" to 196,
                    "wiftype" to 239,
                    "segwit" to true,
                    "bech32_hrp" to "tb",
                    "address_format" to mapOf("format" to "segwit"),
                    "decimals" to 8,
                    "txfee" to 1000,
                    "estimate_fee_mode" to "ECONOMICAL",
                    "estimate_fee_blocks" to 4,
                    "avg_blocktime" to 1,
                    "required_confirmations" to 2,
                    "mature_confirmations" to 1,
                    "derivation_path" to "m/84'/1'",
                    "orderbook_ticker" to "tBTC",
                    "rpcport" to ChainConfig.rpcPort,
                    "mm2" to 1,
                    "is_testnet" to true,
                    "protocol" to mapOf("type" to "UTXO"),
                    "electrum" to TBTC_ELECTRUM_SERVERS.map { mapOf("url" to it, "protocol" to "TCP") },
                ),
                mapOf(
                    "coin" to "SEPOLIAETH",
                    "name" to "ethereum",
                    "fname" to "Ethereum Sepolia (kdf-native, public RPC nodes)",
                    "derivation_path" to "m/44'/60'",
                    "protocol" to mapOf("type" to "ETH", "protocol_data" to mapOf("chain_id" to 11155111)),
                    "max_eth_tx_type" to 2,
                    "mm2" to 1,
                    "is_testnet" to true,
                    "urls" to SEPOLIA_RPC_URLS,
                    "swap_contract_address" to SEPOLIA_SWAP_CONTRACT,
                ),
            )
        } else {
            listOf(
                mapOf(
                    "coin" to "ELEK",
                    "name" to "elektron",
                    "fname" to "Elektron",
                    "pubtype" to 0,
                    "p2shtype" to 5,
                    "wiftype" to 128,
                    "segwit" to true,
                    "bech32_hrp" to "be",
                    "address_format" to mapOf("format" to "segwit"),
                    "decimals" to 8,
                    "txfee" to 1000,
                    "estimate_fee_mode" to "ECONOMICAL",
                    "estimate_fee_blocks" to 4,
                    "avg_blocktime" to 1,
                    "required_confirmations" to 2,
                    "mature_confirmations" to 1,
                    "derivation_path" to "m/84'/1370'",
                    "orderbook_ticker" to "ELEK",
                    "rpcport" to 8332,
                    "mm2" to 1,
                    "protocol" to mapOf("type" to "UTXO"),
                    "electrum" to ELEK_ELECTRUM_SERVERS.map { mapOf("url" to it, "protocol" to "TCP") },
                ),
                mapOf(
                    "coin" to "BTC",
                    "name" to "bitcoin",
                    "fname" to "Bitcoin",
                    "pubtype" to 0,
                    "p2shtype" to 5,
                    "wiftype" to 128,
                    "segwit" to true,
                    "bech32_hrp" to "bc",
                    "address_format" to mapOf("format" to "segwit"),
                    "txfee" to 0,
                    "estimate_fee_mode" to "ECONOMICAL",
                    "avg_blocktime" to 600,
                    "required_confirmations" to 1,
                    "derivation_path" to "m/84'/0'",
                    "protocol" to mapOf("type" to "UTXO", "chain_variant" to "BTC"),
                    "rpcport" to 8332,
                    "mm2" to 1,
                    "sign_message_prefix" to "Bitcoin Signed Message:\n",
                    "electrum" to BTC_ELECTRUM_SERVERS.map { mapOf("url" to it, "protocol" to "TCP") },
                ),
                mapOf(
                    "coin" to "ETH",
                    "name" to "ethereum",
                    "fname" to "Ethereum",
                    "derivation_path" to "m/44'/60'",
                    "max_eth_tx_type" to 2,
                    "mm2" to 1,
                    "chain_id" to 1,
                    "avg_blocktime" to 15,
                    "required_confirmations" to 3,
                    "rpcport" to 80,
                    "swap_gas_fee_policy" to "Medium",
                    "sign_message_prefix" to "Ethereum Signed Message:\n",
                    "protocol" to mapOf("type" to "ETH", "protocol_data" to mapOf("chain_id" to 1)),
                    "urls" to MAINNET_ETH_RPC_URLS,
                    "swap_contract_address" to MAINNET_ETH_SWAP_CONTRACT,
                ),
            )
        }

        return linkedMapOf(
            "gui" to "elektron-net",
            "netid" to netid,
            "rpcip" to DEFAULT_RPC_HOST,
            "rpcport" to rpcPort,
            "passphrase" to passphrase,
            "rpc_password" to rpcPassword,
            "dbdir" to dbDir.absolutePath,
            "seednodes" to listOf(seednode.trim().ifEmpty { DEFAULT_SEEDNODE }),
            "coins" to coins,
        )
    }
}
